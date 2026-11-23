<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CalendarDay } from '../domain/chart'
import { durationMinutes } from '../domain/share'
import { sessionSummary } from '../domain/timer'
import { formatDate } from '../format'
import { haptic } from '../haptics'

/** One square per day, a column per week, Monday on top. Days with a workout or a timer are lit. */
const props = defineProps<{ weeks: CalendarDay[][] }>()

const monthFormat = new Intl.DateTimeFormat(undefined, { month: 'short' })
const weekdayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'narrow' })

/** A month name over the first column of every month. */
const months = computed(() =>
  props.weeks.map((week, index) => {
    const month = new Date(week[0].time).getMonth()
    const before = index === 0 ? -1 : new Date(props.weeks[index - 1][0].time).getMonth()
    return month === before ? '' : monthFormat.format(week[0].time)
  }),
)

const weekdays = computed(() => props.weeks[0].map((day) => weekdayFormat.format(day.time)))

const picked = ref<number | null>(null)
const selected = computed(
  () => props.weeks.flat().find((day) => day.time === picked.value && trained(day)) ?? null,
)

function trained(day: CalendarDay): boolean {
  return day.workouts.length > 0 || day.timers.length > 0
}

function pick(day: CalendarDay) {
  picked.value = day.time === picked.value ? null : day.time
  haptic('tap')
}

/** One line per workout or timer of the day. */
function entries(day: CalendarDay): string[] {
  return [
    ...day.workouts.map((workout) => {
      const minutes = durationMinutes(workout)
      return minutes === null ? workout.name : `${workout.name}, ${minutes} min`
    }),
    ...day.timers.map((session) => `${session.name}, ${sessionSummary(session)}`),
  ]
}
</script>

<template>
  <div class="calendar" :style="{ '--weeks': weeks.length }">
    <div class="months" aria-hidden="true">
      <span v-for="(month, index) in months" :key="index">{{ month }}</span>
    </div>
    <div class="body">
      <div class="weekdays" aria-hidden="true">
        <span v-for="(weekday, index) in weekdays" :key="index">{{ index % 2 === 0 ? weekday : '' }}</span>
      </div>
      <div class="grid">
        <template v-for="week in weeks" :key="week[0].time">
          <template v-for="day in week" :key="day.time">
            <button
              v-if="trained(day)"
              class="day trained"
              :class="{ today: day.today, picked: day.time === picked }"
              :aria-label="`${formatDate(day.time)}: ${entries(day).join('; ')}`"
              :aria-pressed="day.time === picked"
              @click="pick(day)"
            />
            <span v-else class="day" :class="{ today: day.today, future: day.future }" />
          </template>
        </template>
      </div>
    </div>
    <p class="readout" role="status">
      <template v-if="selected">
        <strong>{{ formatDate(selected.time) }}</strong>
        <span v-for="(entry, index) in entries(selected)" :key="index" class="muted entry">{{ entry }}</span>
      </template>
      <span v-else class="muted">Tap a lit day to see the workout.</span>
    </p>
  </div>
</template>

<style scoped>
.calendar {
  --label: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.months,
.grid {
  display: grid;
  gap: 4px;
}

.months {
  grid-template-columns: repeat(var(--weeks), 1fr);
  margin-left: calc(var(--label) + 6px);
  font-size: 11px;
  color: var(--muted-foreground);
  white-space: nowrap;
}

/* A month name is wider than a column: it runs over the empty ones after it. */
.months span {
  overflow: visible;
  width: 0;
}

.body {
  display: flex;
  gap: 6px;
}

.weekdays {
  width: var(--label);
  display: grid;
  grid-template-rows: repeat(7, 1fr);
  gap: 4px;
  align-items: center;
  font-size: 11px;
  color: var(--muted-foreground);
}

.grid {
  flex: 1;
  grid-auto-flow: column;
  grid-template-rows: repeat(7, 1fr);
  grid-template-columns: repeat(var(--weeks), 1fr);
}

.day {
  aspect-ratio: 1;
  border-radius: 5px;
  background: var(--secondary);
}

.day.future {
  background: transparent;
  box-shadow: inset 0 0 0 1px var(--secondary);
}

.day.trained {
  background: var(--primary);
  transition: transform 0.08s ease;
}

.day.trained:active {
  transform: scale(0.88);
}

.day.today {
  box-shadow: 0 0 0 2px var(--card), 0 0 0 3.5px var(--foreground);
}

.day.picked {
  box-shadow: 0 0 0 2px var(--card), 0 0 0 4px var(--primary);
}

.readout {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 20px;
  margin-top: 6px;
  font-size: 13px;
}
</style>
