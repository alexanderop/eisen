<script setup lang="ts">
import { computed, ref } from 'vue'
import { niceTicks } from '../domain/chart'
import { haptic } from '../haptics'

export interface ChartPoint {
  time: number
  value: number
  /** Reps were missed at this weight. */
  missed?: boolean
  /** The weight is lower than the time before. */
  deload?: boolean
  /** The best of the exercise so far. The parent decides which points to mark. */
  record?: boolean
}

export interface ChartSeries {
  id: string
  name: string
  color: string
  points: ChartPoint[]
}

/**
 * Lines over time on one shared axis. A drag, a tap or the arrow keys move a crosshair
 * that snaps to the nearest workout. The parent shows what is under it.
 * `events` draws every point with its outcome, for a single exercise.
 */
const props = defineProps<{
  series: ChartSeries[]
  format: (value: number) => string
  formatTime: (time: number) => string
  label: string
  events?: boolean
}>()

/** The time the crosshair is on. null = at rest. */
const active = defineModel<number | null>('active', { default: null })

const WIDTH = 328
const HEIGHT = 196
const PAD = { top: 12, right: 14, bottom: 24, left: 44 }
const INNER_WIDTH = WIDTH - PAD.left - PAD.right
const INNER_HEIGHT = HEIGHT - PAD.top - PAD.bottom
/** Past this many points, only the points that say something keep their marker. */
const DENSE = 45
/** Past this many points, markers shrink so neighbours do not run into each other. */
const CROWDED = 14

const times = computed(() =>
  [...new Set(props.series.flatMap((entry) => entry.points.map((point) => point.time)))].sort(
    (a, b) => a - b,
  ),
)

const scale = computed(() => {
  const values = props.series.flatMap((entry) => entry.points.map((point) => point.value))
  const ticks = niceTicks(Math.min(...values), Math.max(...values))
  const low = ticks[0]
  const span = ticks[ticks.length - 1] - low
  const start = times.value[0]
  const duration = times.value[times.value.length - 1] - start
  return {
    ticks,
    x: (time: number) =>
      PAD.left + (duration === 0 ? INNER_WIDTH / 2 : ((time - start) / duration) * INNER_WIDTH),
    y: (value: number) => PAD.top + (1 - (value - low) / span) * INNER_HEIGHT,
  }
})

const lines = computed(() =>
  props.series.map((entry) => {
    const points = entry.points.map((point) => ({
      ...point,
      x: scale.value.x(point.time),
      y: scale.value.y(point.value),
    }))
    const path = points.map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(' ')
    const baseline = PAD.top + INNER_HEIGHT
    const last = points[points.length - 1]
    const dense = points.length > DENSE
    const small = points.length > CROWDED
    return {
      ...entry,
      path,
      area: `${points[0].x.toFixed(1)},${baseline} ${path} ${last.x.toFixed(1)},${baseline}`,
      last,
      radius: small ? 3 : 4.5,
      marked: props.events
        ? points.filter(
            (point) => !dense || point.missed || point.deload || point.record || point === last,
          )
        : [last],
    }
  }),
)

/** The point of every series that sits exactly under the crosshair. */
const focused = computed(() =>
  active.value === null
    ? []
    : lines.value.flatMap((line) => {
        const point = line.points.find((entry) => entry.time === active.value)
        return point ? [{ id: line.id, color: line.color, x: scale.value.x(point.time), y: scale.value.y(point.value) }] : []
      }),
)

function triangle(x: number, y: number, radius: number): string {
  const half = radius + 2.5
  return `M${x - half},${y - half + 1} L${x + half},${y - half + 1} L${x},${y + half} Z`
}

const svg = ref<SVGSVGElement | null>(null)

function moveTo(time: number) {
  if (time === active.value) return
  active.value = time
  haptic('step')
}

function pick(event: PointerEvent) {
  const box = svg.value?.getBoundingClientRect()
  if (!box || times.value.length === 0) return
  const pointer = ((event.clientX - box.left) / box.width) * WIDTH
  moveTo(
    times.value.reduce((best, time) =>
      Math.abs(scale.value.x(time) - pointer) < Math.abs(scale.value.x(best) - pointer) ? time : best,
    ),
  )
}

/** A mouse hovers. A finger has to touch, and its selection stays after it lifts. */
function onMove(event: PointerEvent) {
  if (event.pointerType === 'mouse' || event.buttons > 0) pick(event)
}

function onLeave(event: PointerEvent) {
  if (event.pointerType === 'mouse') active.value = null
}

function onKey(event: KeyboardEvent) {
  const all = times.value
  const index = active.value === null ? all.length : all.indexOf(active.value)
  const target = {
    ArrowLeft: Math.max(0, index - 1),
    ArrowRight: Math.min(all.length - 1, index + 1),
    Home: 0,
    End: all.length - 1,
  }[event.key]
  if (event.key === 'Escape') active.value = null
  if (target === undefined) return
  event.preventDefault()
  moveTo(all[target])
}
</script>

<template>
  <svg
    ref="svg"
    class="chart"
    :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
    role="group"
    tabindex="0"
    :aria-label="`${label}. Use the arrow keys to step through the workouts.`"
    @pointerdown="pick"
    @pointermove="onMove"
    @pointerleave="onLeave"
    @keydown="onKey"
  >
    <g class="grid">
      <template v-for="tick in scale.ticks" :key="tick">
        <line :x1="PAD.left" :x2="WIDTH - PAD.right" :y1="scale.y(tick)" :y2="scale.y(tick)" />
        <text :x="PAD.left - 8" :y="scale.y(tick)" text-anchor="end" dominant-baseline="central">
          {{ format(tick) }}
        </text>
      </template>
      <text :x="PAD.left" :y="HEIGHT - 4">{{ formatTime(times[0]) }}</text>
      <text v-if="times.length > 1" :x="WIDTH - PAD.right" :y="HEIGHT - 4" text-anchor="end">
        {{ formatTime(times[times.length - 1]) }}
      </text>
    </g>

    <line
      v-if="active !== null"
      class="crosshair"
      :x1="scale.x(active)"
      :x2="scale.x(active)"
      :y1="PAD.top"
      :y2="PAD.top + INNER_HEIGHT"
    />

    <g v-for="line in lines" :key="line.id">
      <template v-if="events && line.points.length > 1">
        <defs>
          <linearGradient :id="`wash-${line.id}`" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" :stop-color="line.color" stop-opacity="0.2" />
            <stop offset="1" :stop-color="line.color" stop-opacity="0" />
          </linearGradient>
        </defs>
        <polygon class="wash" :points="line.area" :fill="`url(#wash-${line.id})`" />
      </template>
      <polyline class="line" :points="line.path" :stroke="line.color" pathLength="1" />
      <template v-for="point in line.marked" :key="point.time">
        <circle v-if="point.record" class="halo" :cx="point.x" :cy="point.y" :r="line.radius + 4.5" :stroke="line.color" />
        <path
          v-if="point.deload"
          class="marker"
          :class="{ missed: point.missed }"
          :d="triangle(point.x, point.y, line.radius)"
          :fill="line.color"
        />
        <circle
          v-else
          class="marker"
          :class="{ missed: point.missed }"
          :cx="point.x"
          :cy="point.y"
          :r="line.radius"
          :fill="line.color"
        />
      </template>
    </g>

    <circle
      v-for="point in focused"
      :key="point.id"
      class="focus"
      :cx="point.x"
      :cy="point.y"
      r="7"
      :stroke="point.color"
    />
  </svg>
</template>

<style scoped>
.chart {
  display: block;
  width: 100%;
  height: auto;
  /* A sideways drag scrubs the chart. An up or down drag still scrolls the page. */
  touch-action: pan-y;
  user-select: none;
  -webkit-user-select: none;
  cursor: crosshair;
  border-radius: 8px;
}

.chart:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 4px;
}

.grid line {
  stroke: var(--border);
  stroke-width: 1;
}

.grid text {
  fill: var(--muted-foreground);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.crosshair {
  stroke: var(--muted-foreground);
  stroke-width: 1;
}

.line {
  fill: none;
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
  stroke-dasharray: 1;
  animation: draw 0.7s ease-out both;
}

.wash {
  animation: fade 0.7s ease-out both;
}

/* A ring in the card colour keeps a marker readable where it sits on a line. */
.marker {
  stroke: var(--card);
  stroke-width: 2;
}

.marker.missed {
  fill: var(--card);
  stroke: var(--warn);
}

.halo {
  fill: none;
  stroke-width: 1.5;
  opacity: 0.7;
}

.focus {
  fill: none;
  stroke-width: 2;
}

@keyframes draw {
  from {
    stroke-dashoffset: 1;
  }
  to {
    stroke-dashoffset: 0;
  }
}

@keyframes fade {
  from {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .line,
  .wash {
    animation: none;
  }
}
</style>
