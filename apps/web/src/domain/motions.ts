/**
 * Moving pictures of exercises: a figure doing one repetition, for the screens of a WOD.
 * Each is a strip of {@link MOTION_FRAMES} square frames side by side in `assets/motions`,
 * drawn on the dark colour {@link MOTION_BACKGROUND}.
 */
export const MOTION_FRAMES = 6
/** How long one frame shows. One repetition takes six of these. */
export const MOTION_FRAME_MS = 260
export const MOTION_BACKGROUND = '#0B0C0E'
/** The frame shown when the picture must not move: the middle of the movement. */
export const MOTION_STILL_FRAME = 3

/** Exercise id to the file name of its strip, without extension. Some exercises share one. */
export const MOTIONS: Record<string, string> = {
  burpee: 'burpee',
  airSquat: 'air-squat',
  pushups: 'push-up',
  pullups: 'pull-up',
  chinups: 'pull-up',
  situps: 'sit-up',
  run: 'run',
  sprint: 'run',
  thruster: 'thruster',
  kettlebellSwing: 'kettlebell-swing',
  cleanAndJerk: 'clean-and-jerk',
  snatch: 'snatch',
  hangPowerClean: 'hang-power-clean',
  pushJerk: 'push-jerk',
  deadlift: 'deadlift',
  doubleUnder: 'double-under',
  wallBall: 'wall-ball',
  handstandPushup: 'handstand-push-up',
  pistolSquat: 'pistol-squat',
  rowErg: 'row',
  bodyweightLunge: 'lunge',
  climber: 'climbers',
  jumpingJack: 'jumping-jack',
  jump: 'tuck-jump',
  standup: 'standup',
  boxJump: 'box-jump',
  toesToBar: 'toes-to-bar',
}

/** The strip of an exercise, or undefined when it has none. */
export function motionOf(exerciseId: string): string | undefined {
  return MOTIONS[exerciseId]
}
