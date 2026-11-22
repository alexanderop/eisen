import type { Meta, StoryObj } from '@storybook/vue3-vite'
import BarbellPlates from './BarbellPlates.vue'

const meta = {
  title: 'Workout/BarbellPlates',
  component: BarbellPlates,
  args: { plates: [20, 10], largest: 20 },
} satisfies Meta<typeof BarbellPlates>

export default meta
type Story = StoryObj<typeof meta>

/** One side of the bar for 80kg: a 20 and a 10. */
export const Default: Story = {}
export const EmptyBar: Story = { args: { plates: [] } }
export const Heavy: Story = { args: { plates: [20, 20, 20, 20, 15, 2.5, 1.25] } }
