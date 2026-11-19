<script setup lang="ts">
import { Badge, Button, Card, ConfirmButton, Field, IconButton, Input, Stepper } from '../components/ui'
import { computed, ref, watch } from 'vue'
import ExercisePicker from '../components/ExercisePicker.vue'
import { EQUIPMENT_LABELS } from '../domain/exercises'
import { MAX_REPS, MAX_SETS, type Routine, type Slot } from '../domain/program'
import { fmt } from '../format'
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

watch(editingId, () => window.scrollTo(0, 0))

function summary(routine: Routine): string {
  const names = routine.slots.flatMap((slot) => findExercise(slot.exerciseId)?.name ?? [])
  return names.length > 0 ? names.join(' · ') : 'No exercises yet'
}

function detail(slot: Slot): string {
  const exercise = findExercise(slot.exerciseId)
  if (!exercise) return ''
  const label = EQUIPMENT_LABELS[exercise.equipment]
  return exercise.equipment === 'bodyweight'
    ? label
    : `${label} · ${fmt(progressOf(exercise.id).weight)}`
}

function onName(routine: Routine) {
  if (routine.name.trim() === '') routine.name = 'Workout'
}

function onPick(routine: Routine, id: string) {
  addSlot(routine, id)
  picking.value = false
}

function onDelete(routine: Routine) {
  removeRoutine(routine.id)
  editingId.value = null
}
</script>

<template>
  <template v-if="editing">
    <Button class="back" variant="ghost" size="md" @click="editingId = null">‹ Workouts</Button>

    <Field label="Name">
      <Input v-model="editing.name" maxlength="30" @blur="onName(editing)" />
    </Field>

    <Card v-if="editing.slots.length === 0" as="p" class="muted">
      No exercises yet. Add the first one below.
    </Card>

    <Card v-for="(slot, index) in editing.slots" as="section" class="slot" :key="slot.exerciseId">
      <div class="row">
        <div>
          <h2>{{ findExercise(slot.exerciseId)?.name ?? 'Deleted exercise' }}</h2>
          <p class="muted small">{{ detail(slot) }}</p>
        </div>
        <div class="tools">
          <IconButton label="Move up" :disabled="index === 0" @click="moveSlot(editing, index, -1)">
            ↑
          </IconButton>
          <IconButton
            label="Move down"
            :disabled="index === editing.slots.length - 1"
            @click="moveSlot(editing, index, 1)"
          >
            ↓
          </IconButton>
          <IconButton label="Remove exercise" tone="danger" @click="removeSlot(editing, index)">
            ✕
          </IconButton>
        </div>
      </div>
      <div class="row">
        <span class="muted">Sets</span>
        <Stepper v-model="slot.sets" :step="1" :min="1" :max="MAX_SETS" label="sets" />
      </div>
      <div class="row">
        <span class="muted">Reps</span>
        <Stepper
          :model-value="slot.reps ?? 0"
          :step="1"
          :max="MAX_REPS"
          label="reps"
          :format="(value: number) => (value === 0 ? 'max' : String(value))"
          @update:model-value="slot.reps = $event === 0 ? null : $event"
        />
      </div>
    </Card>

    <Button variant="primary" block @click="picking = true">Add exercise</Button>
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

    <Button block @click="editingId = addRoutine().id">New workout</Button>
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
  display: flex;
  flex-direction: column;
  gap: 10px;
}

h2 {
  font-size: 17px;
  font-weight: 700;
}

.tools {
  display: flex;
  gap: 4px;
  flex: none;
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
  color: var(--accent);
  font-size: 22px;
}
</style>
