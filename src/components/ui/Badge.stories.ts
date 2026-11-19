import type { Meta, StoryObj } from '@storybook/vue3-vite'
import Badge from './Badge.vue'

const meta = {
  title: 'UI/Badge',
  component: Badge,
  args: { variant: 'accent' },
  render: (args) => ({
    components: { Badge },
    setup: () => ({ args }),
    template: '<Badge v-bind="args">Next</Badge>',
  }),
} satisfies Meta<typeof Badge>

export default meta
type Story = StoryObj<typeof meta>

export const Accent: Story = {}
export const Neutral: Story = { args: { variant: 'neutral' } }
