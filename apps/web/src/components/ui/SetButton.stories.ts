import type { Meta, StoryObj } from '@storybook/vue3-vite'
import SetButton from './SetButton.vue'

const meta = {
  title: 'UI/SetButton',
  component: SetButton,
  args: { state: 'empty' },
  argTypes: { state: { control: 'inline-radio', options: ['empty', 'done', 'missed'] } },
  render: (args) => ({
    components: { SetButton },
    setup: () => ({ args }),
    template: '<div style="width: 68px"><SetButton v-bind="args" aria-label="Set 1">5</SetButton></div>',
  }),
} satisfies Meta<typeof SetButton>

export default meta
type Story = StoryObj<typeof meta>

export const Empty: Story = {}
export const Done: Story = { args: { state: 'done' } }
export const Missed: Story = { args: { state: 'missed' } }
/** Rated with its reps in reserve: the badge in the corner. */
export const Rated: Story = { args: { state: 'done', badge: '4+' } }

/** Five sets mid-workout: three done, one with missed reps, one still open. */
export const Row: Story = {
  render: () => ({
    components: { SetButton },
    template: `
      <div style="display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px">
        <SetButton state="done" aria-label="Set 1: 5 reps">5</SetButton>
        <SetButton state="done" aria-label="Set 2: 5 reps">5</SetButton>
        <SetButton state="done" aria-label="Set 3: 5 reps">5</SetButton>
        <SetButton state="missed" aria-label="Set 4: 3 reps">3</SetButton>
        <SetButton aria-label="Set 5: not logged">5</SetButton>
      </div>`,
  }),
}
