<script setup lang="ts">
import { Card, ChipGroup } from '../components/ui'
import { computed, ref } from 'vue'
import LineChart from '../components/LineChart.vue'
import type { Equipment } from '../domain/exercises'
import { stepFor, totalReps } from '../domain/program'
import { convert, roundTo, type Unit } from '../domain/units'
import { fmt } from '../format'
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

const picked = ref<string | null>(null)
const selected = computed<Option | null>(
  () => options.value.find((option) => option.id === picked.value) ?? options.value[0] ?? null,
)
const bodyweight = computed(() => selected.value?.equipment === 'bodyweight')

/**
 * One value per workout for the selected exercise, oldest first.
 * Weighted exercises chart the weight in the current unit, bodyweight ones the total reps.
 */
const values = computed(() =>
  state.history
    .slice()
    .reverse()
    .flatMap((workout) =>
      workout.exercises
        .filter((log) => log.id === selected.value?.id && log.sets.some((reps) => reps !== null))
        .map((log) =>
          log.equipment === 'bodyweight'
            ? totalReps(log)
            : inCurrentUnit(log.weight, workout.unit, log.equipment),
        ),
    ),
)

/** Weights logged in the other unit are rounded to what the plates can load, not 176.37lb. */
function inCurrentUnit(weight: number, unit: Unit, equipment: Equipment): number {
  const current = state.settings.unit
  if (unit === current) return weight
  return roundTo(convert(weight, unit, current), stepFor(equipment, current))
}

const current = computed(() => {
  if (!selected.value) return ''
  return bodyweight.value
    ? 'Total reps per workout'
    : `Now ${fmt(progressOf(selected.value.id).weight)}`
})

function format(value: number): string {
  return bodyweight.value ? `${value} reps` : fmt(value)
}

const best = computed(() => Math.max(...values.value))
const gained = computed(() => values.value[values.value.length - 1] - values.value[0])
</script>

<template>
  <h1 class="page-title">Progress</h1>

  <ChipGroup
    v-if="selected"
    :model-value="selected.id"
    :options="options.map((option) => ({ value: option.id, label: option.name }))"
    label="Exercise"
    bleed
    @update:model-value="picked = $event"
  />

  <Card v-if="!selected" as="p" class="muted">Add exercises to a workout to track them here.</Card>
  <Card v-else as="section">
    <div class="row head">
      <h2>{{ selected.name }}</h2>
      <span class="muted">{{ current }}</span>
    </div>
    <p v-if="values.length === 0" class="muted empty">
      No logged sets for this exercise yet.
    </p>
    <p v-else-if="values.length === 1" class="muted empty">
      <strong class="first">{{ format(values[0]) }}</strong>
      Logged once. The chart starts with your second workout.
    </p>
    <template v-else>
      <LineChart :values="values" :format="format" />
      <dl>
        <div>
          <dt>Workouts</dt>
          <dd>{{ values.length }}</dd>
        </div>
        <div>
          <dt>Best</dt>
          <dd>{{ format(best) }}</dd>
        </div>
        <div>
          <dt>Gained</dt>
          <dd>{{ gained >= 0 ? '+' : '' }}{{ format(gained) }}</dd>
        </div>
      </dl>
    </template>
  </Card>
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

.first {
  display: block;
  margin-bottom: 8px;
  color: var(--foreground);
  font-size: 30px;
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
</style>
