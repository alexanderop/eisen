/**
 * barbell: the bar counts toward the weight, plates go on both sides.
 * machine: plate-loaded, no bar. The weight is only the plates.
 * weight: dumbbells, cables, weight stacks and eGym machines. A number, no plates to work out.
 * bodyweight: no load, only reps.
 */
export type Equipment = 'barbell' | 'machine' | 'weight' | 'bodyweight'

export const EQUIPMENT_LABELS: Record<Equipment, string> = {
  barbell: 'Barbell',
  machine: 'Plate machine',
  weight: 'Machine / dumbbell',
  bodyweight: 'Bodyweight',
}

export const EQUIPMENT: Equipment[] = ['barbell', 'machine', 'weight', 'bodyweight']

export const GROUPS = ['Legs', 'Chest', 'Back', 'Shoulders', 'Arms', 'Core'] as const
export type Group = (typeof GROUPS)[number] | 'Custom'

export interface Exercise {
  id: string
  name: string
  group: Group
  equipment: Equipment
  /** Starting weight in kg. 0 for bodyweight. */
  start: number
  /** Weight in kg that a fully completed workout adds. */
  increment: number
  /** File name in `src/assets/exercises`, without extension. The lifter's own exercises have none. */
  image?: string
}

type Row = [
  id: string,
  name: string,
  equipment: Equipment,
  start: number,
  increment: number,
  image: string,
]

const TABLE: Record<(typeof GROUPS)[number], Row[]> = {
  Legs: [
    ['hackSquat', 'Hack Squat', 'machine', 80, 5, 'hack-squat'],
    ['squat', 'Squat', 'barbell', 60, 5, 'squat'],
    ['frontSquat', 'Front Squat', 'barbell', 40, 2.5, 'squat'],
    ['legPress', 'Leg Press', 'machine', 100, 5, 'leg-press'],
    ['romanianDeadlift', 'Romanian Deadlift', 'barbell', 60, 5, 'deadlift'],
    ['hipThrust', 'Hip Thrust', 'barbell', 60, 5, 'hip-thrust'],
    ['lunge', 'Lunge', 'weight', 10, 1, 'walking-lunge'],
    ['splitSquat', 'Bulgarian Split Squat', 'weight', 10, 1, 'bulgarian-split-squat'],
    ['gobletSquat', 'Goblet Squat', 'weight', 16, 2, 'goblet-squat'],
    ['legExtension', 'Leg Extension', 'weight', 30, 2.5, 'leg-extension'],
    ['legCurl', 'Leg Curl', 'weight', 25, 2.5, 'seated-leg-curl'],
    ['calfRaise', 'Calf Raise', 'weight', 40, 2.5, 'standing-calf-raise'],
    ['egymSquat', 'eGym Squat', 'weight', 60, 2, 'egym-squat'],
  ],
  Chest: [
    ['bench', 'Bench Press', 'barbell', 80, 2.5, 'bench-press'],
    ['inclineBench', 'Incline Bench Press', 'barbell', 50, 2.5, 'incline-bench-press'],
    ['closeGripBench', 'Close-Grip Bench Press', 'barbell', 50, 2.5, 'bench-press'],
    ['dumbbellBench', 'Dumbbell Bench Press', 'weight', 20, 2, 'dumbbell-bench-press'],
    ['inclineDumbbellPress', 'Incline Dumbbell Press', 'weight', 16, 2, 'incline-dumbbell-press'],
    ['chestFly', 'Chest Fly', 'weight', 10, 1, 'dumbbell-fly'],
    ['egymChestPress', 'eGym Chest Press', 'weight', 40, 2, 'egym-chest-press'],
    ['dips', 'Dips', 'bodyweight', 0, 0, 'chest-dip'],
    ['pushups', 'Push-ups', 'bodyweight', 0, 0, 'push-up'],
  ],
  Back: [
    ['deadlift', 'Deadlift', 'barbell', 100, 5, 'deadlift'],
    ['row', 'Barbell Row', 'barbell', 60, 2.5, 'barbell-row'],
    ['tBarRow', 'T-Bar Row', 'machine', 30, 2.5, 't-bar-row'],
    ['dumbbellRow', 'Dumbbell Row', 'weight', 20, 2, 'row'],
    ['latPulldown', 'Lat Pulldown', 'weight', 40, 2.5, 'lat-pulldown'],
    ['cableRow', 'Seated Cable Row', 'weight', 40, 2.5, 'seated-row'],
    ['facePull', 'Face Pull', 'weight', 15, 2.5, 'face-pull'],
    ['egymLatPulldown', 'eGym Lat Pulldown', 'weight', 40, 2, 'egym-lat-pulldown'],
    ['egymSeatedRow', 'eGym Seated Row', 'weight', 40, 2, 'egym-seated-row'],
    ['pullups', 'Pull-ups', 'bodyweight', 0, 0, 'pull-up'],
    ['chinups', 'Chin-ups', 'bodyweight', 0, 0, 'chin-up'],
    ['backExtension', 'Back Extension', 'bodyweight', 0, 0, 'back-extension'],
  ],
  Shoulders: [
    ['ohp', 'Overhead Press', 'barbell', 40, 2.5, 'overhead-press'],
    ['dumbbellShoulderPress', 'Dumbbell Shoulder Press', 'weight', 14, 2, 'shoulder-press'],
    ['lateralRaise', 'Lateral Raise', 'weight', 6, 1, 'lateral-raise'],
    ['rearDeltFly', 'Rear Delt Fly', 'weight', 6, 1, 'reverse-fly'],
    ['egymShoulderPress', 'eGym Shoulder Press', 'weight', 25, 2, 'egym-shoulder-press'],
    ['shrug', 'Shrug', 'barbell', 60, 5, 'dumbbell-shrug'],
  ],
  Arms: [
    ['barbellCurl', 'Barbell Curl', 'barbell', 25, 2.5, 'barbell-curl'],
    ['dumbbellCurl', 'Dumbbell Curl', 'weight', 10, 1, 'biceps-curl'],
    ['hammerCurl', 'Hammer Curl', 'weight', 10, 1, 'hammer-curl'],
    ['skullCrusher', 'Skull Crusher', 'weight', 20, 2.5, 'skull-crusher'],
    ['tricepsPushdown', 'Triceps Pushdown', 'weight', 20, 2.5, 'rope-triceps-pushdown'],
    ['overheadTricepsExtension', 'Overhead Triceps Extension', 'weight', 12, 1, 'overhead-triceps-extension'],
  ],
  Core: [
    ['hangingLegRaise', 'Hanging Leg Raise', 'bodyweight', 0, 0, 'hanging-leg-raise'],
    ['situps', 'Sit-ups', 'bodyweight', 0, 0, 'crunch'],
    ['abWheel', 'Ab Wheel Rollout', 'bodyweight', 0, 0, 'ab-wheel-rollout'],
    ['cableCrunch', 'Cable Crunch', 'weight', 25, 2.5, 'cable-crunch'],
    ['egymAbdominalCrunch', 'eGym Abdominal Crunch', 'weight', 30, 2, 'egym-abdominal-crunch'],
    ['egymRotaryTorso', 'eGym Rotary Torso', 'weight', 25, 2, 'egym-rotary-torso'],
  ],
}

export const CATALOG: Exercise[] = GROUPS.flatMap((group) =>
  TABLE[group].map(([id, name, equipment, start, increment, image]) => ({
    id,
    name,
    group,
    equipment,
    start,
    increment,
    image,
  })),
)

const BY_ID = new Map(CATALOG.map((exercise) => [exercise.id, exercise]))

export function catalogExercise(id: string): Exercise | undefined {
  return BY_ID.get(id)
}

const CUSTOM_START: Record<Equipment, [start: number, increment: number]> = {
  barbell: [20, 2.5],
  machine: [20, 5],
  weight: [10, 1],
  bodyweight: [0, 0],
}

export function customExercise(id: string, name: string, equipment: Equipment): Exercise {
  const [start, increment] = CUSTOM_START[equipment]
  return { id, name, group: 'Custom', equipment, start, increment }
}
