import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { Button } from './button'

/** The shadcn-vue button with sizes for thumbs. Use one primary (`default`) button per screen. */
const meta: Meta<typeof Button> = {
  title: 'UI/Button',
  component: Button,
  args: { variant: 'default', size: 'default' },
  argTypes: {
    variant: {
      control: 'inline-radio',
      options: ['default', 'secondary', 'ghost', 'destructive', 'outline', 'link'],
    },
    size: { control: 'inline-radio', options: ['default', 'md', 'sm', 'icon', 'icon-lg'] },
  },
  render: (args) => ({
    components: { Button },
    setup: () => ({ args }),
    template: '<Button v-bind="args" class="w-full">Start workout</Button>',
  }),
}

export default meta
type Story = StoryObj

/** The main action of a screen. Use it once. */
export const Primary: Story = {}

export const Secondary: Story = { args: { variant: 'secondary' } }

/** Text only, for actions in a header or next to a title. */
export const Ghost: Story = {
  render: () => ({
    components: { Button },
    template: '<Button variant="ghost" size="md" class="px-1">Finish</Button>',
  }),
}

export const Destructive: Story = { args: { variant: 'destructive' } }

export const Disabled: Story = {
  render: () => ({
    components: { Button },
    template: '<Button class="w-full" disabled>Start workout</Button>',
  }),
}

/** A square button that shows only a symbol. It needs an `aria-label`. */
export const Icon: Story = {
  render: () => ({
    components: { Button },
    template: `
      <div class="flex items-center gap-3">
        <Button size="icon" variant="secondary" aria-label="Close">✕</Button>
        <Button size="icon" variant="secondary" class="rounded-full" aria-label="Close">✕</Button>
        <Button size="icon-lg" variant="secondary" aria-label="Increase weight">+</Button>
      </div>`,
  }),
}

export const AllVariants: Story = {
  render: () => ({
    components: { Button },
    template: `
      <div class="grid gap-3">
        <Button class="w-full">Primary</Button>
        <Button variant="secondary" class="w-full">Secondary</Button>
        <Button variant="destructive" class="w-full">Destructive</Button>
        <div class="flex items-center gap-4">
          <Button variant="ghost" size="md" class="px-1">Ghost</Button>
          <Button variant="secondary" size="md">Medium</Button>
          <Button variant="secondary" size="sm">Small</Button>
        </div>
      </div>`,
  }),
}
