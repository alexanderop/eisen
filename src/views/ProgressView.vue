<script setup lang="ts">
import { computed, ref } from 'vue'
import LineChart from '../components/LineChart.vue'
import {
  EXERCISE_IDS,
  EXERCISE_NAMES,
  isBodyweight,
  totalReps,
  type ExerciseId,
} from '../domain/program'
import { convert } from '../domain/units'
import { fmt } from '../format'
import { state } from '../store'

const selected = ref<ExerciseId>('hackSquat')

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
        .filter((log) => log.id === selected.value && log.sets.some((reps) => reps !== null))
        .map((log) =>
          isBodyweight(log.id)
            ? totalReps(log)
            : convert(log.weight, workout.unit, state.settings.unit),
        ),
    ),
)

const current = computed(() => {
  const id = selected.value
  return isBodyweight(id) ? 'Total reps per workout' : `Now ${fmt(state.progress[id].weight)}`
})

function format(value: number): string {
  return isBodyweight(selected.value) ? `${value} reps` : fmt(value)
}

const best = computed(() => Math.max(...values.value))
const gained = computed(() => values.value[values.value.length - 1] - values.value[0])
</script>

<template>
  <h1 class="page-title">Progress</h1>

  <div class="chips">
    <button
      v-for="id in EXERCISE_IDS"
      :key="id"
      :aria-pressed="selected === id"
      @click="selected = id"
    >
      {{ EXERCISE_NAMES[id] }}
    </button>
  </div>

  <section class="card">
    <div class="row head">
      <h2>{{ EXERCISE_NAMES[selected] }}</h2>
      <span class="muted">{{ current }}</span>
    </div>
    <p v-if="values.length === 0" class="muted empty">
      No logged sets for this exercise yet.
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
  </section>
</template>

<style scoped>
.chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  margin: 0 -16px;
  padding: 0 16px 4px;
  scrollbar-width: none;
}

.chips button {
  flex: none;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  color: var(--muted);
  font-weight: 600;
  font-size: 14px;
}

.chips button[aria-pressed='true'] {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}

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

dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

dt {
  font-size: 12px;
  color: var(--muted);
}

dd {
  margin: 0;
  font-weight: 700;
  font-size: 17px;
}
</style>
