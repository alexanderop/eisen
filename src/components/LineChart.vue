<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ values: number[]; format: (value: number) => string }>()

const WIDTH = 320
const HEIGHT = 170
const PAD = { top: 14, right: 14, bottom: 14, left: 14 }

const geometry = computed(() => {
  const low = Math.min(...props.values)
  const high = Math.max(...props.values)
  const span = high - low || 1
  const innerWidth = WIDTH - PAD.left - PAD.right
  const innerHeight = HEIGHT - PAD.top - PAD.bottom
  const points = props.values.map((value, index) => ({
    x: PAD.left + (props.values.length === 1 ? innerWidth / 2 : (index / (props.values.length - 1)) * innerWidth),
    y: PAD.top + (1 - (value - low) / span) * innerHeight,
  }))
  const line = points.map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(' ')
  const baseline = HEIGHT - PAD.bottom
  const area = `${points[0].x},${baseline} ${line} ${points[points.length - 1].x},${baseline}`
  return { points, line, area, low, high }
})
</script>

<template>
  <figure class="chart">
    <div class="scale">
      <span>{{ format(geometry.high) }}</span>
      <span v-if="geometry.low !== geometry.high">{{ format(geometry.low) }}</span>
    </div>
    <svg :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" role="img" aria-label="Weight per workout">
      <defs>
        <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="var(--primary)" stop-opacity="0.28" />
          <stop offset="1" stop-color="var(--primary)" stop-opacity="0" />
        </linearGradient>
      </defs>
      <polygon :points="geometry.area" fill="url(#chart-fill)" />
      <polyline
        :points="geometry.line"
        fill="none"
        stroke="var(--primary)"
        stroke-width="2.5"
        stroke-linejoin="round"
        stroke-linecap="round"
      />
      <circle
        v-for="(point, index) in geometry.points"
        :key="index"
        :cx="point.x"
        :cy="point.y"
        :r="index === geometry.points.length - 1 ? 5 : 2.5"
        fill="var(--primary)"
      />
    </svg>
  </figure>
</template>

<style scoped>
.chart {
  margin: 0;
  display: flex;
  gap: 8px;
}

.scale {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 12px;
  color: var(--muted-foreground);
  text-align: right;
}

svg {
  flex: 1;
  min-width: 0;
  height: auto;
}
</style>
