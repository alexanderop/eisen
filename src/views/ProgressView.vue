<script setup lang="ts">
import { Card, ChipGroup, Segmented } from '../components/ui'
import { computed, ref, watch } from 'vue'
import TimeChart, { type ChartPoint, type ChartSeries } from '../components/TimeChart.vue'
import TrainingCalendar from '../components/TrainingCalendar.vue'
import { calendarWeeks, weekStreak } from '../domain/chart'
import type { Equipment } from '../domain/exercises'
import { outcome, stepFor, totalReps, type ExerciseLog } from '../domain/program'
import { recordIds, recordOf } from '../domain/records'
import { convert, roundTo, type Unit } from '../domain/units'
import { fmt, formatDate, formatShortDate } from '../format'
import { findExercise, progressOf, state } from '../store'

interface Option {
  id: string
  name: string
  equipment: Equipment
}

/** Exercises in the current workouts first, then ones that only exist in the history. */
const options = computed(() => {
  const found = new Map<string, Option>()
  for (const routine of state.routines) {
    for (const slot of routine.slots) {
      const exercise = findExercise(slot.exerciseId)
      if (exercise) found.set(exercise.id, exercise)
    }
  }
  for (const workout of state.history) {
    for (const log of workout.exercises) {
      if (!found.has(log.id)) found.set(log.id, findExercise(log.id) ?? log)
    }
  }
  return [...found.values()]
})

/** Weights logged in the other unit are rounded to what the plates can load, not 176.37lb. */
function inCurrentUnit(weight: number, unit: Unit, equipment: Equipment): number {
  const current = state.settings.unit
  if (unit === current) return weight
  return roundTo(convert(weight, unit, current), stepFor(equipment, current))
}

// Consistency

const WEEKS = 12
const now = Date.now()
const weeks = computed(() => calendarWeeks(state.history, now, WEEKS))
const consistency = computed(() => {
  const counts = weeks.value.map((week) => week.reduce((sum, day) => sum + day.workouts.length, 0))
  const firstTrained = counts.findIndex((count) => count > 0)
  const total = counts.reduce((sum, count) => sum + count, 0)
  return {
    thisWeek: counts[counts.length - 1],
    streak: weekStreak(
      state.history.map((workout) => workout.startedAt),
      now,
    ),
    // Weeks before the first workout do not pull the average down.
    perWeek: firstTrained === -1 ? 0 : total / (counts.length - firstTrained),
  }
})

// Range: scopes both charts below it.

type Range = '3m' | '1y' | 'all'
const RANGES: { value: Range; label: string }[] = [
  { value: '3m', label: '3 months' },
  { value: '1y', label: '1 year' },
  { value: 'all', label: 'All' },
]
const DAYS: Record<Range, number> = { '3m': 91, '1y': 365, all: Infinity }
const range = ref<Range>('3m')

interface Entry {
  time: number
  unit: Unit
  log: ExerciseLog
  record: boolean
}

/** Every logged exercise in the range, oldest first. Records count against the whole history. */
const entries = computed<Entry[]>(() => {
  const cutoff = now - DAYS[range.value] * 24 * 60 * 60 * 1000
  return state.history
    .flatMap((workout, index) => {
      if (workout.startedAt < cutoff) return []
      const records = recordIds(state.history, index)
      return workout.exercises
        .filter((log) => log.sets.some((reps) => reps !== null))
        .map((log) => ({ time: workout.startedAt, unit: workout.unit, log, record: records.has(log.id) }))
    })
    .reverse()
})

function valueOf(entry: Entry): number {
  return entry.log.equipment === 'bodyweight'
    ? totalReps(entry.log)
    : inCurrentUnit(entry.log.weight, entry.unit, entry.log.equipment)
}

/**
 * `record` says the workout beat everything before it. With steady progress that is every
 * workout, so the chart only marks `best`: the record that still stands.
 */
function pointsOf(id: string): (ChartPoint & { log: ExerciseLog; best: boolean })[] {
  const own = entries.value.filter((entry) => entry.log.id === id)
  const standing = recordOf(state.history, id)?.log
  return own.map((entry, index) => {
    const value = valueOf(entry)
    return {
      time: entry.time,
      value,
      log: entry.log,
      record: entry.record,
      best: entry.log === standing,
      missed: outcome(entry.log) === 'fail',
      deload: entry.log.equipment !== 'bodyweight' && index > 0 && value < valueOf(own[index - 1]),
    }
  })
}

// All lifts: every weighted exercise as its own line on one axis.

/** Colours checked for colour-blind separation on the card surface. A lift keeps its colour. */
const SERIES_COLORS = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#0ca30c', '#9085e9', '#e66767']

const weighted = computed(() => options.value.filter((option) => option.equipment !== 'bodyweight'))
const lifts = computed<ChartSeries[]>(() =>
  weighted.value
    .slice(0, SERIES_COLORS.length)
    .map((option, index) => ({
      id: option.id,
      name: option.name,
      color: SERIES_COLORS[index],
      points: pointsOf(option.id).map(({ time, value }) => ({ time, value })),
    }))
    .filter((series) => series.points.length > 0),
)
const liftsAt = ref<number | null>(null)

/** Each lift's weight on the day under the crosshair: the last one logged up to then. */
const liftRows = computed(() =>
  lifts.value.map((series) => {
    const upTo = liftsAt.value ?? Infinity
    const point = series.points.findLast((entry) => entry.time <= upTo)
    return { ...series, value: point ? fmt(point.value) : '–' }
  }),
)

// One exercise, with what happened in every workout.

const picked = ref<string | null>(null)
const selected = computed<Option | null>(
  () => options.value.find((option) => option.id === picked.value) ?? options.value[0] ?? null,
)
const bodyweight = computed(() => selected.value?.equipment === 'bodyweight')
const points = computed(() => (selected.value ? pointsOf(selected.value.id) : []))
const series = computed<ChartSeries[]>(() =>
  selected.value && points.value.length > 0
    ? [
        {
          id: selected.value.id,
          name: selected.value.name,
          color: 'var(--primary)',
          points: points.value.map((point) => ({ ...point, record: point.best })),
        },
      ]
    : [],
)
const pointAt = ref<number | null>(null)
watch([selected, range], () => (pointAt.value = null))

function format(value: number): string {
  return bodyweight.value ? `${value} reps` : fmt(value)
}

/** The workout under the crosshair, or the latest one when the chart is at rest. */
const shown = computed(() => {
  const point =
    points.value.find((entry) => entry.time === pointAt.value) ?? points.value[points.value.length - 1]
  if (!point) return null
  return {
    date: formatDate(point.time),
    value: format(point.value),
    sets: point.log.sets.map((reps) => reps ?? '–').join(' '),
    notes: [
      point.best ? 'Best ever' : point.record ? 'Record that day' : '',
      point.missed ? 'Missed reps' : '',
      point.deload ? 'Deload' : '',
    ].filter(Boolean),
  }
})

const current = computed(() => {
  if (!selected.value) return ''
  return bodyweight.value ? 'Total reps per workout' : `Now ${fmt(progressOf(selected.value.id).weight)}`
})

/** The best fully completed workout of the exercise ever, and the day it happened. */
const record = computed(() => {
  const found = selected.value && recordOf(state.history, selected.value.id)
  if (!found) return null
  const { workout, log } = found
  return {
    value: format(
      log.equipment === 'bodyweight'
        ? totalReps(log)
        : inCurrentUnit(log.weight, workout.unit, log.equipment),
    ),
    date: formatShortDate(workout.startedAt),
  }
})

const gained = computed(() =>
  points.value.length === 0 ? 0 : points.value[points.value.length - 1].value - points.value[0].value,
)
</script>

<template>
  <h1 class="page-title">Progress</h1>

  <Card v-if="state.history.length === 0" as="p" class="muted">
    Finish your first workout and your progress shows up here.
  </Card>

  <template v-else>
    <Card as="section">
      <div class="row head">
        <h2>Consistency</h2>
        <span class="muted">Last {{ WEEKS }} weeks</span>
      </div>
      <TrainingCalendar :weeks="weeks" />
      <dl>
        <div>
          <dt>This week</dt>
          <dd>{{ consistency.thisWeek }}</dd>
        </div>
        <div>
          <dt>Weeks in a row</dt>
          <dd>{{ consistency.streak }}</dd>
        </div>
        <div>
          <dt>Per week</dt>
          <dd>{{ consistency.perWeek.toFixed(1) }}</dd>
        </div>
      </dl>
    </Card>

    <Segmented v-model="range" :options="RANGES" />

    <Card v-if="lifts.length > 1" as="section">
      <div class="row head">
        <h2>All lifts</h2>
        <span class="muted">{{ liftsAt === null ? 'Latest' : formatDate(liftsAt) }}</span>
      </div>
      <TimeChart
        v-model:active="liftsAt"
        :key="range"
        :series="lifts"
        :format="(value: number) => fmt(value)"
        :format-time="formatShortDate"
        label="Weight of every lift over time"
      />
      <ul class="legend">
        <li v-for="row in liftRows" :key="row.id">
          <span class="key" :style="{ background: row.color }" aria-hidden="true" />
          <span class="name">{{ row.name }}</span>
          <strong>{{ row.value }}</strong>
        </li>
      </ul>
      <p v-if="weighted.length > SERIES_COLORS.length" class="muted note">
        Showing the first {{ SERIES_COLORS.length }} exercises.
      </p>
    </Card>

    <ChipGroup
      v-if="selected"
      :model-value="selected.id"
      :options="options.map((option) => ({ value: option.id, label: option.name }))"
      label="Exercise"
      bleed
      @update:model-value="picked = $event"
    />

    <Card v-if="selected" as="section">
      <div class="row head">
        <h2>{{ selected.name }}</h2>
        <span class="muted">{{ current }}</span>
      </div>
      <p v-if="!shown" class="muted empty">No logged sets for this exercise in this time.</p>
      <template v-else>
        <div class="readout" role="status">
          <strong class="value">{{ shown.value }}</strong>
          <span class="sets">{{ shown.sets }}</span>
          <span class="muted date">
            {{ shown.date }}<template v-for="note in shown.notes" :key="note"> · {{ note }}</template>
          </span>
        </div>
        <TimeChart
          v-model:active="pointAt"
          :key="`${selected.id}-${range}`"
          :series="series"
          :format="(value: number) => (bodyweight ? String(value) : fmt(value))"
          :format-time="formatShortDate"
          :label="`${selected.name} over time`"
          events
        />
        <ul class="marks" aria-label="What the marks mean">
          <li>
            <svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="4.5" class="done" /></svg>
            All reps
          </li>
          <li v-if="!bodyweight">
            <svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="4.5" class="missed" /></svg>
            Missed reps
          </li>
          <li v-if="!bodyweight">
            <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M2.5,3.5 L13.5,3.5 L8,13.5 Z" class="done" /></svg>
            Deload
          </li>
          <li>
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <circle cx="8" cy="8" r="3" class="done" />
              <circle cx="8" cy="8" r="6.5" class="halo" />
            </svg>
            Best ever
          </li>
        </ul>
        <dl>
          <div>
            <dt>Workouts</dt>
            <dd>{{ points.length }}</dd>
          </div>
          <div>
            <dt>Best ever</dt>
            <dd>{{ record?.value ?? '–' }}</dd>
            <dd v-if="record" class="when">{{ record.date }}</dd>
          </div>
          <div>
            <dt>Gained</dt>
            <dd>{{ gained >= 0 ? '+' : '' }}{{ format(gained) }}</dd>
          </div>
        </dl>
      </template>
    </Card>
  </template>
</template>

<style scoped>
.head {
  margin-bottom: 12px;
}

h2 {
  font-size: 18px;
}

.empty {
  padding: 32px 0;
  text-align: center;
}

.readout {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: baseline;
  gap: 0 12px;
  margin-bottom: 8px;
}

.readout .value {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.readout .sets {
  justify-self: end;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.readout .date {
  grid-column: 1 / -1;
  font-size: 13px;
}

.legend,
.marks {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.legend li {
  display: grid;
  grid-template-columns: 16px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 15px;
}

.legend .key {
  height: 3px;
  border-radius: 2px;
}

.legend .name {
  color: var(--muted-foreground);
}

.legend strong {
  font-variant-numeric: tabular-nums;
}

.note {
  margin-top: 4px;
  font-size: 13px;
}

.marks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 12px;
  color: var(--muted-foreground);
}

.marks li {
  display: flex;
  align-items: center;
  gap: 5px;
}

.marks svg {
  width: 14px;
  height: 14px;
}

.marks .done {
  fill: var(--primary);
}

.marks .missed {
  fill: none;
  stroke: var(--warn);
  stroke-width: 2;
}

.marks .halo {
  fill: none;
  stroke: var(--primary);
  stroke-width: 1.5;
  opacity: 0.7;
}

dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

dt {
  font-size: 12px;
  color: var(--muted-foreground);
}

dd {
  margin: 0;
  font-weight: 700;
  font-size: 17px;
}

dd.when {
  font-size: 12px;
  font-weight: 400;
  color: var(--muted-foreground);
}
</style>
