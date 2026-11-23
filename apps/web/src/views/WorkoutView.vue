<script setup lang="ts">
import { Timer } from '@lucide/vue'
import { Button, Card, ConfirmButton, Segmented, SetButton, Sheet, Stepper } from '../components/ui'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import BarbellPlates from '../components/BarbellPlates.vue'
import ExerciseImage from '../components/ExerciseImage.vue'
import ExercisePicker from '../components/ExercisePicker.vue'
import RestTimer from '../components/RestTimer.vue'
import { warmupSets } from '../domain/plates'
import {
  holdText,
  incrementStep,
  isComplete,
  isPlateLoaded,
  outcome,
  rirLine,
  rirOf,
  rirSpoken,
  rirText,
  setText,
  stepFor,
  UNIT_DEFAULTS,
  warmupStep,
  type ExerciseLog,
} from '../domain/program'
import { lastLogged } from '../domain/records'
import {
  ADDED_WEIGHT_HINT,
  emptyWeightFor,
  fmt,
  fmtAdded,
  fmtLoad,
  formatShortDate,
  loadFor,
  platesText,
} from '../format'
import { haptic } from '../haptics'
import { hold, startHold } from '../hold'
import { openSetDone, startRest, stopRest } from '../rest'
import {
  addActiveExercise,
  deferActiveExercise,
  discardWorkout,
  findExercise,
  finishWorkout,
  logReps,
  progressOf,
  removeActiveExercise,
  setActiveWeight,
  setIncrement,
  state,
  lastReps,
  tapSet,
  warmupDone,
} from '../store'
import { useWakeLock } from '../wakeLock'

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

/** A logged set as the lifter reads it: reps, or the hold of a timed exercise. */
function setsText(log: ExerciseLog, separator: string): string {
  return log.sets.map((count) => (count === null ? '–' : setText(log.timed, count))).join(separator)
}

/** "3×5", "3×1:00" for a hold, or "3×max". */
function scheme(log: ExerciseLog): string {
  const target = log.targetReps === null ? 'max' : setText(log.timed, log.targetReps)
  return `${log.sets.length}×${target}`
}

/**
 * "12 Nov: 80kg · 5 5 5 4 3", so the lifter sees what there is to beat.
 * Plain bodyweight shows only the reps, as there is no load worth naming.
 */
function lastTime(id: string): string | null {
  const last = lastLogged(state.history, id)
  if (!last) return null
  const { workout: then, log } = last
  const reps = setsText(log, ' ')
  const plain = log.equipment === 'bodyweight' && log.weight === 0
  const load = plain ? '' : `${fmtLoad(log.equipment, log.weight, then.unit)} · `
  return `${formatShortDate(then.startedAt)}: ${load}${reps}`
}

/** "RIR – – 2 1 0" after last time's reps, when sets were rated. */
function lastRir(id: string): string | null {
  const last = lastLogged(state.history, id)
  return last ? rirLine(last.log) : null
}

function toggleWarmup(key: string) {
  if (!warmupDone.value.delete(key)) warmupDone.value.add(key)
}

function onRemove(index: number) {
  removeActiveExercise(index)
  editing.value = null
}
/**
 * The exercise being worked on rises to the top, finished ones sink to the bottom and fold up,
 * so the top of the screen is what to do now. Starting with the third exercise because the
 * first one's machine is taken needs no sorting by hand.
 * The exercise tapped last stays where it is for a moment: the tap after a full set is
 * how missed reps are entered, and the card must not jump away under the thumb.
 */
const SETTLE_MS = 2500
const settling = ref<string | null>(null)
const expanded = ref(new Set<string>())
let settleTimer: ReturnType<typeof setTimeout> | undefined

function isStarted(log: ExerciseLog) {
  return log.sets.some((reps) => reps !== null)
}

function isDone(log: ExerciseLog) {
  return log.sets.every((reps) => reps !== null)
}

/** Exercises with sets logged, most recently worked on first. */
const current = ref<string[]>(
  (state.active?.exercises ?? []).filter((log) => isStarted(log) && !isDone(log)).map((log) => log.id),
)

function settle() {
  clearTimeout(settleTimer)
  const id = settling.value
  settling.value = null
  if (id === null) return
  const log = workout.value?.exercises.find((item) => item.id === id)
  const rises = log !== undefined && isStarted(log) && !isDone(log)
  const wasFirst = ordered.value[0]?.log.id === id
  current.value = [...(rises ? [id] : []), ...current.value.filter((item) => item !== id)]
  if (rises && !wasFirst) void nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

function touch(id: string) {
  if (settling.value !== id) settle()
  settling.value = id
  clearTimeout(settleTimer)
  settleTimer = setTimeout(settle, SETTLE_MS)
}

const ordered = computed(() => {
  const items = (workout.value?.exercises ?? []).map((log, index) => ({
    log,
    index,
    done: log.id !== settling.value && isDone(log),
  }))
  const open = items.filter((item) => !item.done)
  const rank = (id: string) => current.value.indexOf(id)
  return [
    ...open.filter((item) => rank(item.log.id) !== -1).sort((a, b) => rank(a.log.id) - rank(b.log.id)),
    ...open.filter((item) => rank(item.log.id) === -1),
    ...items.filter((item) => item.done),
  ]
})
const openCount = computed(() => ordered.value.length - doneCount.value)

function onDefer(index: number) {
  const id = workout.value?.exercises[index]?.id
  current.value = current.value.filter((item) => item !== id)
  deferActiveExercise(index)
  editing.value = null
}

const doneCount = computed(() => ordered.value.filter((item) => item.done).length)

function toggle(id: string) {
  if (!expanded.value.delete(id)) expanded.value.add(id)
}

const nothingLogged = computed(
  () => workout.value?.exercises.every((log) => outcome(log) === 'skipped') ?? true,
)

/** A timed set opens the hold timer. Tapping one that is logged clears it. */
function onTapHold(exerciseIndex: number, setIndex: number) {
  const log = workout.value?.exercises[exerciseIndex]
  if (!log) return
  if (log.sets[setIndex] === null) {
    startHold(log.id, setIndex, log.targetReps)
    return
  }
  logReps(exerciseIndex, setIndex, null)
  touch(log.id)
  haptic('tap')
  stopRest()
}

/** The card of a hold that just ended settles like after a tap. */
watch(
  () => hold.active,
  (active) => {
    if (!active && workout.value?.exercises.some((log) => log.id === hold.exerciseId)) {
      touch(hold.exerciseId)
    }
  },
)

function onTap(exerciseIndex: number, setIndex: number) {
  const active = workout.value
  if (!active) return
  const log = active.exercises[exerciseIndex]
  if (log.timed) {
    onTapHold(exerciseIndex, setIndex)
    return
  }
  if (log.targetReps === null) {
    // An open max-reps set takes last time's count with one tap: most days it is the same again.
    const last = lastReps(log.id, setIndex)
    if (log.sets[setIndex] === null && last !== null) {
      logMaxSet(exerciseIndex, setIndex, last)
      return
    }
    entry.value = { exerciseIndex, setIndex, reps: log.sets[setIndex] ?? FIRST_MAX_REPS }
    return
  }
  const reps = tapSet(exerciseIndex, setIndex)
  touch(log.id)
  if (reps !== null && reps < log.targetReps) haptic('warning')
  else haptic(outcome(log) === 'success' ? 'success' : 'tap')
  if (reps === null || lastSetLogged(log)) {
    stopRest()
    if (reps !== null) openSetDone({ exerciseId: log.id, exerciseIndex, setIndex })
    return
  }
  const done = reps === log.targetReps
  startRest(
    done ? state.settings.restDone : state.settings.restMissed,
    done ? 'All reps done.' : 'Missed reps. Rest longer.',
    { exerciseId: log.id, exerciseIndex, setIndex },
  )
}

/**
 * No rest follows the last set of an exercise: the lifter moves on to the next one,
 * and a timer for the old one would only be in the way.
 */
function lastSetLogged(log: ExerciseLog) {
  return log.sets.every((reps) => reps !== null)
}

function rirBadge(log: ExerciseLog, setIndex: number): string | undefined {
  const rir = rirOf(log, setIndex)
  return rir === null ? undefined : rirText(rir)
}

/** A set as a screen reader says it: "5 reps", "5 reps, 2 in reserve". */
function repsSpoken(log: ExerciseLog, setIndex: number, reps: number): string {
  const rir = rirOf(log, setIndex)
  return rir === null ? `${reps} reps` : `${reps} reps, ${rirSpoken(rir)} in reserve`
}

function setState(log: ExerciseLog, reps: number | null) {
  if (reps === null) return 'empty'
  return log.targetReps !== null && reps < log.targetReps ? 'missed' : 'done'
}

/** Starting point for a max-reps entry when there is no last time to go by. */
const FIRST_MAX_REPS = 8

function saveEntry(reps: number | null) {
  if (!entry.value) return
  logMaxSet(entry.value.exerciseIndex, entry.value.setIndex, reps)
  entry.value = null
}

function logMaxSet(exerciseIndex: number, setIndex: number, reps: number | null) {
  const active = workout.value
  if (!active) return
  const log = active.exercises[exerciseIndex]
  logReps(exerciseIndex, setIndex, reps)
  touch(log.id)
  haptic('tap')
  if (reps === null || lastSetLogged(log)) {
    stopRest()
    return
  }
  startRest(state.settings.restDone, 'Max set logged.', {
    exerciseId: log.id,
    exerciseIndex,
    setIndex,
  })
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

useWakeLock()

onBeforeUnmount(() => clearTimeout(settleTimer))
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
            <span class="done-reps">{{ setsText(log, ' · ') }}</span>
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
                {{ scheme(log) }}
                <strong>{{ fmtLoad(log.equipment, log.weight) }}</strong>
                <span class="chevron" aria-hidden="true">›</span>
              </span>
            </button>
            <div class="sets">
              <template v-if="log.timed">
                <SetButton
                  v-for="(seconds, j) in log.sets"
                  :key="j"
                  :state="setState(log, seconds)"
                  :class="{ long: (seconds ?? log.targetReps ?? 0) >= 600 }"
                  :aria-label="`${log.name} set ${j + 1}: ${seconds === null ? 'start the timer' : `held ${holdText(seconds)}`}`"
                  @click="onTap(i, j)"
                >
                  <Timer v-if="seconds === null && log.targetReps === null" :size="24" aria-hidden="true" />
                  <template v-else>{{ holdText(seconds ?? log.targetReps ?? 0) }}</template>
                </SetButton>
              </template>
              <template v-else>
                <SetButton
                  v-for="(reps, j) in log.sets"
                  :key="j"
                  :state="setState(log, reps)"
                  :badge="rirBadge(log, j)"
                  :aria-label="`${log.name} set ${j + 1}: ${reps === null ? 'not logged' : repsSpoken(log, j, reps)}`"
                  @click="onTap(i, j)"
                >
                  {{ reps ?? log.targetReps ?? lastReps(log.id, j) ?? '+' }}
                </SetButton>
              </template>
            </div>
            <p v-if="lastTime(log.id)" class="plates last">
              Last time, {{ lastTime(log.id) }}
              <span v-if="lastRir(log.id)" class="nowrap">· {{ lastRir(log.id) }}</span>
            </p>
            <p v-if="log.timed" class="plates">
              {{
                log.targetReps === null
                  ? 'Hold as long as you can. Tap a set to start the timer.'
                  : 'Tap a set to start the timer.'
              }}
            </p>
            <p v-else-if="log.targetReps === null" class="plates">
              {{
                log.sets.some((_, j) => lastReps(log.id, j) !== null)
                  ? "As many reps as you can. Tap a set to log last time's reps, tap it again to change them."
                  : 'As many reps as you can. Tap a set to enter them.'
              }}
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
      <template v-if="editedLog.equipment === 'bodyweight'">
        <div class="row">
          <span class="muted">Added weight</span>
          <Stepper
            :model-value="editedLog.weight"
            :step="stepFor(editedLog.equipment, state.settings.unit)"
            :min="0"
            label="added weight"
            :format="(value: number) => fmtAdded(value)"
            @update:model-value="setActiveWeight(editing, $event)"
          />
        </div>
        <p class="muted sheet-hint">{{ ADDED_WEIGHT_HINT }}</p>
      </template>
      <div v-else class="row">
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
        <span class="muted">{{ editedLog.timed ? 'Add after full holds' : 'Add after all reps' }}</span>
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
      <Button
        v-if="openCount > 1 && !isDone(editedLog)"
        variant="secondary"
        class="w-full"
        @click="onDefer(editing)"
      >
        Do later
      </Button>
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

/* "10:00" is the widest a target gets. */
.sets .long {
  font-size: var(--fs-base);
}

.plates {
  font-size: 13px;
  color: var(--muted-foreground);
}

.last {
  color: var(--foreground);
}

/* The ratings wrap as one, never with a lone "4+" on the next line. */
.nowrap {
  white-space: nowrap;
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

.sheet-bar,
.sheet-hint {
  font-size: 13px;
  margin-top: -12px;
}

.sheet-bar {
  text-align: center;
}
</style>
