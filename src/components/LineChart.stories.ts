import type { Meta, StoryObj } from '@storybook/vue3-vite'
import LineChart from './LineChart.vue'

const meta = {
  title: 'Workout/LineChart',
  component: LineChart,
  args: { values: [60, 62.5, 65, 67.5, 67.5, 67.5, 60, 62.5, 65, 67.5, 70], format: (kg) => `${kg}kg` },
} satisfies Meta<typeof LineChart>

export default meta
type Story = StoryObj<typeof meta>

/** Steady progress, three misses, a deload and the climb back. */
export const Default: Story = {}
export const SingleWorkout: Story = { args: { values: [80] } }
