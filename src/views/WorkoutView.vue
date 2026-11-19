<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import BarbellPlates from '../components/BarbellPlates.vue'
import BottomSheet from '../components/BottomSheet.vue'
import RestTimer from '../components/RestTimer.vue'
import WeightStepper from '../components/WeightStepper.vue'
import { warmupSets } from '../domain/plates'
import {
  EXERCISE_NAMES,
  isComplete,
  isMachine,
  outcome,
  UNIT_DEFAULTS,
  type ExerciseLog,
} from '../domain/program'
import { emptyWeightFor, fmt, loadFor, platesText } from '../format'
import { startRest, stopRest } from '../rest'
import {
  discardWorkout,
  finishWorkout,
  logReps,
  setActiveWeight,
  state,
  suggestedReps,
  tapSet,
} from '../store'

const emit = defineEmits<{ close: [] }>()

const workout = computed(() => state.active)
const mode = ref<'workout' | 'warmup'>('workout')
const editing = ref<number | null>(null)
const confirming = ref(false)
/** The max-reps set being entered. */
const entry = ref<{ exerciseIndex: number; setIndex: number; reps: number } | null>(null)

const defaults = computed(() => UNIT_DEFAULTS[state.settings.unit])
const editedLog = computed(() =>
  editing.value === null ? null : (workout.value?.exercises[editing.value] ?? null),
)
const weightedLogs = computed(
  () => workout.value?.exercises.filter((log) => log.targetReps !== null) ?? [],
)
const nothingLogged = computed(
  () => workout.value?.exercises.every((log) => outcome(log) === 'skipped') ?? true,
)

function onTap(exerciseIndex: number, setIndex: number) {
  const active = workout.value
  if (!active) return
  const log = active.exercises[exerciseIndex]
  if (log.targetReps === null) {
    entry.value = {
      exerciseIndex,
      setIndex,
      reps: log.sets[setIndex] ?? suggestedReps(log.id, setIndex),
    }
    return
  }
  const reps = tapSet(exerciseIndex, setIndex)
  if (reps === null || isComplete(active)) {
    stopRest()
    return
  }
  const done = reps === log.targetReps
  startRest(
    done ? state.settings.restDone : state.settings.restMissed,
    done ? 'All reps done.' : 'Missed reps. Rest longer.',
  )
}

function setClass(log: ExerciseLog, reps: number | null) {
  if (reps === null) return {}
  const missed = log.targetReps !== null && reps < log.targetReps
  return { done: !missed, missed }
}

function saveEntry(reps: number | null) {
  const active = workout.value
  if (!active || !entry.value) return
  logReps(entry.value.exerciseIndex, entry.value.setIndex, reps)
  entry.value = null
  if (reps === null || isComplete(active)) stopRest()
  else startRest(state.settings.restDone, 'Max set logged.')
}

function onFinish() {
  const active = workout.value
  if (!active) return
  if (isComplete(active)) finish()
  else confirming.value = true
}

function finish() {
  stopRest()
  finishWorkout()
  emit('close')
}

function discard() {
  stopRest()
  discardWorkout()
  emit('close')
}

let wakeLock: WakeLockSentinel | null = null

async function keepScreenOn() {
  if (document.visibilityState !== 'visible' || !('wakeLock' in navigator)) return
  try {
    wakeLock = await navigator.wakeLock.request('screen')
  } catch {
    // Denied, for example in battery saver mode. The workout works without it.
  }
}

onMounted(() => {
  void keepScreenOn()
  document.addEventListener('visibilitychange', keepScreenOn)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', keepScreenOn)
  void wakeLock?.release()
})
</script>

<template>
  <div v-if="workout" class="workout">
    <header class="bar">
      <button class="link" @click="emit('close')">‹ Back</button>
      <h1>Workout {{ workout.type }}</h1>
      <button class="link" @click="onFinish">Finish</button>
    </header>

    <div class="segmented">
      <button :aria-pressed="mode === 'workout'" @click="mode = 'workout'">Workout</button>
      <button :aria-pressed="mode === 'warmup'" @click="mode = 'warmup'">Warmup</button>
    </div>

    <template v-if="mode === 'workout'">
      <section v-for="(log, i) in workout.exercises" :key="log.id" class="exercise">
        <div v-if="log.targetReps === null" class="exercise-head">
          <span class="name">{{ EXERCISE_NAMES[log.id] }}</span>
          <span class="target">{{ log.sets.length }}×max <strong>Bodyweight</strong></span>
        </div>
        <button v-else class="exercise-head" @click="editing = i">
          <span class="name">{{ EXERCISE_NAMES[log.id] }}</span>
          <span class="target">
            {{ log.sets.length }}×{{ log.targetReps }}
            <strong>{{ fmt(log.weight) }}</strong>
            <span class="chevron" aria-hidden="true">›</span>
          </span>
        </button>
        <div class="sets">
          <button
            v-for="(reps, j) in log.sets"
            :key="j"
            class="set"
            :class="setClass(log, reps)"
            :aria-label="`${EXERCISE_NAMES[log.id]} set ${j + 1}: ${reps === null ? 'not logged' : `${reps} reps`}`"
            @click="onTap(i, j)"
          >
            {{ reps ?? log.targetReps ?? '+' }}
          </button>
        </div>
        <p v-if="log.targetReps !== null" class="plates">{{ platesText(log.weight, log.id) }}</p>
        <p v-else class="plates">As many reps as you can. Tap a set to enter them.</p>
      </section>
      <p class="hint">Tap a set when you finish it. Tap again for every rep you missed.</p>
    </template>

    <template v-else>
      <section v-for="log in weightedLogs" :key="log.id" class="card warmup">
        <div class="row">
          <h2>{{ EXERCISE_NAMES[log.id] }}</h2>
          <span class="muted">Work set {{ fmt(log.weight) }}</span>
        </div>
        <p
          v-if="warmupSets(log.weight, emptyWeightFor(log.id), defaults.step).length === 0"
          class="muted"
        >
          No warmup needed at this weight.
        </p>
        <ol v-else>
          <li
            v-for="(set, k) in warmupSets(log.weight, emptyWeightFor(log.id), defaults.step)"
            :key="k"
          >
            <span class="reps">{{ set.sets }}×{{ set.reps }}</span>
            <strong>{{ fmt(set.weight) }}</strong>
            <span class="muted">{{ platesText(set.weight, log.id) }}</span>
          </li>
        </ol>
      </section>
    </template>

    <RestTimer />

    <BottomSheet
      v-if="editedLog && editing !== null"
      :title="EXERCISE_NAMES[editedLog.id]"
      @close="editing = null"
    >
      <div class="row">
        <span class="muted">Weight</span>
        <WeightStepper
          :model-value="editedLog.weight"
          :step="defaults.step"
          :min="defaults.step"
          label="weight"
          :format="(value: number) => fmt(value)"
          @update:model-value="setActiveWeight(editing, $event)"
        />
      </div>
      <BarbellPlates :plates="loadFor(editedLog.weight, editedLog.id).plates" :largest="defaults.plates[0]" />
      <p class="sheet-plates">{{ platesText(editedLog.weight, editedLog.id) }}</p>
      <p class="muted sheet-bar">
        {{ isMachine(editedLog.id) ? 'Plates only. The sled is not counted.' : `Bar ${fmt(state.settings.bar)}` }}
      </p>
      <button class="btn btn-primary" @click="editing = null">Done</button>
    </BottomSheet>

    <BottomSheet
      v-if="entry"
      :title="`${EXERCISE_NAMES[workout.exercises[entry.exerciseIndex].id]} · Set ${entry.setIndex + 1}`"
      @close="entry = null"
    >
      <div class="row">
        <span class="muted">Reps</span>
        <WeightStepper v-model="entry.reps" :step="1" label="reps" />
      </div>
      <button class="btn btn-primary" @click="saveEntry(entry.reps)">Save set</button>
      <button class="btn" @click="saveEntry(null)">Clear set</button>
    </BottomSheet>

    <BottomSheet
      v-if="confirming"
      :title="nothingLogged ? 'Nothing logged' : 'Finish early?'"
      @close="confirming = false"
    >
      <p class="muted">
        {{
          nothingLogged
            ? 'This workout has no sets. It will not be saved.'
            : 'Some sets are not logged. Unfinished exercises count as missed, so their weight stays the same next time.'
        }}
      </p>
      <button v-if="nothingLogged" class="btn btn-danger" @click="discard">Discard workout</button>
      <button v-else class="btn btn-primary" @click="finish">Finish workout</button>
      <button class="btn" @click="confirming = false">Keep going</button>
    </BottomSheet>
  </div>
</template>

<style scoped>
.workout {
  padding: calc(env(safe-area-inset-top) + 8px) 16px calc(env(safe-area-inset-bottom) + 132px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.bar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
}

.bar h1 {
  font-size: 17px;
  font-weight: 700;
}

.bar .link:first-child {
  justify-self: start;
}

.bar .link:last-child {
  justify-self: end;
}

.exercise {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.exercise-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  text-align: left;
}

.name {
  font-size: 19px;
  font-weight: 700;
}

.target {
  color: var(--muted);
  font-size: 15px;
}

.target strong {
  color: var(--text);
  margin-left: 4px;
}

.chevron {
  color: var(--accent);
  font-size: 20px;
  margin-left: 4px;
}

.sets {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.set {
  aspect-ratio: 1;
  max-width: 68px;
  width: 100%;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--muted);
  font-size: 20px;
  font-weight: 700;
  transition:
    background-color 0.12s ease,
    transform 0.08s ease;
}

.set:active {
  transform: scale(0.94);
}

.set.done {
  background: var(--accent);
  color: var(--accent-ink);
}

.set.missed {
  background: transparent;
  color: var(--warn);
  box-shadow: inset 0 0 0 2px var(--warn);
}

.plates {
  font-size: 13px;
  color: var(--muted);
}

.hint {
  font-size: 13px;
  color: var(--muted);
  text-align: center;
}

.warmup {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.warmup h2 {
  font-size: 17px;
}

.warmup ol {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.warmup li {
  display: grid;
  grid-template-columns: 44px 72px 1fr;
  align-items: baseline;
  font-size: 15px;
}

.warmup li .muted {
  font-size: 13px;
}

.reps {
  color: var(--accent);
  font-weight: 700;
}

.sheet-plates {
  text-align: center;
  font-weight: 700;
}

.sheet-bar {
  text-align: center;
  font-size: 13px;
  margin-top: -12px;
}
</style>
