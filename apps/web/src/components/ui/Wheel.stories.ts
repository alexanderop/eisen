import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { ref } from 'vue'
import { repsWheel, weightWheel } from '../../domain/rest'
import Wheel from './Wheel.vue'

const meta: Meta<typeof Wheel> = { title: 'UI/Wheel', component: Wheel }

export default meta
type Story = StoryObj

export const WeightAndReps: Story = {
  render: () => ({
    components: { Wheel },
    setup: () => ({
      weight: ref(80),
      reps: ref(5),
      weights: weightWheel('barbell', 'kg', 20, 80),
      counts: repsWheel(5, 5),
    }),
    template: `
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; max-width: 360px">
        <Wheel v-model="weight" :values="weights" header="kg" label="Weight" />
        <Wheel v-model="reps" :values="counts" header="reps" label="Reps" />
      </div>`,
  }),
}

/** No added weight reads "BW". */
export const AddedWeight: Story = {
  render: () => ({
    components: { Wheel },
    setup: () => ({
      weight: ref(0),
      weights: weightWheel('bodyweight', 'kg', 20, 0),
      format: (kg: number) => (kg === 0 ? 'BW' : String(kg)),
    }),
    template:
      '<div style="max-width: 170px"><Wheel v-model="weight" :values="weights" header="+kg" label="Added weight" :format="format" /></div>',
  }),
}
