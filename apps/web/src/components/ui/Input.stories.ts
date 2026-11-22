import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { ref } from 'vue'
import Field from './Field.vue'
import { Input } from './input'

const meta: Meta<typeof Input> = { title: 'UI/Input', component: Input }

export default meta
type Story = StoryObj

export const Default: Story = {
  render: () => ({
    components: { Input },
    setup: () => ({ value: ref('Pull Day') }),
    template: '<Input v-model="value" />',
  }),
}

export const Search: Story = {
  render: () => ({
    components: { Input },
    setup: () => ({ value: ref('') }),
    template:
      '<Input v-model="value" type="search" placeholder="Search exercises" aria-label="Search exercises" />',
  }),
}

/** Field puts a label above the control and ties the two together. */
export const WithField: Story = {
  render: () => ({
    components: { Input, Field },
    setup: () => ({ value: ref('Workout A') }),
    template: '<Field label="Name"><Input v-model="value" /></Field>',
  }),
}
