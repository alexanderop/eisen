import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { ref } from 'vue'
import { Button } from './button'
import Sheet from './Sheet.vue'
import Stepper from './Stepper.vue'

const meta: Meta<typeof Sheet> = {
  title: 'UI/Sheet',
  component: Sheet,
  // A sheet covers the screen, so each story gets its own frame on the docs page.
  parameters: { docs: { story: { inline: false, height: '420px' } } },
}

export default meta
type Story = StoryObj

export const Default: Story = {
  render: () => ({
    components: { Sheet, Button, Stepper },
    setup: () => ({ open: ref(true), weight: ref(80), format: (kg: number) => `${kg}kg` }),
    template: `
      <Button class="w-full" @click="open = true">Open sheet</Button>
      <Sheet v-if="open" title="Bench Press" @close="open = false">
        <div class="row">
          <span class="muted">Weight</span>
          <Stepper v-model="weight" :step="2.5" :min="2.5" label="weight" :format="format" />
        </div>
        <Button class="w-full" @click="open = false">Done</Button>
      </Sheet>`,
  }),
}

export const Confirmation: Story = {
  render: () => ({
    components: { Sheet, Button },
    setup: () => ({ open: ref(true) }),
    template: `
      <Button variant="secondary" class="w-full" @click="open = true">Open sheet</Button>
      <Sheet v-if="open" title="Finish early?" @close="open = false">
        <p class="muted">Some sets are not logged. Exercises you did not start are skipped.</p>
        <Button class="w-full" @click="open = false">Finish workout</Button>
        <Button variant="secondary" class="w-full" @click="open = false">Keep going</Button>
      </Sheet>`,
  }),
}
