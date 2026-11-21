<script setup lang="ts">
import { Button, Card, ConfirmButton, Segmented, SetButton, Sheet, Stepper } from '../components/ui'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import BarbellPlates from '../components/BarbellPlates.vue'
import ExerciseImage from '../components/ExerciseImage.vue'
import ExercisePicker from '../components/ExercisePicker.vue'
import RestTimer from '../components/RestTimer.vue'
import { warmupSets } from '../domain/plates'
import {
  incrementStep,
  isComplete,
  isPlateLoaded,
  outcome,
  stepFor,
  UNIT_DEFAULTS,
  warmupStep,
  type ExerciseLog,
} from '../domain/program'
import { lastLogged } from '../domain/records'
import { emptyWeightFor, fmt, formatShortDate, loadFor, platesText } from '../format'
import { haptic } from '../haptics'
import { startRest, stopRest } from '../rest'
import {
  addActiveExercise,
  discardWorkout,
  findExercise,
  finishWorkout,
  logReps,
  progressOf,
  removeActiveExercise,
  setActiveWeight,
  setIncrement,
  state,
  suggestedReps,
  tapSet,
  warmupDone,
} from '../store'

const emit = defineEmits<{ close: [] }>()

const workout = computed(() => state.active)
type Mode = 'workout' | 'warmup'
const MODES: { value: Mode; label: string }[] = [
  { value: 'workout', label: 'Workout' },
  { value: 'warmup', label: 'Warmup' },
]
const mode = ref<Mode>('workout')
const editing = ref<number | null>(null)
const confirming = ref(false)
const picking = ref(false)

function onPick(id: string) {
  addActiveExercise(id)
  picking.value = false
}
/** The max-reps set being entered. */
const entry = ref<{ exerciseIndex: number; setIndex: number; reps: number } | null>(null)

const defaults = computed(() => UNIT_DEFAULTS[state.settings.unit])
const editedLog = computed(() =>
  editing.value === null ? null : (workout.value?.exercises[editing.value] ?? null),
)
const warmupLogs = computed(
  () => workout.value?.exercises.filter((log) => isPlateLoaded(log.equipment)) ?? [],
)

function warmups(log: ExerciseLog) {
  return warmupSets(
    log.weight,
    emptyWeightFor(log.equipment),
    warmupStep(log.equipment, state.settings.unit),
  )
}

/** "12 Nov: 80kg · 5 5 5 4 3", so the lifter sees what there is to beat. */
function lastTime(id: string): string | null {
  const last = lastLogged(state.history, id)
  if (!last) return null
  const { workout: then, log } = last
  const reps = log.sets.map((count) => count ?? '–').join(' ')
  const load = log.equipment === 'bodyweight' ? '' : `${fmt(log.weight, then.unit)} · `
  return `${formatShortDate(then.startedAt)}: ${load}${reps}`
}

function toggleWarmup(key: string) {
  if (!warmupDone.value.delete(key)) warmupDone.value.add(key)
}

function onRemove(index: number) {
  removeActiveExercise(index)
  editing.value = null
}
/**
 * Finished exercises sink to the bottom and fold up, so the top of the screen is what is left to do.
 * The exercise tapped last stays where it is for a moment: the tap after a full set is
 * how missed reps are entered, and the card must not jump away under the thumb.
 */
const SETTLE_MS = 2500
const settling = ref<string | null>(null)
const expanded = ref(new Set<string>())
let settleTimer: ReturnType<typeof setTimeout> | undefined

function touch(id: string) {
  settling.value = id
  clearTimeout(settleTimer)
  settleTimer = setTimeout(() => (settling.value = null), SETTLE_MS)
}

const ordered = computed(() => {
  const items = (workout.value?.exercises ?? []).map((log, index) => ({
    log,
    index,
    done: log.id !== settling.value && log.sets.every((reps) => reps !== null),
  }))
  return [...items.filter((item) => !item.done), ...items.filter((item) => item.done)]
})
const doneCount = computed(() => ordered.value.filter((item) => item.done).length)

function toggle(id: string) {
  if (!expanded.value.delete(id)) expanded.value.add(id)
}

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
  touch(log.id)
  if (reps !== null && reps < log.targetReps) haptic('warning')
  else haptic(outcome(log) === 'success' ? 'success' : 'tap')
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

function setState(log: ExerciseLog, reps: number | null) {
  if (reps === null) return 'empty'
  return log.targetReps !== null && reps < log.targetReps ? 'missed' : 'done'
}

function saveEntry(reps: number | null) {
  const active = workout.value
  if (!active || !entry.value) return
  logReps(entry.value.exerciseIndex, entry.value.setIndex, reps)
  touch(active.exercises[entry.value.exerciseIndex].id)
  entry.value = null
  haptic('tap')
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
  haptic('done')
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
  clearTimeout(settleTimer)
  document.removeEventListener('visibilitychange', keepScreenOn)
  void wakeLock?.release()
})
</script>

<template>
  <div v-if="workout" class="workout">
    <header class="bar">
      <Button class="px-1" variant="ghost" size="md" @click="emit('close')">‹ Back</Button>
      <h1>{{ workout.name }}</h1>
      <Button class="px-1" variant="ghost" size="md" @click="onFinish">Finish</Button>
      <RestTimer />
    </header>

    <Segmented v-model="mode" :options="MODES" />

    <template v-if="mode === 'workout'">
      <template v-for="({ log, index: i, done }, position) in ordered" :key="log.id">
        <p v-if="done && position === ordered.length - doneCount" class="eyebrow done-title">
          Done · {{ doneCount }} of {{ ordered.length }}
        </p>
        <Card as="section" class="exercise" :class="{ done }">
          <button
            v-if="done"
            class="done-head"
            :aria-expanded="expanded.has(log.id)"
            @click="toggle(log.id)"
          >
            <ExerciseImage :image="findExercise(log.id)?.image" :name="log.name" :size="40" />
            <span class="done-name">{{ log.name }}</span>
            <span class="done-reps">{{ log.sets.join(' · ') }}</span>
            <span class="chevron" aria-hidden="true">{{ expanded.has(log.id) ? '⌃' : '⌄' }}</span>
          </button>
          <ExerciseImage
            v-else-if="position === 0"
            :image="findExercise(log.id)?.image"
            :name="log.name"
            variant="banner"
          />
          <div v-if="!done || expanded.has(log.id)" class="exercise-body">
            <button class="exercise-head" @click="editing = i">
              <ExerciseImage
                v-if="!done && position > 0"
                :image="findExercise(log.id)?.image"
                :name="log.name"
                :size="40"
              />
              <span class="name">{{ done ? 'Sets' : log.name }}</span>
              <span class="target">
                {{ log.sets.length }}×{{ log.targetReps ?? 'max' }}
                <strong>{{ log.equipment === 'bodyweight' ? 'BW' : fmt(log.weight) }}</strong>
                <span class="chevron" aria-hidden="true">›</span>
              </span>
            </button>
            <div class="sets">
              <SetButton
                v-for="(reps, j) in log.sets"
                :key="j"
                :state="setState(log, reps)"
                :aria-label="`${log.name} set ${j + 1}: ${reps === null ? 'not logged' : `${reps} reps`}`"
                @click="onTap(i, j)"
              >
                {{ reps ?? log.targetReps ?? '+' }}
              </SetButton>
            </div>
            <p v-if="lastTime(log.id)" class="plates last">Last time, {{ lastTime(log.id) }}</p>
            <p v-if="log.targetReps === null" class="plates">
              As many reps as you can. Tap a set to enter them.
            </p>
            <p v-else-if="isPlateLoaded(log.equipment)" class="plates">
              {{ platesText(log.weight, log.equipment) }}
            </p>
          </div>
        </Card>
      </template>
      <Button variant="secondary" class="w-full" @click="picking = true">Add exercise</Button>
      <Button class="w-full" v-if="doneCount === ordered.length" @click="onFinish">
        Finish workout
      </Button>
      <p class="hint">Tap a set when you finish it. Tap again for every rep you missed.</p>
    </template>

    <template v-else>
      <p v-if="warmupLogs.length === 0" class="hint">
        Warmup sets are worked out for barbell and plate machine exercises.
      </p>
      <Card v-for="log in warmupLogs" as="section" class="warmup" :key="log.id">
        <div class="row">
          <h2>{{ log.name }}</h2>
          <span class="muted">Work set {{ fmt(log.weight) }}</span>
        </div>
        <p v-if="warmups(log).length === 0" class="muted">No warmup needed at this weight.</p>
        <ol v-else>
          <li v-for="(set, k) in warmups(log)" :key="k">
            <button
              :aria-pressed="warmupDone.has(`${log.id}:${k}`)"
              @click="toggleWarmup(`${log.id}:${k}`)"
            >
              <span class="check" aria-hidden="true">✓</span>
              <span class="reps">{{ set.sets }}×{{ set.reps }}</span>
              <strong>{{ fmt(set.weight) }}</strong>
              <span class="muted">{{ platesText(set.weight, log.equipment) }}</span>
            </button>
          </li>
        </ol>
      </Card>
    </template>

    <ExercisePicker
      v-if="picking"
      :exclude="workout.exercises.map((log) => log.id)"
      @pick="onPick"
      @close="picking = false"
    />

    <Sheet
      v-if="editedLog && editing !== null"
      :title="editedLog.name"
      @close="editing = null"
    >
      <div v-if="editedLog.equipment !== 'bodyweight'" class="row">
        <span class="muted">Weight</span>
        <Stepper
          :model-value="editedLog.weight"
          :step="stepFor(editedLog.equipment, state.settings.unit)"
          :min="stepFor(editedLog.equipment, state.settings.unit)"
          label="weight"
          :format="(value: number) => fmt(value)"
          @update:model-value="setActiveWeight(editing, $event)"
        />
      </div>
      <template v-if="isPlateLoaded(editedLog.equipment)">
        <BarbellPlates
          :plates="loadFor(editedLog.weight, editedLog.equipment).plates"
          :largest="defaults.plates[0]"
        />
        <p class="sheet-plates">{{ platesText(editedLog.weight, editedLog.equipment) }}</p>
        <p class="muted sheet-bar">
          {{
            editedLog.equipment === 'machine'
              ? 'Plates only. The sled is not counted.'
              : `Bar ${fmt(state.settings.bar)}`
          }}
        </p>
      </template>
      <div v-if="editedLog.targetReps !== null && editedLog.equipment !== 'bodyweight'" class="row">
        <span class="muted">Add after all reps</span>
        <Stepper
          :model-value="progressOf(editedLog.id).increment"
          :step="incrementStep(state.settings.unit)"
          :min="incrementStep(state.settings.unit)"
          label="weight added after a full workout"
          :format="(value: number) => `+${fmt(value)}`"
          @update:model-value="setIncrement(editedLog.id, $event)"
        />
      </div>
      <Button class="w-full" @click="editing = null">Done</Button>
      <ConfirmButton
        v-if="workout.exercises.length > 1"
        :key="editedLog.id"
        block
        label="Remove from this workout"
        confirm-label="Tap again to remove"
        @confirm="onRemove(editing)"
      />
    </Sheet>

    <Sheet
      v-if="entry"
      :title="`${workout.exercises[entry.exerciseIndex].name} · Set ${entry.setIndex + 1}`"
      @close="entry = null"
    >
      <div class="row">
        <span class="muted">Reps</span>
        <Stepper v-model="entry.reps" :step="1" label="reps" />
      </div>
      <Button class="w-full" @click="saveEntry(entry.reps)">Save set</Button>
      <Button variant="secondary" class="w-full" @click="saveEntry(null)">Clear set</Button>
    </Sheet>

    <Sheet
      v-if="confirming"
      :title="nothingLogged ? 'Nothing logged' : 'Finish early?'"
      @close="confirming = false"
    >
      <p class="muted">
        {{
          nothingLogged
            ? 'This workout has no sets. It will not be saved.'
            : 'Some sets are not logged. Exercises you started keep their weight next time. Exercises you did not start are skipped.'
        }}
      </p>
      <Button variant="destructive" class="w-full" v-if="nothingLogged" @click="discard">Discard workout</Button>
      <Button class="w-full" v-else @click="finish">Finish workout</Button>
      <Button variant="secondary" class="w-full" @click="confirming = false">Keep going</Button>
    </Sheet>
  </div>
</template>

<style scoped>
.workout {
  padding: 0 16px calc(env(safe-area-inset-bottom) + 32px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Stays in view. The rest timer hangs below it, at the top of the screen and away from the thumb. */
.bar {
  position: sticky;
  top: 0;
  z-index: 10;
  margin: 0 -16px;
  padding: calc(env(safe-area-inset-top) + 8px) 16px 8px;
  min-height: 60px;
  background: var(--background);
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
}

.bar h1 {
  font-size: 17px;
  font-weight: 700;
}

.bar > :first-child {
  justify-self: start;
}

.bar > :nth-child(3) {
  justify-self: end;
}

.exercise {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.done-title {
  margin-top: 8px;
}

.done-head {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 16px 10px 10px;
  text-align: left;
}

.done-name {
  flex: 1;
  font-weight: 700;
}

.done-reps {
  color: var(--primary);
  font-weight: 600;
  font-size: 14px;
}

.done .exercise-body {
  padding-top: 0;
}

.exercise-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 16px 16px;
}

.exercise-head {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  text-align: left;
}

.name {
  flex: 1;
  font-size: 19px;
  font-weight: 700;
}

.target {
  color: var(--muted-foreground);
  font-size: 15px;
}

.target strong {
  color: var(--foreground);
  margin-left: 4px;
}

.chevron {
  color: var(--primary);
  font-size: 20px;
  margin-left: 4px;
}

.sets {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.plates {
  font-size: 13px;
  color: var(--muted-foreground);
}

.last {
  color: var(--foreground);
}

.hint {
  font-size: 13px;
  color: var(--muted-foreground);
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
}

.warmup li button {
  display: grid;
  grid-template-columns: 32px 44px 72px 1fr;
  align-items: center;
  width: 100%;
  min-height: 44px;
  text-align: left;
  font-size: 15px;
}

.warmup li .muted {
  font-size: 13px;
}

.check {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid var(--border);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 800;
  color: transparent;
}

.warmup [aria-pressed='true'] .check {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}

.warmup [aria-pressed='true'] :is(strong, .reps) {
  color: var(--muted-foreground);
  text-decoration: line-through;
}

.reps {
  color: var(--primary);
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
