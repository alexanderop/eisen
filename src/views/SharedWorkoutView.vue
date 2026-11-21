<script setup lang="ts">
/** A workout that arrived in a link. It is only shown: nothing of it is saved on this device. */
import { computed } from 'vue'
import { Badge, Button, Card } from '../components/ui'
import { sharedStats, type SharedWorkout } from '../domain/share'
import { fmt, formatDate } from '../format'

const props = defineProps<{ shared: SharedWorkout }>()
const emit = defineEmits<{ close: [] }>()

const stats = computed(() => sharedStats(props.shared))
</script>

<template>
  <main class="page shared">
    <header>
      <p class="eyebrow">Shared workout</p>
      <h1 class="page-title">{{ shared.name }}</h1>
      <p class="muted">
        {{ formatDate(shared.startedAt) }}
        <template v-if="shared.minutes !== null"> · {{ shared.minutes }} min</template>
      </p>
    </header>

    <section class="stats">
      <Card v-if="stats.volume > 0">
        <strong>{{ Math.round(stats.volume).toLocaleString() }} {{ shared.unit }}</strong>
        <span class="muted">volume</span>
      </Card>
      <Card>
        <strong>{{ stats.sets }}</strong>
        <span class="muted">sets</span>
      </Card>
      <Card>
        <strong>{{ stats.reps }}</strong>
        <span class="muted">reps</span>
      </Card>
    </section>

    <Card as="section">
      <ul>
        <li v-for="(exercise, index) in shared.exercises" :key="index">
          <span class="name">
            {{ exercise.name }}
            <Badge v-if="exercise.record">Record</Badge>
          </span>
          <strong>{{ exercise.bodyweight ? 'BW' : fmt(exercise.weight, shared.unit) }}</strong>
          <span class="reps">
            <span
              v-for="(reps, set) in exercise.sets"
              :key="set"
              :class="{
                missed: reps === null || (exercise.targetReps !== null && reps !== exercise.targetReps),
              }"
              >{{ reps ?? '–' }}</span
            >
          </span>
        </li>
      </ul>
    </Card>

    <Button class="w-full" @click="emit('close')">Open Eisen</Button>
    <p class="muted hint">Eisen is a free workout tracker that keeps your training on your device.</p>
  </main>
</template>

<style scoped>
.shared {
  padding-bottom: calc(env(safe-area-inset-bottom) + 24px);
}

header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  overflow-wrap: anywhere;
}

.stats {
  display: flex;
  gap: 10px;
}

.stats > * {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 12px;
}

.stats strong {
  font-size: 17px;
}

.stats .muted {
  font-size: 12px;
}

ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

li {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  align-items: baseline;
  padding: 10px 0;
  border-top: 1px solid var(--border);
  font-size: 15px;
}

li:first-child {
  border-top: 0;
  padding-top: 0;
}

li:last-child {
  padding-bottom: 0;
}

.name {
  overflow-wrap: anywhere;
}

.reps {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--primary);
  font-weight: 600;
}

.reps .missed {
  color: var(--warn);
}

.hint {
  font-size: 13px;
  text-align: center;
}
</style>
