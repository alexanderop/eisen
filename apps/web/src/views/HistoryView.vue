<script setup lang="ts">
import { Badge, Button, Card, ConfirmButton, SetButton, Sheet, Stepper } from '../components/ui'
import { computed, ref, toRaw } from 'vue'
import ShareWorkout from '../components/ShareWorkout.vue'
import {
  HOLD_STEP_SECONDS,
  MAX_HELD_SECONDS,
  nextReps,
  setText,
  stepFor,
  type ExerciseLog,
  type Workout,
} from '../domain/program'
import { recordIds } from '../domain/records'
import { durationMinutes } from '../domain/share'
import { fmt, fmtAdded, fmtLoad, formatDate } from '../format'
import { haptic } from '../haptics'
import { deleteWorkout, state, updateWorkout } from '../store'

function duration(workout: Workout): string {
  const minutes = durationMinutes(workout)
  return minutes === null ? '' : `${minutes} min`
}

/** Per workout, the exercises that set a record on that day. */
const records = computed(() => state.history.map((_, index) => recordIds(state.history, index)))

/** A copy of the workout being corrected. Nothing changes until it is saved. */
const draft = ref<Workout | null>(null)

function edit(workout: Workout) {
  draft.value = structuredClone(toRaw(workout))
}

function onTap(log: ExerciseLog, setIndex: number) {
  if (log.targetReps === null) return
  log.sets[setIndex] = nextReps(log.sets[setIndex], log.targetReps)
  haptic('tap')
}

/** A set in the edit sheet: reps, or a hold in seconds. 0 is not logged. */
function setFormat(log: ExerciseLog) {
  return (value: number) => (value === 0 ? '–' : setText(log.timed, value))
}

function setState(log: ExerciseLog, reps: number | null) {
  if (reps === null) return 'empty'
  return log.targetReps !== null && reps < log.targetReps ? 'missed' : 'done'
}

function save() {
  if (draft.value) updateWorkout(draft.value.id, draft.value.exercises)
  draft.value = null
}
</script>

<template>
  <h1 class="page-title">History</h1>

  <Card v-if="state.history.length === 0" as="p" class="muted">
    No workouts yet. Finish your first one and it shows up here.
  </Card>

  <Card v-for="(workout, position) in state.history" as="article" :key="workout.id">
    <header class="row">
      <div>
        <h2>{{ workout.name }}</h2>
        <p class="muted meta">{{ formatDate(workout.startedAt) }} · {{ duration(workout) }}</p>
      </div>
      <div class="actions">
        <ShareWorkout class="px-1" variant="ghost" size="md" :workout="workout" />
        <Button class="px-1" variant="ghost" size="md" @click="edit(workout)">Edit</Button>
        <ConfirmButton
          variant="ghost"
          size="md"
          label="Delete"
          confirm-label="Confirm"
          @confirm="deleteWorkout(workout.id)"
        />
      </div>
    </header>
    <ul>
      <li v-for="log in workout.exercises" :key="log.id">
        <span class="name">
          {{ log.name }}
          <Badge v-if="records[position].has(log.id)">Record</Badge>
        </span>
        <strong>{{ fmtLoad(log.equipment, log.weight, workout.unit) }}</strong>
        <span class="reps">
          <span
            v-for="(reps, index) in log.sets"
            :key="index"
            :class="{ missed: reps === null || (log.targetReps !== null && reps !== log.targetReps) }"
            >{{ reps === null ? '–' : setText(log.timed, reps) }}</span
          >
        </span>
      </li>
    </ul>
  </Card>

  <Sheet v-if="draft" :title="`Edit ${draft.name}`" @close="draft = null">
    <p class="muted meta">
      {{ formatDate(draft.startedAt) }}. This corrects the log. Your next weights stay as they are.
    </p>
    <section v-for="log in draft.exercises" :key="log.id" class="edit">
      <div class="row">
        <h3>{{ log.name }}</h3>
        <Stepper
          v-if="log.equipment === 'bodyweight'"
          v-model="log.weight"
          :step="stepFor(log.equipment, draft.unit)"
          :min="0"
          :label="`${log.name} added weight`"
          :format="(value: number) => fmtAdded(value, draft?.unit)"
        />
        <Stepper
          v-else
          v-model="log.weight"
          :step="stepFor(log.equipment, draft.unit)"
          :min="stepFor(log.equipment, draft.unit)"
          :label="`${log.name} weight`"
          :format="(value: number) => fmt(value, draft?.unit)"
        />
      </div>
      <div v-if="log.targetReps !== null && !log.timed" class="sets">
        <SetButton
          v-for="(reps, index) in log.sets"
          :key="index"
          :state="setState(log, reps)"
          :aria-label="`${log.name} set ${index + 1}: ${reps === null ? 'not logged' : `${reps} reps`}`"
          @click="onTap(log, index)"
        >
          {{ reps ?? log.targetReps }}
        </SetButton>
      </div>
      <template v-else>
        <div v-for="(reps, index) in log.sets" :key="index" class="row">
          <span class="muted">Set {{ index + 1 }}</span>
          <Stepper
            :model-value="reps ?? 0"
            :step="log.timed ? HOLD_STEP_SECONDS : 1"
            :max="log.timed ? MAX_HELD_SECONDS : undefined"
            :label="`${log.name} set ${index + 1} ${log.timed ? 'hold' : 'reps'}`"
            :format="setFormat(log)"
            @update:model-value="log.sets[index] = $event === 0 ? null : $event"
          />
        </div>
      </template>
    </section>
    <Button class="w-full" @click="save">Save changes</Button>
    <Button variant="secondary" class="w-full" @click="draft = null">Cancel</Button>
  </Sheet>
</template>

<style scoped>
h2 {
  font-size: 18px;
  font-weight: 700;
}

.actions {
  display: flex;
  gap: 8px;
  flex: none;
}

.meta {
  font-size: 13px;
}

.edit {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

h3 {
  font-size: 16px;
  font-weight: 700;
}

.sets {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

ul {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}

/* The load column grows for "BW + 12.5kg" instead of wrapping it. */
li {
  display: grid;
  grid-template-columns: 1fr minmax(64px, auto) minmax(92px, auto);
  gap: 8px;
  align-items: baseline;
  padding: 8px 0;
  border-top: 1px solid var(--border);
  font-size: 15px;
}

li strong {
  text-align: right;
}

.reps {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  color: var(--primary);
  font-weight: 600;
}

.reps .missed {
  color: var(--warn);
}
</style>
