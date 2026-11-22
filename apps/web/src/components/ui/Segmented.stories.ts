import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { ref } from 'vue'
import Segmented from './Segmented.vue'

const meta: Meta = { title: 'UI/Segmented', component: Segmented as Meta['component'] }

export default meta
type Story = StoryObj

const options = [
  { value: 'workout', label: 'Workout' },
  { value: 'warmup', label: 'Warmup' },
]

export const Default: Story = {
  render: () => ({
    components: { Segmented },
    setup: () => ({ value: ref('workout'), options }),
    template: '<Segmented v-model="value" :options="options" />',
  }),
}

export const Disabled: Story = {
  render: () => ({
    components: { Segmented },
    setup: () => ({ value: ref('workout'), options }),
    template: '<Segmented v-model="value" :options="options" disabled />',
  }),
}
