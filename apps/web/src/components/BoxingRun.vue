<script setup lang="ts">
/**
 * A boxing run, built around the combination. With gloves on the lifter cannot tap precisely and
 * looks from two or three metres: the combination the coach called last fills the screen, the
 * time is a band above it, and Pause is the one big button. End sits small in the corner.
 * What is shown comes from the very cues that are played, so the screen says what was said.
 */
import { useResizeObserver } from '@vueuse/core'
import { Pause, Play, Volume2 } from '@lucide/vue'
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'
import { THEME_COLORS, THEME_NAMES } from '../domain/boxing'
import { callSpan, planOf, upNext } from '../domain/boxingScreens'
import { haptic } from '../haptics'
import { formatClock } from '../rest'
import { timer, timerActive, timerBoxing, timerCuesShown, timerNow, wakeSound } from '../timer'
import { Button } from './ui'

const emit = defineEmits<{ end: []; toggle: [] }>()

const run = computed(() => timer.run!)
const view = computed(() => timerNow.value!)
const punch = computed(() => timerBoxing.value!)
const paused = computed(() => run.value.pausedAt !== null)
const style = computed(() => planOf(run.value.config).style)
const needsTap = computed(() => timer.restored && !timer.sound)
const phase = computed(() => view.value.phase)
const themeColor = computed(() => THEME_COLORS[punch.value.round.theme])

/** "ROUND 2/6" and the theme in a work phase, "GET READY" and "REST" around it. */
const heading = computed(() => {
  if (phase.value === 'ready') return 'Get ready'
  if (phase.value === 'rest') return 'Rest'
  return `Round ${view.value.round}/${view.value.rounds}`
})
const themeName = computed(() => (phase.value === 'work' ? THEME_NAMES[punch.value.round.theme] : ''))

const clock = computed(() => formatClock(Math.ceil((view.value.phaseLeft ?? 0) / 1000)))
/** The band under the time empties over the phase, like the ring of the other timers. */
const left = computed(() => 1 - view.value.progress)

const focus = computed(() => (phase.value === 'work' ? punch.value.focus : null))

/** From the last thing to throw, a call or "Again!", to the next one, for the thin bar that fills until then. */
const span = computed(() => {
  if (!punch.value.combo || phase.value !== 'work') return null
  const start = timerActive.value - view.value.phaseElapsed
  return callSpan(timerCuesShown.value, timerActive.value, start, start + (view.value.phaseTotal ?? 0))
})
const spanFill = computed(() => {
  const range = span.value
  if (!range || range.to <= range.from) return 1
  return Math.min(1, Math.max(0, (timerActive.value - range.from) / (range.to - range.from)))
})

/** What the combination area shows: the call, or what comes before one. */
const shown = computed(() => {
  const round = punch.value.round
  const theme = THEME_NAMES[round.theme]
  const color = THEME_COLORS[round.theme]
  if (phase.value !== 'work') {
    const next = phase.value === 'ready' ? 1 : view.value.round + 1
    return { key: `next-${next}`, label: upNext(next), big: theme, small: '', color, call: false }
  }
  if (round.theme === 'freestyle') {
    return { key: `free-${view.value.round}`, label: '', big: 'Freestyle', small: 'your pace, no calls', color, call: false }
  }
  const combo = punch.value.combo
  if (!combo) {
    return { key: `wait-${view.value.round}`, label: '', big: theme, small: 'listen for the call', color, call: false }
  }
  const numbers = style.value === 'numbers'
  return {
    key: `call-${span.value?.call ?? 0}`,
    label: '',
    // A line breaks after a dot or a dash, never before one.
    big: numbers ? combo.numbers.replaceAll(' – ', '\u00a0– ') : combo.names.replaceAll(' · ', '\u00a0· '),
    small: numbers ? combo.names : combo.numbers,
    color: 'var(--foreground)',
    call: true,
  }
})

/*
 * The combination is set as large as fits: on one line while that is still large, else on two,
 * and never under 40px. A longest combination that does not fit two lines at 40px takes more
 * lines rather than shrink further.
 */
const MIN_SIZE = 40
/** One line reads better than two: it is kept down to this size, or three quarters of two lines. */
const ONE_LINE = 64
const box = useTemplateRef<HTMLElement>('box')
const big = useTemplateRef<HTMLElement>('big')
const size = ref(MIN_SIZE)

function fits(element: HTMLElement, frame: HTMLElement, px: number, lines: number): boolean {
  element.style.fontSize = `${px}px`
  if (element.scrollWidth > element.clientWidth + 1 || element.offsetHeight > frame.clientHeight) return false
  return Math.round(element.offsetHeight / (px * 1.05)) <= lines
}

function largest(element: HTMLElement, frame: HTMLElement, max: number, lines: number): number | null {
  if (!fits(element, frame, MIN_SIZE, lines)) return null
  let low = MIN_SIZE
  let high = max
  while (high - low > 1) {
    const middle = Math.floor((low + high) / 2)
    if (fits(element, frame, middle, lines)) low = middle
    else high = middle
  }
  return low
}

function fit() {
  const element = big.value
  const frame = box.value
  if (!element || !frame || frame.clientHeight === 0) return
  const max = Math.min(style.value === 'numbers' && shown.value.call ? 220 : 150, frame.clientHeight)
  const one = largest(element, frame, max, 1)
  const two = largest(element, frame, max, 2)
  const best =
    one !== null && (one >= ONE_LINE || two === null || one >= two * 0.75)
      ? one
      : (two ?? largest(element, frame, max, 6) ?? MIN_SIZE)
  element.style.fontSize = `${best}px`
  size.value = best
}

watch(
  () => [shown.value.key, shown.value.big, style.value],
  async () => {
    await nextTick()
    fit()
  },
)
onMounted(fit)
useResizeObserver(box, fit)

/** A drilled combination: which go of how many, for the pips under it. Nothing for a single go. */
const goes = computed(() => (shown.value.call && punch.value.goes > 1 ? { go: punch.value.go, of: punch.value.goes } : null))

/**
 * A new call, or "Again!" for the one on screen, flashes the area in the round's colour and buzzes
 * once. Only a new call brings new text, which pops in.
 */
const flash = ref(0)
watch(
  () => span.value?.from,
  (from, before) => {
    if (from === undefined || from === before || paused.value) return
    flash.value += 1
    haptic('call')
  },
)
</script>

<template>
  <header class="head fade">
    <h2 class="name">{{ run.name }}</h2>
    <Button variant="secondary" class="end" @click="emit('end')">End</Button>
  </header>

  <button v-if="needsTap" class="sound" @click="wakeSound">
    <Volume2 :size="22" aria-hidden="true" />
    Tap for sound
  </button>

  <p class="line fade">
    <span class="heading">{{ heading }}</span>
    <span v-if="themeName" class="theme">{{ themeName }}</span>
  </p>

  <div class="time fade" role="timer">
    <span class="digits">{{ clock }}</span>
    <span class="track" aria-hidden="true">
      <span class="fill" :style="{ transform: `scaleX(${left})` }" />
    </span>
  </div>

  <p v-if="focus" :key="focus" class="band fade" :class="focus">{{ focus }}</p>

  <section class="call" :style="{ '--call': shown.color, '--round': themeColor }" aria-live="polite">
    <span v-if="flash > 0" :key="flash" class="flash" aria-hidden="true" />
    <p v-if="shown.label" class="label fade">{{ shown.label }}</p>
    <div ref="box" class="box fade">
      <p
        ref="big"
        :key="shown.key"
        class="big"
        :class="{ numbers: style === 'numbers' && shown.call, theme: !shown.call, pop: shown.call }"
      >
        {{ shown.big }}
      </p>
    </div>
    <div v-if="goes" class="goes fade" :aria-label="`Go ${goes.go} of ${goes.of}`">
      <span class="pips" aria-hidden="true">
        <span v-for="pip in goes.of" :key="pip" class="pip" :class="{ done: pip <= goes.go }" />
      </span>
      <span class="goes-text" aria-hidden="true">{{ goes.go }} of {{ goes.of }}</span>
    </div>
    <p v-if="shown.small" class="small fade">{{ shown.small }}</p>
    <span v-if="span" class="next fade" aria-hidden="true">
      <span :key="shown.key" class="next-fill" :style="{ transform: `scaleX(${spanFill})` }" />
    </span>
    <p v-if="paused" class="paused-word">Paused</p>
  </section>

  <Button :variant="paused ? 'default' : 'secondary'" class="pause" @click="emit('toggle')">
    <Play v-if="paused" :size="24" aria-hidden="true" />
    <Pause v-else :size="24" aria-hidden="true" />
    {{ paused ? 'Resume' : 'Pause' }}
  </Button>
</template>

<style scoped>
.head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.name {
  min-width: 0;
  overflow: hidden;
  color: var(--muted-foreground);
  font-size: var(--fs-base);
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.end {
  flex: none;
  min-width: 72px;
  height: 48px;
  border-radius: 14px;
}

.sound {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 52px;
  border-radius: 18px;
  background: var(--foreground);
  color: var(--background);
  font-size: var(--fs-md);
  font-weight: 800;
}

/* One line: the round in its colour, the theme after it, cut short before it wraps. */
.line {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 12px;
  line-height: 1.15;
  white-space: nowrap;
}

.heading {
  flex: none;
  color: var(--stage);
  font-size: clamp(22px, 3.6dvh, 30px);
  font-weight: 900;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-variant-numeric: tabular-nums;
}

.theme {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: clamp(16px, 2.6dvh, 20px);
  font-weight: 800;
}

/* The time is read, not watched: half the size of the other timers, with the phase as a band. */
.time {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.digits {
  font-size: clamp(40px, 7.5dvh, 64px);
  line-height: 1;
  font-weight: 900;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.track {
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: color-mix(in srgb, var(--stage) 18%, var(--border));
}

.fill {
  display: block;
  height: 100%;
  background: var(--stage);
  transform-origin: left;
  transition: transform 200ms linear;
}

/* Speed or power: a band across the screen, the colour says which before the word does. */
.band {
  flex: none;
  display: grid;
  place-items: center;
  height: clamp(40px, 7dvh, 56px);
  margin-bottom: -4px;
  border-radius: 14px;
  color: #0b0c0e;
  font-size: clamp(22px, 4dvh, 32px);
  font-weight: 900;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  animation: band 420ms cubic-bezier(0.2, 0.9, 0.3, 1.2);
}

.band.speed {
  background: var(--stage);
}

.band.power {
  background: var(--foreground);
}

/* The hero: everything left above Pause. */
.call {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 14px 16px 18px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--round) 30%, var(--border));
  border-radius: 24px;
  background: color-mix(in srgb, var(--round) 6%, var(--card));
  text-align: center;
}

.flash {
  position: absolute;
  inset: 0;
  background: var(--round);
  opacity: 0;
  animation: flash 260ms ease-out;
  pointer-events: none;
}

.label {
  position: relative;
  color: var(--muted-foreground);
  font-size: clamp(15px, 2.6dvh, 19px);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.box {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.big {
  width: 100%;
  color: var(--call);
  line-height: 1.05;
  font-size: 40px;
  font-weight: 900;
  letter-spacing: -0.02em;
  overflow-wrap: normal;
  word-break: keep-all;
  text-wrap: balance;
  font-variant-numeric: tabular-nums;
}

/* Numbers are few and short: set very large, with room between them. */
.big.numbers {
  letter-spacing: 0.02em;
  word-spacing: 0.08em;
}

.big.pop {
  animation: pop 260ms cubic-bezier(0.2, 0.9, 0.3, 1.3);
}

/* The goes of a drilled combination, read from three metres: done and now filled, still to come hollow. */
.goes {
  position: relative;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.pips {
  display: flex;
  gap: 10px;
}

.pip {
  width: clamp(22px, 4dvh, 32px);
  aspect-ratio: 1;
  border: 4px solid var(--round);
  border-radius: 50%;
}

.pip.done {
  background: var(--round);
}

.goes-text {
  font-size: clamp(22px, 4dvh, 32px);
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}

.small {
  position: relative;
  color: var(--muted-foreground);
  font-size: clamp(17px, 3dvh, 24px);
  line-height: 1.2;
  font-weight: 800;
  text-wrap: balance;
}

.next {
  position: absolute;
  left: 20px;
  right: 20px;
  bottom: 9px;
  height: 4px;
  overflow: hidden;
  border-radius: 2px;
  background: color-mix(in srgb, var(--round) 16%, transparent);
}

.next-fill {
  display: block;
  height: 100%;
  background: var(--round);
  transform-origin: left;
  transition: transform 200ms linear;
}

.paused-word {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--background) 55%, transparent);
  font-size: clamp(44px, min(15vw, 10dvh), 84px);
  font-weight: 900;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

/* Paused: the same colours, the whole screen at 60%, and only PAUSED and Resume at full strength. */
.timer.paused .fade {
  opacity: 0.6;
}

.pause {
  flex: none;
  width: 100%;
  height: 76px;
  gap: 10px;
  border-radius: 22px;
  font-size: var(--fs-lg);
}

@media (max-height: 700px) {
  .pause {
    height: 72px;
  }

  .call {
    padding: 10px 14px 16px;
  }
}

@keyframes flash {
  25% {
    opacity: 0.85;
  }
}

@keyframes pop {
  from {
    transform: scale(0.82);
  }
}

@keyframes band {
  from {
    transform: translateY(-8px) scaleY(0.6);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fill,
  .next-fill {
    transition: none;
  }

  .big.pop,
  .band {
    animation: none;
  }
}
</style>
