import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { ref } from 'vue'
import ChipGroup from './ChipGroup.vue'

const meta: Meta = { title: 'UI/ChipGroup', component: ChipGroup as Meta['component'] }

export default meta
type Story = StoryObj

export const Default: Story = {
  render: () => ({
    components: { ChipGroup },
    setup: () => ({
      value: ref('a'),
      options: [
        { value: 'a', label: 'Workout A' },
        { value: 'b', label: 'Workout B' },
        { value: 'pull', label: 'Pull Day' },
      ],
    }),
    template: '<ChipGroup v-model="value" :options="options" label="Next workout" />',
  }),
}

/** More options than fit: the row scrolls sideways and runs to the screen edge. */
export const Overflowing: Story = {
  render: () => ({
    components: { ChipGroup },
    setup: () => ({
      value: ref('bench'),
      options: ['Hack Squat', 'Bench Press', 'Barbell Row', 'Overhead Press', 'Deadlift', 'Dips'].map(
        (label) => ({ value: label.split(' ')[0].toLowerCase(), label }),
      ),
    }),
    template: '<ChipGroup v-model="value" :options="options" label="Exercise" bleed />',
  }),
}
