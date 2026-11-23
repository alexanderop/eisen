<script setup lang="ts">
import { Timer } from '@lucide/vue'
import { Badge, Button, Card, ConfirmButton, Segmented, SetButton, Sheet, Stepper } from '../components/ui'
import { computed, ref, toRaw, watch } from 'vue'
import ShareWorkout from '../components/ShareWorkout.vue'
import ProgressView from './ProgressView.vue'
import {
  HOLD_STEP_SECONDS,
  MAX_HELD_SECONDS,
  nextReps,
  rirLine,
  setText,
  stepFor,
  withReps,
  type ExerciseLog,
  type Workout,
} from '../domain/program'
import { recordIds } from '../domain/records'
import { durationMinutes } from '../domain/share'
import { OUTCOME_LABELS, sessionSummary, timerOutcome } from '../domain/timer'
import { historyItems } from '../domain/timerScreens'
import { wodRecordIds } from '../domain/wodScreens'
import { fmt, fmtAdded, fmtLoad, formatDate } from '../format'
import { haptic } from '../haptics'
import { deleteTimerSession, deleteWorkout, state, updateWorkout } from '../store'

type HistoryView = 'log' | 'progress'
const VIEW_KEY = 'eisen:history-view'

function savedView(): HistoryView {
  try {
    return localStorage.getItem(VIEW_KEY) === 'progress' ? 'progress' : 'log'
  } catch {
    return 'log'
  }
}

/** The log of everything done, or the charts of how it went. It opens on the one picked last. */
const view = ref<HistoryView>(savedView())
watch(view, (current) => {
  try {
    localStorage.setItem(VIEW_KEY, current)
  } catch {
    // Storage blocked: the switch just starts on the log next time.
  }
})
const VIEWS = [
  { value: 'log' as const, label: 'Log' },
  { value: 'progress' as const, label: 'Progress' },
]

function duration(workout: Workout): string {
  const minutes = durationMinutes(workout)
  return minutes === null ? '' : `${minutes} min`
}

/** Per workout, the exercises that set a record on that day. */
const records = computed(() => state.history.map((_, index) => recordIds(state.history, index)))

/** The WOD runs that beat every run of their WOD before them. */
const wodRecords = computed(() => wodRecordIds(state.timerSessions))

/** Workouts and interval timers in one list, newest first. */
const items = computed(() => historyItems(state.history, state.timerSessions))

/** A copy of the workout being corrected. Nothing changes until it is saved. */
const draft = ref<Workout | null>(null)

function edit(workout: Workout) {
  draft.value = structuredClone(toRaw(workout))
}

/** Changing the reps of a set keeps its reps in reserve, clearing the set clears them. */
function setReps(logIndex: number, setIndex: number, reps: number | null) {
  const log = draft.value?.exercises[logIndex]
  if (log && draft.value) draft.value.exercises[logIndex] = withReps(log, setIndex, reps)
}

function onTap(logIndex: number, setIndex: number) {
  const log = draft.value?.exercises[logIndex]
  if (!log || log.targetReps === null) return
  setReps(logIndex, setIndex, nextReps(log.sets[setIndex], log.targetReps))
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
  <Segmented v-model="view" :options="VIEWS" />

  <ProgressView v-if="view === 'progress'" />

  <template v-else>
    <Card v-if="items.length === 0" as="p" class="muted">
      Nothing logged yet
    </Card>

    <template v-for="item in items" :key="item.kind === 'timer' ? item.session.id : item.workout.id">
      <Card v-if="item.kind === 'timer'" as="article" class="timer-row">
        <span class="timer-icon" aria-hidden="true"><Timer :size="20" :stroke-width="2.25" /></span>
        <div class="timer-text">
          <h2>
            {{ item.session.name }}
            <Badge v-if="wodRecords.has(item.session.id)">Record</Badge>
          </h2>
          <p class="muted meta">
            {{ formatDate(item.session.startedAt) }} ·
            <span class="nowrap">{{ sessionSummary(item.session) }}</span>
          </p>
          <p v-if="timerOutcome(item.session) !== 'completed'" class="outcome">
            {{ OUTCOME_LABELS[timerOutcome(item.session)] }}
          </p>
        </div>
        <ConfirmButton
          class="px-1"
          variant="ghost"
          size="md"
          label="Delete"
          confirm-label="Confirm"
          @confirm="deleteTimerSession(item.session.id)"
        />
      </Card>

      <Card v-else as="article">
        <header class="row">
          <div>
            <h2>{{ item.workout.name }}</h2>
            <p class="muted meta">{{ formatDate(item.workout.startedAt) }} · {{ duration(item.workout) }}</p>
          </div>
          <div class="actions">
            <ShareWorkout class="px-1" variant="ghost" size="md" :workout="item.workout" />
            <Button class="px-1" variant="ghost" size="md" @click="edit(item.workout)">Edit</Button>
            <ConfirmButton
              variant="ghost"
              size="md"
              label="Delete"
              confirm-label="Confirm"
              @confirm="deleteWorkout(item.workout.id)"
            />
          </div>
        </header>
        <ul>
          <li v-for="log in item.workout.exercises" :key="log.id">
            <span class="name">
              {{ log.name }}
              <Badge v-if="records[item.index]?.has(log.id)">Record</Badge>
            </span>
            <strong>{{ fmtLoad(log.equipment, log.weight, item.workout.unit) }}</strong>
            <span class="reps">
              <span
                v-for="(reps, index) in log.sets"
                :key="index"
                :class="{ missed: reps === null || (log.targetReps !== null && reps !== log.targetReps) }"
                >{{ reps === null ? '–' : setText(log.timed, reps) }}</span
              >
            </span>
            <span v-if="rirLine(log)" class="rir">{{ rirLine(log) }}</span>
          </li>
        </ul>
      </Card>
    </template>
  </template>

  <Sheet v-if="draft" :title="`Edit ${draft.name}`" @close="draft = null">
    <p class="muted meta">
      {{ formatDate(draft.startedAt) }}. This corrects the log. Your next weights stay as they are.
    </p>
    <section v-for="(log, logIndex) in draft.exercises" :key="log.id" class="edit">
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
          @click="onTap(logIndex, index)"
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
            @update:model-value="setReps(logIndex, index, $event === 0 ? null : $event)"
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

.timer-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-block: 12px;
}

.timer-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--r-md);
  background: color-mix(in srgb, var(--primary) 14%, transparent);
  color: var(--primary);
}

.timer-text {
  flex: 1;
  min-width: 0;
}

.timer-text h2 {
  font-size: 16px;
  overflow-wrap: anywhere;
}

.nowrap {
  white-space: nowrap;
}

.outcome {
  display: inline-block;
  margin-top: 4px;
  padding: 0 7px;
  border-radius: var(--r-full);
  background: color-mix(in srgb, var(--warn) 16%, transparent);
  color: var(--warn);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
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

/* Under the reps: in their row it would squeeze the name on a narrow phone. */
.rir {
  grid-column: 1 / -1;
  margin-top: -4px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: var(--muted-foreground);
}
</style>
