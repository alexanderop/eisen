import type { Meta, StoryObj } from '@storybook/vue3-vite'
import IconButton from './IconButton.vue'

const meta = {
  title: 'UI/IconButton',
  component: IconButton,
  args: { label: 'Close', variant: 'secondary', tone: 'default', size: 'md', round: false },
  render: (args) => ({
    components: { IconButton },
    setup: () => ({ args }),
    template: '<IconButton v-bind="args">✕</IconButton>',
  }),
} satisfies Meta<typeof IconButton>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}

export const Variants: Story = {
  render: () => ({
    components: { IconButton },
    template: `
      <div style="display: flex; gap: 12px; align-items: center">
        <IconButton label="Move up">↑</IconButton>
        <IconButton label="Move down" disabled>↓</IconButton>
        <IconButton label="Remove" tone="danger">✕</IconButton>
        <IconButton label="Close" tone="muted" round>✕</IconButton>
        <IconButton label="Add" size="lg">+</IconButton>
      </div>`,
  }),
}
