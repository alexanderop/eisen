import type { Meta, StoryObj } from '@storybook/vue3-vite'
import ExerciseImage from './ExerciseImage.vue'

const meta = {
  title: 'Workout/ExerciseImage',
  component: ExerciseImage,
  args: { image: 'hack-squat', name: 'Hack Squat', variant: 'tile', size: 44 },
  argTypes: { variant: { control: 'inline-radio', options: ['tile', 'banner'] } },
} satisfies Meta<typeof ExerciseImage>

export default meta
type Story = StoryObj<typeof meta>

export const Tile: Story = {}
export const LargeTile: Story = { args: { size: 104 } }

/** The lifter's own exercises have no picture. */
export const TileWithoutImage: Story = { args: { image: undefined, name: 'Cable Lateral' } }

export const Banner: Story = { args: { variant: 'banner' } }
