import type { Workout } from './program'

/**
 * Round axis values that cover low to high, about `target` of them.
 * The first is at or below low and the last at or above high, so no mark touches the frame.
 */
export function niceTicks(low: number, high: number, target = 3): number[] {
  const rough = (high - low || Math.abs(high) || 1) / target
  const power = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 2.5, 5, 10].map((factor) => factor * power).find((size) => size >= rough)!
  let first = Math.floor(low / step) * step
  let last = Math.ceil(high / step) * step
  if (first === last) {
    first -= step
    last += step
  }
  const count = Math.round((last - first) / step)
  return Array.from({ length: count + 1 }, (_, index) => Number((first + index * step).toFixed(6)))
}

function startOfDay(time: number): Date {
  const date = new Date(time)
  date.setHours(0, 0, 0, 0)
  return date
}

/** Monday 00:00 of the week the moment is in, in local time. */
export function weekStart(time: number): number {
  const date = startOfDay(time)
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7))
  return date.getTime()
}

function addDays(time: number, days: number): number {
  const date = new Date(time)
  date.setDate(date.getDate() + days)
  return date.getTime()
}

export interface CalendarDay {
  /** Local midnight. */
  time: number
  today: boolean
  future: boolean
  workouts: Workout[]
}

/** The last `weeks` weeks as columns of seven days, Monday first, ending with the current week. */
export function calendarWeeks(history: Workout[], now: number, weeks: number): CalendarDay[][] {
  const byDay = new Map<number, Workout[]>()
  for (const workout of history) {
    const day = startOfDay(workout.startedAt).getTime()
    byDay.set(day, [...(byDay.get(day) ?? []), workout])
  }
  const today = startOfDay(now).getTime()
  const first = addDays(weekStart(now), -7 * (weeks - 1))
  return Array.from({ length: weeks }, (_, week) =>
    Array.from({ length: 7 }, (_, day) => {
      const time = addDays(first, week * 7 + day)
      return { time, today: time === today, future: time > today, workouts: byDay.get(time) ?? [] }
    }),
  )
}

/**
 * Weeks in a row with at least one workout, counted back from now.
 * A current week without a workout yet does not break the streak: it is not over.
 */
export function weekStreak(times: number[], now: number): number {
  const trained = new Set(times.map(weekStart))
  let week = weekStart(now)
  if (!trained.has(week)) week = addDays(week, -7)
  let streak = 0
  while (trained.has(week)) {
    streak++
    week = addDays(week, -7)
  }
  return streak
}
