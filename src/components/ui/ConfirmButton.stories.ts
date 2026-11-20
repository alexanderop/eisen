import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { ref } from 'vue'
import ConfirmButton from './ConfirmButton.vue'

const meta: Meta<typeof ConfirmButton> = {
  title: 'UI/ConfirmButton',
  component: ConfirmButton,
}

export default meta
type Story = StoryObj

/** Tap once to arm it, a second time to confirm. It disarms after four seconds. */
export const Default: Story = {
  render: () => ({
    components: { ConfirmButton },
    setup: () => ({ count: ref(0) }),
    template: `
      <ConfirmButton class="w-full" label="Erase all data" confirm-label="Tap again to erase everything" @confirm="count++" />
      <p class="muted" style="margin-top: 12px; font-size: 13px">Confirmed {{ count }} times</p>`,
  }),
}

export const Inline: Story = {
  render: () => ({
    components: { ConfirmButton },
    template: '<ConfirmButton variant="ghost" size="md" label="Delete" confirm-label="Confirm" />',
  }),
}
