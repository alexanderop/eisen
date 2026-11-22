import { loadText } from './domain/program'
import { sharedStats, type SharedExercise, type SharedWorkout } from './domain/share'

/** The app's palette. A canvas cannot read the CSS variables of a page it is not part of. */
const COLOR = {
  background: '#0b0c0e',
  card: '#15171b',
  border: '#2b2f37',
  foreground: '#f4f5f7',
  muted: '#8d939f',
  primary: '#c8f031',
  onPrimary: '#11140a',
  warn: '#ffb020',
}

const FONT = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'
const WIDTH = 1080
/** 4:5, the tallest picture a feed shows uncropped. Longer workouts make it taller. */
const MIN_HEIGHT = 1350
const PAD = 72
const INNER = WIDTH - PAD * 2

const HEADER = 452
const STATS = 176
const ROW = 204
const ROW_GAP = 20
const FOOTER = 132
const PILL = 76
const PILL_GAP = 12

type Context = CanvasRenderingContext2D

function font(context: Context, weight: number, size: number) {
  context.font = `${weight} ${size}px ${FONT}`
}

/** Cuts a text that is too wide and ends it with an ellipsis. */
function fit(context: Context, text: string, width: number): string {
  if (context.measureText(text).width <= width) return text
  let cut = text
  while (cut.length > 1 && context.measureText(`${cut}…`).width > width) cut = cut.slice(0, -1)
  return `${cut.trimEnd()}…`
}

function box(context: Context, x: number, y: number, width: number, height: number, radius: number) {
  context.beginPath()
  context.roundRect(x, y, width, height, radius)
}

/** The barbell from the app icon. */
function barbell(context: Context, x: number, y: number, size: number) {
  const unit = size / 264
  const part = (left: number, top: number, width: number, height: number, radius: number) => {
    box(context, x + left * unit, y + top * unit, width * unit, height * unit, radius * unit)
    context.fill()
  }
  part(22, 68, 220, 24, 6)
  part(42, 0, 34, 160, 10)
  part(188, 0, 34, 160, 10)
  part(0, 28, 30, 104, 10)
  part(234, 28, 30, 104, 10)
}

function background(context: Context, height: number) {
  context.fillStyle = COLOR.background
  context.fillRect(0, 0, WIDTH, height)

  const glow = context.createRadialGradient(WIDTH - 60, -40, 0, WIDTH - 60, -40, 900)
  glow.addColorStop(0, 'rgb(200 240 49 / 0.30)')
  glow.addColorStop(0.45, 'rgb(200 240 49 / 0.07)')
  glow.addColorStop(1, 'rgb(200 240 49 / 0)')
  context.fillStyle = glow
  context.fillRect(0, 0, WIDTH, height)

  // A barbell far too big for the picture, barely there, behind the headline.
  context.save()
  context.globalAlpha = 0.05
  context.fillStyle = COLOR.primary
  context.translate(WIDTH - 250, 210)
  context.rotate(-0.21)
  barbell(context, -390, -236, 780)
  context.restore()
}

function header(context: Context, shared: SharedWorkout, date: string) {
  context.fillStyle = COLOR.primary
  barbell(context, PAD, 80, 66)
  font(context, 800, 30)
  context.letterSpacing = '5px'
  context.textBaseline = 'middle'
  context.fillText('EISEN', PAD + 88, 102)
  context.letterSpacing = '0px'

  context.textBaseline = 'alphabetic'
  context.fillStyle = COLOR.muted
  font(context, 700, 30)
  context.letterSpacing = '4px'
  context.fillText('WORKOUT DONE', PAD, 250)
  context.letterSpacing = '0px'

  context.fillStyle = COLOR.foreground
  let size = 116
  font(context, 800, size)
  while (size > 68 && context.measureText(shared.name).width > INNER) {
    size -= 4
    font(context, 800, size)
  }
  context.fillText(fit(context, shared.name, INNER), PAD - 4, 364)

  context.fillStyle = COLOR.muted
  font(context, 500, 38)
  context.fillText(shared.minutes === null ? date : `${date}  ·  ${shared.minutes} min`, PAD, 426)
}

function stats(context: Context, shared: SharedWorkout, top: number) {
  const { volume, sets, reps } = sharedStats(shared)
  const tiles = [
    ...(volume > 0
      ? [{ value: Math.round(volume).toLocaleString('en-US'), unit: shared.unit, label: 'VOLUME' }]
      : []),
    { value: String(sets), unit: '', label: 'SETS' },
    { value: String(reps), unit: '', label: 'REPS' },
    ...(volume > 0 || shared.minutes === null
      ? []
      : [{ value: String(shared.minutes), unit: 'min', label: 'TIME' }]),
  ]
  const gap = 16
  // The volume is the longest number, so its tile is the widest.
  const weights = tiles.map((tile) => (tile.label === 'VOLUME' ? 1.5 : 1))
  const total = weights.reduce((sum, weight) => sum + weight, 0)
  let x = PAD
  tiles.forEach((tile, index) => {
    const width = ((INNER - gap * (tiles.length - 1)) * weights[index]) / total
    box(context, x, top, width, 148, 28)
    context.fillStyle = COLOR.card
    context.fill()
    context.strokeStyle = COLOR.border
    context.lineWidth = 2
    context.stroke()

    context.fillStyle = COLOR.foreground
    font(context, 800, 58)
    context.fillText(tile.value, x + 28, top + 78)
    if (tile.unit) {
      const offset = context.measureText(tile.value).width
      context.fillStyle = COLOR.muted
      font(context, 700, 30)
      context.fillText(tile.unit, x + 28 + offset + 8, top + 78)
    }
    context.fillStyle = COLOR.muted
    font(context, 700, 22)
    context.letterSpacing = '3px'
    context.fillText(tile.label, x + 28, top + 120)
    context.letterSpacing = '0px'
    x += width + gap
  })
}

function badge(context: Context, x: number, y: number): number {
  font(context, 800, 22)
  context.letterSpacing = '2px'
  const width = context.measureText('RECORD').width + 34
  box(context, x, y - 31, width, 42, 21)
  context.fillStyle = COLOR.primary
  context.fill()
  context.fillStyle = COLOR.onPrimary
  context.fillText('RECORD', x + 18, y - 2)
  context.letterSpacing = '0px'
  return width
}

function pill(context: Context, exercise: SharedExercise, reps: number | null, x: number, y: number) {
  const missed = reps !== null && exercise.targetReps !== null && reps < exercise.targetReps
  box(context, x, y, PILL, PILL, 22)
  if (reps === null) {
    context.strokeStyle = COLOR.border
    context.lineWidth = 3
    context.setLineDash([9, 8])
    context.stroke()
    context.setLineDash([])
  } else if (missed) {
    context.fillStyle = 'rgb(255 176 32 / 0.14)'
    context.fill()
    context.strokeStyle = COLOR.warn
    context.lineWidth = 3
    context.stroke()
  } else {
    context.fillStyle = COLOR.primary
    context.fill()
  }
  context.fillStyle = reps === null ? COLOR.muted : missed ? COLOR.warn : COLOR.onPrimary
  font(context, 800, 36)
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(reps === null ? '–' : String(reps), x + PILL / 2, y + PILL / 2 + 2)
  context.textAlign = 'left'
  context.textBaseline = 'alphabetic'
}

function exerciseRow(context: Context, shared: SharedWorkout, exercise: SharedExercise, top: number) {
  box(context, PAD, top, INNER, ROW, 32)
  context.fillStyle = COLOR.card
  context.fill()
  context.strokeStyle = COLOR.border
  context.lineWidth = 2
  context.stroke()

  const left = PAD + 32
  const right = PAD + INNER - 32
  const baseline = top + 76

  const load = loadText(exercise.bodyweight, exercise.weight, shared.unit)
  font(context, 800, 48)
  // Plain bodyweight stays quiet. Added weight is a load like any other and gets the accent.
  context.fillStyle = exercise.bodyweight && exercise.weight === 0 ? COLOR.muted : COLOR.primary
  context.textAlign = 'right'
  context.fillText(load, right, baseline)
  const loadWidth = context.measureText(load).width
  context.textAlign = 'left'

  font(context, 800, 22)
  context.letterSpacing = '2px'
  const badgeWidth = exercise.record ? context.measureText('RECORD').width + 34 + 18 : 0
  context.letterSpacing = '0px'

  font(context, 700, 42)
  context.fillStyle = COLOR.foreground
  const name = fit(context, exercise.name, right - left - loadWidth - 28 - badgeWidth)
  context.fillText(name, left, baseline)
  if (exercise.record) {
    font(context, 700, 42)
    badge(context, left + context.measureText(name).width + 18, baseline)
  }

  exercise.sets.forEach((reps, index) => {
    pill(context, exercise, reps, left + index * (PILL + PILL_GAP), top + 100)
  })
}

function footer(context: Context, height: number, site: string) {
  const y = height - 64
  context.fillStyle = COLOR.muted
  font(context, 600, 28)
  context.fillText('Tracked with Eisen 5×5', PAD, y)
  context.textAlign = 'right'
  context.fillText(fit(context, site, 480), WIDTH - PAD, y)
  context.textAlign = 'left'
}

/**
 * Draws a finished workout as a picture to post or send.
 * `date` is already written out, `site` is where the app lives, shown in the corner.
 */
export function drawWorkoutImage(shared: SharedWorkout, date: string, site: string): HTMLCanvasElement {
  const rows = shared.exercises.length
  const content = HEADER + 40 + STATS + rows * (ROW + ROW_GAP) + FOOTER
  const height = Math.max(MIN_HEIGHT, content)
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) throw new Error('This browser cannot draw the picture.')

  background(context, height)
  header(context, shared, date)
  // A short workout is spread over the picture instead of leaving the bottom empty.
  const spare = Math.min(height - content, 120)
  const statsTop = HEADER + 40 + spare / 2
  stats(context, shared, statsTop)
  const listTop = statsTop + STATS + spare / 2
  shared.exercises.forEach((exercise, index) => {
    exerciseRow(context, shared, exercise, listTop + index * (ROW + ROW_GAP))
  })
  footer(context, height, site)
  return canvas
}

export function canvasBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('The picture could not be made.'))),
      'image/png',
    )
  })
}
