import type { Meta, StoryObj } from '@storybook/vue3-vite'
import Badge from './Badge.vue'
import Card from './Card.vue'

const meta = { title: 'UI/Card', component: Card } satisfies Meta<typeof Card>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {
  render: () => ({
    components: { Card },
    template: `
      <Card as="section">
        <p class="eyebrow">Next up</p>
        <h2 style="font-size: 24px; font-weight: 800">Workout A</h2>
        <p class="muted">Hack Squat · Bench Press · Barbell Row · Dips</p>
      </Card>`,
  }),
}

/** The whole card is the tap target, for list entries that open a detail screen. */
export const Tappable: Story = {
  render: () => ({
    components: { Card, Badge },
    template: `
      <Card as="button">
        <span class="row"><strong style="font-size: 18px; flex: 1">Pull Day</strong><Badge>Next</Badge></span>
        <span class="muted" style="font-size: 13px">Lat Pulldown · Chin-ups · Face Pull</span>
      </Card>`,
  }),
}
