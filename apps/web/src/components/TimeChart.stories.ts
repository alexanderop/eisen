import type { Meta, StoryObj } from '@storybook/vue3-vite'
import TimeChart, { type ChartPoint } from './TimeChart.vue'

const DAY = 24 * 60 * 60 * 1000
const START = new Date(2026, 8, 1).getTime()

function points(values: number[], flags: Record<number, Partial<ChartPoint>> = {}): ChartPoint[] {
  return values.map((value, index) => ({ time: START + index * 3 * DAY, value, ...flags[index] }))
}

const formatTime = (time: number) =>
  new Intl.DateTimeFormat('en', { day: 'numeric', month: 'short' }).format(time)

const meta = {
  title: 'Workout/TimeChart',
  component: TimeChart,
  args: { format: (kg: number) => `${kg}kg`, formatTime, label: 'Weight over time', series: [] },
} satisfies Meta<typeof TimeChart>

export default meta
type Story = StoryObj<typeof meta>

/** Steady progress, three misses, a deload and the climb back to a new record. */
export const OneExercise: Story = {
  args: {
    events: true,
    series: [
      {
        id: 'bench',
        name: 'Bench Press',
        color: 'var(--primary)',
        points: points([60, 62.5, 65, 67.5, 67.5, 67.5, 60, 62.5, 65, 67.5, 70], {
          1: { record: true },
          2: { record: true },
          3: { missed: true },
          4: { missed: true },
          5: { missed: true },
          6: { deload: true },
          9: { record: true },
          10: { record: true },
        }),
      },
    ],
  },
}

export const AllLifts: Story = {
  args: {
    series: [
      { id: 'squat', name: 'Hack Squat', color: '#3987e5', points: points([80, 85, 90, 95, 100, 105]) },
      { id: 'bench', name: 'Bench Press', color: '#d95926', points: points([60, 62.5, 65, 65, 67.5, 70]) },
      { id: 'row', name: 'Barbell Row', color: '#199e70', points: points([50, 52.5, 55, 57.5, 60, 60]) },
      { id: 'ohp', name: 'Overhead Press', color: '#c98500', points: points([35, 37.5, 40, 40, 40, 36]) },
      { id: 'deadlift', name: 'Deadlift', color: '#d55181', points: points([100, 105, 110, 115, 120, 125]) },
    ],
  },
}

export const SingleWorkout: Story = {
  args: { events: true, series: [{ id: 'bench', name: 'Bench Press', color: 'var(--primary)', points: points([80]) }] },
}
