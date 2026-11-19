import type { Meta, StoryObj } from '@storybook/vue3-vite'
import BarbellPlates from './BarbellPlates.vue'

const meta = {
  title: 'Workout/BarbellPlates',
  component: BarbellPlates,
  args: { plates: [25, 5], largest: 25 },
} satisfies Meta<typeof BarbellPlates>

export default meta
type Story = StoryObj<typeof meta>

/** One side of the bar for 80kg: a 25 and a 5. */
export const Default: Story = {}
export const EmptyBar: Story = { args: { plates: [] } }
export const Heavy: Story = { args: { plates: [25, 25, 25, 20, 2.5, 1.25] } }
