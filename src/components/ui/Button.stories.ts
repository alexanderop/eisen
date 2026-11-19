import type { Meta, StoryObj } from '@storybook/vue3-vite'
import Button from './Button.vue'

const meta = {
  title: 'UI/Button',
  component: Button,
  args: { variant: 'secondary', tone: 'default', size: 'lg', block: false },
  argTypes: {
    variant: { control: 'inline-radio', options: ['primary', 'secondary', 'ghost'] },
    tone: { control: 'inline-radio', options: ['default', 'danger'] },
    size: { control: 'inline-radio', options: ['sm', 'md', 'lg'] },
  },
  render: (args) => ({
    components: { Button },
    setup: () => ({ args }),
    template: '<Button v-bind="args">Start workout</Button>',
  }),
} satisfies Meta<typeof Button>

export default meta
type Story = StoryObj<typeof meta>

/** The main action of a screen. Use it once. */
export const Primary: Story = { args: { variant: 'primary', block: true } }

export const Secondary: Story = { args: { block: true } }

/** Text only, for actions in a header or next to a title. */
export const Ghost: Story = { args: { variant: 'ghost', size: 'md' } }

export const Danger: Story = { args: { tone: 'danger', block: true } }

export const Disabled: Story = {
  render: () => ({
    components: { Button },
    template: '<Button variant="primary" block disabled>Start workout</Button>',
  }),
}

export const AllVariants: Story = {
  render: () => ({
    components: { Button },
    template: `
      <div style="display: grid; gap: 12px">
        <Button variant="primary" block>Primary</Button>
        <Button block>Secondary</Button>
        <Button tone="danger" block>Secondary danger</Button>
        <div style="display: flex; gap: 16px; align-items: center">
          <Button variant="ghost" size="md">Ghost</Button>
          <Button variant="ghost" tone="danger" size="md">Ghost danger</Button>
          <Button size="md">Medium</Button>
          <Button size="sm">Small</Button>
        </div>
      </div>`,
  }),
}
