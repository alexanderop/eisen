import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { Timer, Trash2, Vibrate } from '@lucide/vue'
import ListRow from './ListRow.vue'

const meta = {
  title: 'UI/ListRow',
  component: ListRow,
  args: { icon: Timer, label: 'After a full set', value: '1:30' },
} satisfies Meta<typeof ListRow>

export default meta
type Story = StoryObj<typeof meta>

/** Opens something: the value says what the setting is now. */
export const WithValue: Story = {}
export const Switch: Story = {
  args: { icon: Vibrate, label: 'Vibrate on taps', value: undefined, trailing: 'switch', checked: true },
}
export const Destructive: Story = {
  args: {
    icon: Trash2,
    label: 'Erase all data',
    description: 'Workouts, weights and settings',
    value: undefined,
    tone: 'destructive',
    trailing: 'none',
  },
}
