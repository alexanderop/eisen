<script setup lang="ts">
import { ChevronRight, Undo2 } from '@lucide/vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { BOXING_TEMPLATES, THEME_COLORS, THEME_NAMES, type BoxingRound, type BoxingStyle } from '../domain/boxing'
import {
  copyConfig,
  isTemplate,
  NUMBERS_KEY,
  planOf,
  planTotal,
  roundDetail,
  STYLE_EXAMPLE,
  withRounds,
  withStyle,
  withTemplate,
} from '../domain/boxingScreens'
import { holdText } from '../domain/program'
import type { TimerConfig } from '../domain/timer'
import { timerWheels } from '../domain/timerScreens'
import { keptRounds } from '../boxingDraft'
import { haptic } from '../haptics'
import BoxingRoundSheet from './BoxingRoundSheet.vue'
import { Card, Segmented, Wheel } from './ui'

/**
 * The boxing workout set up on the sofa: how many rounds and how long, then the fight card with
 * what each round is about. `draft` names the setup, so rounds that fell off the end when the
 * number went down come back when it goes up, also after leaving and coming back.
 */
const config = defineModel<TimerConfig>('config', { required: true })
const props = defineProps<{ draft: string }>()

const plan = computed(() => planOf(config.value))
const wheels = computed(() => timerWheels(config.value))

function setNumber(field: 'work' | 'rest' | 'rounds', value: number) {
  if (field !== 'rounds') {
    config.value = { ...config.value, [field]: value }
    return
  }
  const next = withRounds(config.value, value, keptRounds.get(props.draft))
  keptRounds.set(props.draft, next.kept)
  config.value = next.config
}

/** The round open in the sheet, null while it is closed. */
const open = ref<number | null>(null)
const openIndex = computed({
  get: () => open.value ?? 0,
  set: (value: number) => (open.value = value),
})

function openRound(index: number) {
  haptic('tap')
  open.value = index
}

/** The plan before the last template, while "Undo" is offered: until the next edit or six seconds. */
const undo = ref<{ before: TimerConfig; after: TimerConfig; name: string } | null>(null)
let undoTimer: ReturnType<typeof setTimeout> | undefined
const UNDO_MS = 6000

function applyTemplate(template: { name: string; rounds: BoxingRound[] }) {
  if (isTemplate(config.value, template.rounds)) return
  const after = withTemplate(config.value, template.rounds)
  undo.value = { before: copyConfig(config.value), after, name: template.name }
  config.value = after
  haptic('tap')
  clearTimeout(undoTimer)
  undoTimer = setTimeout(() => (undo.value = null), UNDO_MS)
}

function undoTemplate() {
  if (!undo.value) return
  config.value = { ...config.value, boxing: undo.value.before.boxing }
  undo.value = null
  clearTimeout(undoTimer)
  haptic('tap')
}

watch(config, (current) => {
  if (undo.value && current !== undo.value.after) undo.value = null
})

onBeforeUnmount(() => clearTimeout(undoTimer))

const STYLES = [
  { value: 'names' as const, label: 'Names' },
  { value: 'numbers' as const, label: 'Numbers' },
]
const style = computed({
  get: () => plan.value.style,
  set: (value: BoxingStyle) => (config.value = withStyle(config.value, value)),
})
</script>

<template>
  <Card as="section" class="numbers">
    <div class="wheels">
      <Wheel
        v-for="wheel in wheels"
        :key="wheel.field"
        :model-value="config[wheel.field]"
        :values="wheel.values"
        :header="wheel.header"
        :label="wheel.header"
        :format="wheel.format"
        :spoken="wheel.spoken"
        @update:model-value="setNumber(wheel.field, $event)"
      />
    </div>
  </Card>

  <section class="block">
    <div class="head">
      <h2 class="eyebrow">Templates</h2>
      <button v-if="undo" class="undo" @click="undoTemplate">
        <Undo2 :size="16" aria-hidden="true" />
        Undo {{ undo.name }}
      </button>
    </div>
    <div class="templates">
      <button
        v-for="template in BOXING_TEMPLATES"
        :key="template.name"
        class="template"
        :class="{ on: isTemplate(config, template.rounds) }"
        :aria-description="template.hint"
        :aria-pressed="isTemplate(config, template.rounds)"
        @click="applyTemplate(template)"
      >
        {{ template.name }}
      </button>
    </div>
  </section>

  <section class="block">
    <div class="head">
      <h2 class="eyebrow">Fight card</h2>
      <p class="total">{{ planTotal(config) }}</p>
    </div>
    <p class="rest-note">
      <span class="rest-line" aria-hidden="true" />
      rest {{ holdText(config.rest) }} between rounds
      <span class="rest-line" aria-hidden="true" />
    </p>
    <ol class="card">
      <li v-for="(round, index) in plan.rounds" :key="index">
        <button
          class="tile"
          :class="{ split: round.split > 0 }"
          :style="{ '--theme': THEME_COLORS[round.theme] }"
          @click="openRound(index)"
        >
          <span class="number" aria-hidden="true">{{ index + 1 }}</span>
          <span class="tile-text">
            <span class="sr-only">Round {{ index + 1 }}: </span>
            <span class="tile-theme">{{ THEME_NAMES[round.theme] }}</span>
            <span class="tile-detail">{{ roundDetail(round) }}</span>
          </span>
          <ChevronRight class="chevron" :size="20" aria-hidden="true" />
        </button>
      </li>
    </ol>
  </section>

  <section class="block">
    <h2 class="eyebrow">Calls</h2>
    <Card class="calls">
      <Segmented v-model="style" :options="STYLES" />
      <p class="example" aria-live="polite">
        <span class="example-big">{{ style === 'numbers' ? STYLE_EXAMPLE.numbers : STYLE_EXAMPLE.names }}</span>
        <span class="example-small">“{{ style === 'numbers' ? STYLE_EXAMPLE.sayNumbers : STYLE_EXAMPLE.say }}”</span>
      </p>
      <p class="key">{{ NUMBERS_KEY }}</p>
    </Card>
  </section>

  <BoxingRoundSheet
    v-if="open !== null"
    v-model:config="config"
    v-model:index="openIndex"
    @close="open = null"
  />
</template>

<style scoped>
.numbers {
  padding: 12px;
}

/* Rounds go up to "15", a round to "10:00" and a rest to "5:00": each wheel as wide as its numbers. */
.wheels {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.3fr) minmax(0, 1.1fr);
  gap: 8px;
}

.wheels :deep(.centre) {
  font-size: min(calc(var(--row) * 0.66), 6.4vw);
}

.block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.total {
  font-size: var(--fs-base);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.templates {
  display: flex;
  gap: 8px;
  margin: 0 -16px;
  padding: 0 16px 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.template {
  flex: none;
  height: 44px;
  padding: 0 16px;
  border: 1px solid var(--border);
  border-radius: var(--r-full);
  background: var(--secondary);
  font-size: var(--fs-sm-plus);
  font-weight: 700;
  white-space: nowrap;
}

.template.on {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 14%, var(--secondary));
  color: var(--primary);
}

.template:active,
.undo:active {
  transform: scale(0.97);
}

/* On the heading's line, right above the chips: always in sight, never scrolled away with them. */
.undo {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  margin: -10px -6px -10px 0;
  padding: 0 8px;
  color: var(--primary);
  font-size: var(--fs-sm-plus);
  font-weight: 800;
  animation: fade 160ms ease-out;
}

.rest-note {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--muted-foreground);
  font-size: var(--fs-sm);
  font-weight: 700;
  white-space: nowrap;
}

.rest-line {
  flex: 1;
  height: 1px;
  background: var(--border);
}

.card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

/* A round of the fight card: a block in its colour with its number, then what it is about. */
.tile {
  width: 100%;
  min-height: 76px;
  display: flex;
  align-items: stretch;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--card);
  text-align: left;
}

.tile:active {
  background: var(--secondary);
}

.number {
  flex: none;
  width: 60px;
  display: grid;
  place-items: center;
  background: var(--theme);
  color: #0b0c0e;
  font-size: 26px;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}

/* Speed and power: the block in two tones of its colour. */
.split .number {
  background: repeating-linear-gradient(
    135deg,
    color-mix(in srgb, var(--theme) 60%, white) 0 10px,
    color-mix(in srgb, var(--theme) 70%, black) 10px 20px
  );
  text-shadow: 0 0 6px color-mix(in srgb, var(--theme) 60%, white);
}

.tile-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 10px 14px;
}

.tile-theme {
  font-size: var(--fs-md);
  line-height: 1.25;
  font-weight: 800;
}

.tile-detail {
  font-size: var(--fs-sm);
  color: var(--muted-foreground);
}

.chevron {
  flex: none;
  align-self: center;
  margin-right: 12px;
  color: var(--muted-foreground);
}

.calls {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.example {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 0 2px;
  text-align: center;
}

.example-big {
  font-size: var(--fs-xl);
  font-weight: 900;
  letter-spacing: -0.01em;
}

.example-small {
  font-size: var(--fs-sm-plus);
  color: var(--muted-foreground);
}

.key {
  padding-top: 10px;
  border-top: 1px solid var(--border);
  font-size: var(--fs-xs);
  text-align: center;
  color: var(--muted-foreground);
}

@keyframes fade {
  from {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .undo {
    animation: none;
  }
}
</style>
