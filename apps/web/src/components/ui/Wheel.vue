<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { haptic } from '../../haptics'

/**
 * Picks one of a list of numbers by scrolling, like the wheels of an alarm clock.
 * Three rows show and the middle one is the value. It snaps to a row, ticks once per row and
 * changes the value only once it has come to rest. A tap on the row above or below moves there.
 * Screen readers and keyboards get a native slider over the same rows.
 */
const model = defineModel<number>({ required: true })
const props = withDefaults(
  defineProps<{
    values: number[]
    header: string
    label: string
    format?: (value: number) => string
    /** What a screen reader says for a value. */
    spoken?: (value: number) => string
  }>(),
  { format: (value: number) => String(value), spoken: undefined },
)

const list = ref<HTMLElement>()
/** The row in the middle right now, also while the wheel still turns. */
const centre = ref(0)
const touching = ref(false)
/** Older browsers have no `scrollend`: the wheel counts as settled once the scrolling pauses. */
const SETTLE_MS = 150
let settleTimer: ReturnType<typeof setTimeout> | undefined

const valueIndex = computed(() => {
  const exact = props.values.indexOf(model.value)
  if (exact !== -1) return exact
  return props.values.reduce(
    (best, value, index) =>
      Math.abs(value - model.value) < Math.abs(props.values[best] - model.value) ? index : best,
    0,
  )
})

function rowHeight(): number {
  return (list.value?.clientHeight ?? 0) / 3
}

function clampRow(row: number): number {
  return Math.min(props.values.length - 1, Math.max(0, row))
}

/** Puts a row in the middle at once, without a tick or a change. */
function place(row: number) {
  centre.value = row
  if (list.value) list.value.scrollTop = row * rowHeight()
}

function scrollToRow(row: number) {
  const smooth = !matchMedia('(prefers-reduced-motion: reduce)').matches
  list.value?.scrollTo({ top: clampRow(row) * rowHeight(), behavior: smooth ? 'smooth' : 'instant' })
}

function onScroll() {
  const height = rowHeight()
  if (!list.value || height === 0) return
  const row = clampRow(Math.round(list.value.scrollTop / height))
  if (row !== centre.value) {
    centre.value = row
    haptic('step')
  }
  clearTimeout(settleTimer)
  settleTimer = setTimeout(settle, SETTLE_MS)
}

function settle() {
  clearTimeout(settleTimer)
  if (touching.value) return
  const value = props.values[centre.value]
  if (value !== undefined && value !== model.value) model.value = value
}

function onTouchEnd() {
  touching.value = false
  clearTimeout(settleTimer)
  settleTimer = setTimeout(settle, SETTLE_MS)
}

function onInput(event: Event) {
  scrollToRow(Number((event.target as HTMLInputElement).value))
}

// A value set from outside, or rows that moved because an off-step weight dropped out.
watch(valueIndex, (row) => {
  if (!touching.value && row !== centre.value) place(row)
})

onMounted(() => place(valueIndex.value))
onBeforeUnmount(() => clearTimeout(settleTimer))
</script>

<template>
  <div class="wheel">
    <p class="header" aria-hidden="true">{{ header }}</p>
    <div class="window">
      <div class="band" aria-hidden="true" />
      <div
        ref="list"
        class="list"
        aria-hidden="true"
        @scroll.passive="onScroll"
        @scrollend="settle"
        @touchstart.passive="touching = true"
        @touchend.passive="onTouchEnd"
        @touchcancel.passive="onTouchEnd"
      >
        <div class="spacer" />
        <div
          v-for="(value, row) in values"
          :key="value"
          class="item"
          :class="{ centre: row === centre, near: Math.abs(row - centre) === 1 }"
          @click="scrollToRow(row)"
        >
          {{ format(value) }}
        </div>
        <div class="spacer" />
      </div>
    </div>
    <input
      class="sr-only"
      type="range"
      min="0"
      :max="values.length - 1"
      step="1"
      :value="centre"
      :aria-label="label"
      :aria-valuetext="(spoken ?? format)(values[centre] ?? model)"
      @input="onInput"
    />
  </div>
</template>

<style scoped>
.wheel {
  --row: clamp(40px, 7dvh, 62px);
  position: relative;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--background);
  overflow: hidden;
}

@media (max-height: 700px) {
  .wheel {
    --row: 38px;
  }
}

.wheel:has(input:focus-visible) {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.header {
  padding: 6px 0;
  text-align: center;
  font-size: var(--fs-base);
  font-weight: 700;
  background: var(--secondary);
  border-bottom: 1px solid var(--border);
}

.window {
  position: relative;
}

/* The middle row, between two lime marks. */
.band {
  position: absolute;
  inset: var(--row) 4px auto;
  height: var(--row);
  border-radius: var(--r-md);
  background: var(--secondary);
  pointer-events: none;
}

.band::before,
.band::after {
  content: '';
  position: absolute;
  top: 28%;
  bottom: 28%;
  width: 4px;
  border-radius: var(--r-full);
  background: var(--primary);
}

.band::before {
  left: 10px;
}

.band::after {
  right: 10px;
}

.list {
  position: relative;
  height: calc(var(--row) * 3);
  overflow-y: auto;
  overscroll-behavior: contain;
  scroll-snap-type: y mandatory;
  scrollbar-width: none;
  touch-action: pan-y;
}

.list::-webkit-scrollbar {
  display: none;
}

.spacer {
  height: var(--row);
}

.item {
  height: var(--row);
  display: grid;
  place-items: center;
  scroll-snap-align: center;
  font-size: calc(var(--row) * 0.42);
  font-weight: 700;
  color: var(--muted-foreground);
  opacity: 0.35;
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
}

.near {
  opacity: 1;
}

.centre {
  opacity: 1;
  color: var(--foreground);
  font-size: calc(var(--row) * 0.66);
  font-weight: 800;
  letter-spacing: -0.02em;
}
</style>
