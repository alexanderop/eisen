import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { ref } from 'vue'
import Stepper from './Stepper.vue'

const meta: Meta<typeof Stepper> = { title: 'UI/Stepper', component: Stepper }

export default meta
type Story = StoryObj

export const Weight: Story = {
  render: () => ({
    components: { Stepper },
    setup: () => ({ value: ref(80), format: (kg: number) => `${kg}kg` }),
    template: '<Stepper v-model="value" :step="2.5" :min="2.5" label="weight" :format="format" />',
  }),
}

/** With `min` and `max` the buttons switch off at the ends. */
export const Bounded: Story = {
  render: () => ({
    components: { Stepper },
    setup: () => ({ value: ref(10) }),
    template: '<Stepper v-model="value" :step="1" :min="1" :max="10" label="sets" />',
  }),
}

export const InARow: Story = {
  render: () => ({
    components: { Stepper },
    setup: () => ({ value: ref(5) }),
    template:
      '<div class="row"><span class="muted">Reps</span><Stepper v-model="value" :step="1" :min="1" label="reps" /></div>',
  }),
}
