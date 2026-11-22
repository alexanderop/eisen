import type { Meta, StoryObj } from '@storybook/vue3-vite'
import { Badge } from './badge'

const meta: Meta<typeof Badge> = {
  title: 'UI/Badge',
  component: Badge,
  args: { variant: 'default' },
  render: (args) => ({
    components: { Badge },
    setup: () => ({ args }),
    template: '<Badge v-bind="args">Next</Badge>',
  }),
}

export default meta
type Story = StoryObj

export const Default: Story = {}
export const Secondary: Story = { args: { variant: 'secondary' } }
