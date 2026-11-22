<script setup lang="ts">
import { Badge, Button, Card, ConfirmButton, Field, Input, Stepper } from '../components/ui'
import { computed, ref, watch } from 'vue'
import ExercisePicker from '../components/ExercisePicker.vue'
import ExerciseImage from '../components/ExerciseImage.vue'
import { kindLabel } from '../domain/exercises'
import { MAX_REPS, MAX_SETS, type Routine, type Slot } from '../domain/program'
import { fmtLoad } from '../format'
import {
  addRoutine,
  addSlot,
  findExercise,
  moveSlot,
  nextRoutine,
  progressOf,
  removeRoutine,
  removeSlot,
  state,
} from '../store'

const editingId = ref<string | null>(null)
const editing = computed(
  () => state.routines.find((routine) => routine.id === editingId.value) ?? null,
)
const picking = ref(false)
/** The exercise whose sets, reps and order are being changed. One at a time keeps the list short. */
const openSlot = ref<string | null>(null)

watch(editingId, () => {
  openSlot.value = null
  window.scrollTo(0, 0)
})

function summary(routine: Routine): string {
  const names = routine.slots.flatMap((slot) => findExercise(slot.exerciseId)?.name ?? [])
  return names.length > 0 ? names.join(' · ') : 'No exercises yet'
}

function detail(slot: Slot): string {
  const exercise = findExercise(slot.exerciseId)
  if (!exercise) return ''
  const scheme = `${slot.sets} × ${slot.reps ?? 'max'}`
  const weight = progressOf(exercise.id).weight
  // Plain bodyweight needs no load: the kind already says "Bodyweight".
  return exercise.equipment === 'bodyweight' && weight === 0
    ? `${scheme} · ${kindLabel(exercise)}`
    : `${scheme} · ${fmtLoad(exercise.equipment, weight)} · ${kindLabel(exercise)}`
}

function toggleMax(slot: Slot) {
  slot.reps = slot.reps === null ? 5 : null
}

function onRemove(routine: Routine, index: number) {
  removeSlot(routine, index)
  openSlot.value = null
}

function onName(routine: Routine) {
  if (routine.name.trim() === '') routine.name = 'Workout'
}

function onPick(routine: Routine, id: string) {
  addSlot(routine, id)
  openSlot.value = id
  picking.value = false
}

function onDelete(routine: Routine) {
  removeRoutine(routine.id)
  editingId.value = null
}
</script>

<template>
  <template v-if="editing">
    <Button class="back px-1" variant="ghost" size="md" @click="editingId = null">‹ Workouts</Button>

    <Field label="Name">
      <Input v-model="editing.name" maxlength="30" @blur="onName(editing)" />
    </Field>

    <Card v-if="editing.slots.length === 0" as="p" class="muted">
      No exercises yet. Add the first one below.
    </Card>

    <Card v-for="(slot, index) in editing.slots" as="section" class="slot" :key="slot.exerciseId">
      <button
        class="slot-head"
        :aria-expanded="openSlot === slot.exerciseId"
        @click="openSlot = openSlot === slot.exerciseId ? null : slot.exerciseId"
      >
        <ExerciseImage
          :image="findExercise(slot.exerciseId)?.image"
          :name="findExercise(slot.exerciseId)?.name ?? '?'"
        />
        <span class="slot-text">
          <strong>{{ findExercise(slot.exerciseId)?.name ?? 'Deleted exercise' }}</strong>
          <span class="muted small">{{ detail(slot) }}</span>
        </span>
        <span class="chevron" aria-hidden="true">{{ openSlot === slot.exerciseId ? '⌃' : '⌄' }}</span>
      </button>
      <div v-if="openSlot === slot.exerciseId" class="slot-body">
        <div class="row">
          <span class="muted">Sets</span>
          <Stepper v-model="slot.sets" :step="1" :min="1" :max="MAX_SETS" label="sets" />
        </div>
        <div class="row">
          <span class="muted">Reps</span>
          <Stepper
            v-if="slot.reps !== null"
            v-model="slot.reps"
            :step="1"
            :min="1"
            :max="MAX_REPS"
            label="reps"
          />
          <span v-else class="max-note">As many as possible</span>
        </div>
        <button class="max" :aria-pressed="slot.reps === null" @click="toggleMax(slot)">
          <span class="box" aria-hidden="true">✓</span>
          Max reps instead of a fixed number
        </button>
        <div class="row tools">
          <Button variant="secondary" :disabled="index === 0" @click="moveSlot(editing, index, -1)">↑ Move up</Button>
          <Button variant="secondary" :disabled="index === editing.slots.length - 1" @click="moveSlot(editing, index, 1)">
            ↓ Move down
          </Button>
        </div>
        <ConfirmButton class="w-full"
          :key="slot.exerciseId"
         
          variant="ghost"
          label="Remove from workout"
          confirm-label="Tap again to remove"
          @confirm="onRemove(editing, index)"
        />
      </div>
    </Card>

    <Button class="w-full" @click="picking = true">Add exercise</Button>
    <p v-if="state.active" class="muted small note">
      Changes apply from the next workout. The one in progress stays as it is.
    </p>
    <ConfirmButton
      v-if="state.routines.length > 1"
      :key="editing.id"
      block
      label="Delete workout"
      confirm-label="Tap again to delete"
      @confirm="onDelete(editing)"
    />

    <ExercisePicker
      v-if="picking"
      :exclude="editing.slots.map((slot) => slot.exerciseId)"
      @pick="onPick(editing, $event)"
      @close="picking = false"
    />
  </template>

  <template v-else>
    <h1 class="page-title">Workouts</h1>
    <p class="muted small">Workouts rotate in this order. Tap one to change it.</p>

    <Card v-for="routine in state.routines" as="button" class="routine" :key="routine.id" @click="editingId = routine.id">
      <span class="row">
        <strong>{{ routine.name }}</strong>
        <Badge v-if="routine.id === nextRoutine.id">Next</Badge>
        <span class="chevron" aria-hidden="true">›</span>
      </span>
      <span class="muted small">{{ summary(routine) }}</span>
    </Card>

    <Button variant="secondary" class="w-full" @click="editingId = addRoutine().id">New workout</Button>
  </template>
</template>

<style scoped>
.back {
  align-self: flex-start;
}

.small {
  font-size: 13px;
}

.note {
  text-align: center;
}

.slot {
  padding: 0;
}

.slot-head {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 16px 10px 10px;
  text-align: left;
}

.slot-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.slot-text strong {
  font-size: 16px;
}

.slot-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 16px 12px;
}

.max-note {
  font-weight: 700;
  min-height: 52px;
  display: flex;
  align-items: center;
}

.max {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  text-align: left;
  color: var(--muted-foreground);
  font-size: 14px;
}

.box {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 2px solid var(--border);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 800;
  color: transparent;
}

.max[aria-pressed='true'] {
  color: var(--foreground);
}

.max[aria-pressed='true'] .box {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}

.tools {
  gap: 8px;
}

.tools > * {
  flex: 1;
}

.routine {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.routine strong {
  font-size: 18px;
  flex: 1;
}

.chevron {
  color: var(--primary);
  font-size: 22px;
}
</style>
