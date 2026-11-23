<script setup lang="ts">
import { ArrowDown, ArrowUp, Check, ChevronLeft, ChevronRight, Copy, CopyPlus, Play, Trash2, Volume2 } from '@lucide/vue'
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import {
  BOXING_PACES,
  BOXING_SPLITS,
  BOXING_THEMES,
  comboSound,
  copiedToAll,
  MAX_BOXING_ROUNDS,
  paceOf,
  roundCombos,
  THEME_COLORS,
  THEME_HINTS,
  THEME_NAMES,
  type BoxingCombo,
  type BoxingTheme,
} from '../domain/boxing'
import {
  callsText,
  checkedOf,
  comboGroups,
  pickOf,
  pickText,
  planOf,
  roundCalls,
  themeExample,
  withRoundAction,
  withRoundChange,
  type RoundAction,
} from '../domain/boxingScreens'
import type { TimerConfig } from '../domain/timer'
import { haptic } from '../haptics'
import { state } from '../store'
import { playSample } from '../timer'
import { Button, ChipGroup, Sheet } from './ui'

/**
 * One round of a boxing workout: its theme, which of its combinations, the pace, speed and power.
 * Every change applies at once; the arrows step through the rounds without closing. The sheet
 * keeps one height, so it does not jump as the theme changes.
 */
const config = defineModel<TimerConfig>('config', { required: true })
const index = defineModel<number>('index', { required: true })
defineEmits<{ close: [] }>()

const ids = useId()
const plan = computed(() => planOf(config.value))
const round = computed(() => plan.value.rounds[index.value])
const count = computed(() => plan.value.rounds.length)
const freestyle = computed(() => round.value.theme === 'freestyle')

/** The round's settings, or the list of its combinations to pick from. */
const page = ref<'round' | 'combos'>('round')

function pick(theme: BoxingTheme) {
  if (theme === round.value.theme) return
  haptic('tap')
  config.value = withRoundChange(config.value, index.value, { theme })
}

function step(direction: 1 | -1) {
  index.value = Math.min(count.value - 1, Math.max(0, index.value + direction))
  haptic('step')
}

const PACE_OPTIONS = BOXING_PACES.map((pace) => ({ value: pace.name, label: pace.name }))
const pace = computed({
  get: () => paceOf(round.value.every),
  set: (name: string) => {
    const every = BOXING_PACES.find((entry) => entry.name === name)?.every
    if (every === undefined) return
    config.value = withRoundChange(config.value, index.value, { every })
    haptic('tap')
  },
})

const SPLIT_OPTIONS = BOXING_SPLITS.map((split) => ({ value: String(split), label: split === 0 ? 'Off' : `${split}s` }))
const split = computed({
  get: () => String(round.value.split),
  set: (value: string) => {
    config.value = withRoundChange(config.value, index.value, { split: Number(value) })
    haptic('tap')
  },
})

const calls = computed(() =>
  callsText(roundCalls(config.value, index.value, state.settings.timerCalls), config.value.work),
)

/** "Listen" goes through the round's combinations in order, so each tap is the next one. */
const listened = ref<BoxingCombo | null>(null)
let listenAt = 0
function listen() {
  const combos = roundCombos(round.value)
  if (combos.length === 0) return
  const combo = combos[listenAt % combos.length]
  listenAt += 1
  listened.value = combo
  void playSample(comboSound(combo, plan.value.style))
}

function hear(combo: BoxingCombo) {
  void playSample(comboSound(combo, plan.value.style))
}

/** The combinations checked in the list. None checked is all of them, as the coach sees it. */
const checked = ref<string[]>([])
function openCombos() {
  checked.value = [...checkedOf(round.value)]
  page.value = 'combos'
  haptic('tap')
}

function setChecked(next: string[]) {
  checked.value = next
  config.value = withRoundChange(config.value, index.value, { pick: pickOf(round.value.theme, next) })
}

function toggle(id: string) {
  setChecked(checked.value.includes(id) ? checked.value.filter((entry) => entry !== id) : [...checked.value, id])
  haptic('tap')
}

const groups = computed(() => comboGroups(round.value.theme))
const allIds = computed(() => groups.value.flatMap((group) => group.combos.map((combo) => combo.id)))

/** "Copied to all 6 rounds" stands in the button's place for a moment. */
const copied = ref(false)
let copiedTimer: ReturnType<typeof setTimeout> | undefined
function copyToAll() {
  config.value = { ...config.value, boxing: copiedToAll(plan.value, index.value) }
  haptic('success')
  copied.value = true
  clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => (copied.value = false), 2500)
}

function act(action: RoundAction) {
  const next = withRoundAction(config.value, index.value, action)
  config.value = next.config
  index.value = next.index
  haptic('tap')
}

// Another round, or another theme: what was played, picked and copied belongs to the one before.
watch([index, () => round.value.theme], () => {
  listened.value = null
  listenAt = 0
  copied.value = false
  page.value = 'round'
})

onBeforeUnmount(() => clearTimeout(copiedTimer))
</script>

<template>
  <Sheet :title="page === 'combos' ? `Round ${index + 1} · Combinations` : `Round ${index + 1}`" tall @close="$emit('close')">
    <template v-if="page === 'round'" #actions>
      <div class="arrows">
        <Button
          size="icon"
          variant="secondary"
          class="rounded-full"
          :disabled="index === 0"
          aria-label="Previous round"
          @click="step(-1)"
        >
          <ChevronLeft :size="22" aria-hidden="true" />
        </Button>
        <Button
          size="icon"
          variant="secondary"
          class="rounded-full"
          :disabled="index === count - 1"
          aria-label="Next round"
          @click="step(1)"
        >
          <ChevronRight :size="22" aria-hidden="true" />
        </Button>
      </div>
    </template>

    <template v-if="page === 'round'">
      <section class="block">
        <p :id="`${ids}-theme`" class="eyebrow">Theme</p>
        <div class="themes" role="radiogroup" :aria-labelledby="`${ids}-theme`">
          <button
            v-for="theme in BOXING_THEMES"
            :key="theme"
            class="theme"
            :class="{ chosen: theme === round.theme }"
            :style="{ '--theme': THEME_COLORS[theme] }"
            role="radio"
            :aria-checked="theme === round.theme"
            :aria-label="`${THEME_NAMES[theme]}: ${THEME_HINTS[theme]}`"
            @click="pick(theme)"
          >
            <span class="theme-name">{{ THEME_NAMES[theme] }}</span>
            <span class="theme-example" aria-hidden="true">{{ themeExample(theme, plan.style) }}</span>
          </button>
        </div>
        <p class="hint">{{ THEME_HINTS[round.theme] }}</p>
      </section>

      <div v-if="freestyle" class="calm">
        <p class="calm-title">No calls in a freestyle round.</p>
        <p class="calm-text">You box, the clock runs.</p>
      </div>

      <template v-else>
        <button class="combos-row" @click="openCombos">
          <span>{{ pickText(round) }}</span>
          <ChevronRight :size="20" aria-hidden="true" />
        </button>

        <section class="block">
          <p class="eyebrow">Pace</p>
          <ChipGroup v-model="pace" :options="PACE_OPTIONS" label="Pace" />
          <p class="hint" aria-live="polite">{{ calls }}</p>
        </section>

        <section class="block">
          <p class="eyebrow">Speed and power</p>
          <ChipGroup v-model="split" :options="SPLIT_OPTIONS" label="Speed and power" />
          <p class="hint split-hint">
            {{
              round.split > 0
                ? 'Speed and power take turns. The coach calls faster in speed and leaves room in power.'
                : 'Off: one pace all round.'
            }}
          </p>
        </section>

        <button class="listen" @click="listen">
          <span class="listen-icon" :style="{ '--theme': THEME_COLORS[round.theme] }">
            <Volume2 :size="22" aria-hidden="true" />
          </span>
          <span class="listen-text">
            <span class="listen-label">Listen</span>
            <span class="listen-combo" aria-live="polite">
              {{ listened ? (plan.style === 'numbers' ? listened.numbers : listened.names) : 'Hear the calls of this round' }}
            </span>
          </span>
        </button>
      </template>

      <section class="block">
        <p class="eyebrow">Round</p>
        <div class="actions">
          <Button variant="secondary" :disabled="index === 0" @click="act('earlier')">
            <ArrowUp :size="18" aria-hidden="true" />
            Move earlier
          </Button>
          <Button variant="secondary" :disabled="index === count - 1" @click="act('later')">
            <ArrowDown :size="18" aria-hidden="true" />
            Move later
          </Button>
          <Button variant="secondary" :disabled="count >= MAX_BOXING_ROUNDS" @click="act('duplicate')">
            <CopyPlus :size="18" aria-hidden="true" />
            Duplicate
          </Button>
          <Button variant="destructive" :disabled="count === 1" @click="act('remove')">
            <Trash2 :size="18" aria-hidden="true" />
            Remove
          </Button>
        </div>
        <Button variant="secondary" class="w-full" :disabled="count === 1" @click="copyToAll">
          <template v-if="copied">Copied to all {{ count }} rounds</template>
          <template v-else>
            <Copy :size="18" aria-hidden="true" />
            Copy to all rounds
          </template>
        </Button>
      </section>
    </template>

    <template v-else>
      <div class="list-head">
        <Button variant="ghost" size="md" class="px-1" @click="page = 'round'">‹ Round {{ index + 1 }}</Button>
        <div class="all-none">
          <Button variant="secondary" size="sm" @click="setChecked([...allIds])">All</Button>
          <Button variant="secondary" size="sm" @click="setChecked([])">None</Button>
        </div>
      </div>
      <p class="hint">
        {{ checked.length === 0 ? `None checked: the coach calls all ${allIds.length}.` : 'Only the checked ones are called.' }}
      </p>
      <section v-for="group in groups" :key="group.theme" class="group">
        <p v-if="groups.length > 1" class="eyebrow group-name" :style="{ '--theme': THEME_COLORS[group.theme] }">
          {{ THEME_NAMES[group.theme] }}
        </p>
        <ul class="combos">
          <li v-for="combo in group.combos" :key="combo.id" class="combo">
            <button
              class="combo-check"
              role="checkbox"
              :aria-checked="checked.includes(combo.id)"
              @click="toggle(combo.id)"
            >
              <span class="box" :class="{ on: checked.includes(combo.id) }" aria-hidden="true">
                <Check v-if="checked.includes(combo.id)" :size="16" :stroke-width="3.5" />
              </span>
              <span class="combo-text">
                <span class="combo-names">{{ combo.names }}</span>
                <span class="combo-numbers">{{ combo.numbers }}</span>
              </span>
            </button>
            <button class="play" :aria-label="`Hear ${combo.names}`" @click="hear(combo)">
              <Play :size="16" fill="currentColor" aria-hidden="true" />
            </button>
          </li>
        </ul>
      </section>
    </template>

    <template #footer>
      <Button class="w-full" @click="$emit('close')">Done</Button>
    </template>
  </Sheet>
</template>

<style scoped>
.arrows {
  display: flex;
  gap: 8px;
}

.block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.themes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

/* The theme colour as the whole left edge; the chosen one outlined and tinted in it. */
.theme {
  position: relative;
  min-height: 76px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 4px;
  padding: 10px 8px 10px 18px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--card);
  text-align: left;
  transition:
    border-color 120ms ease,
    background-color 120ms ease;
}

.theme::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 9px;
  background: var(--theme);
}

.theme:active {
  transform: scale(0.97);
}

.theme.chosen {
  border-color: var(--theme);
  box-shadow: inset 0 0 0 1px var(--theme);
  background: color-mix(in srgb, var(--theme) 14%, var(--card));
}

.theme-name {
  font-size: var(--fs-sm-plus);
  line-height: 1.2;
  font-weight: 800;
}

.theme-example {
  font-size: var(--fs-xs);
  line-height: 1.25;
  color: var(--muted-foreground);
}

.chosen .theme-example {
  color: var(--foreground);
}

.hint {
  font-size: var(--fs-sm-plus);
  color: var(--muted-foreground);
}

/* Two lines of room, so the sheet does not move when speed and power is switched on. */
.split-hint {
  min-height: 2.8em;
}

.combos-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 52px;
  padding: 0 14px 0 16px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--card);
  font-size: var(--fs-base-plus);
  font-weight: 700;
}

.combos-row:active {
  background: var(--secondary);
}

.calm {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 280px;
  padding: 24px;
  border: 1px dashed var(--border);
  border-radius: 18px;
  text-align: center;
}

.calm-title {
  font-size: var(--fs-md);
  font-weight: 800;
}

.calm-text {
  color: var(--muted-foreground);
}

.listen {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 64px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--card);
  text-align: left;
}

.listen:active {
  background: var(--secondary);
}

.listen-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--theme);
  color: #0b0c0e;
}

.listen-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.listen-label {
  font-size: var(--fs-base-plus);
  font-weight: 800;
}

.listen-combo {
  font-size: var(--fs-sm);
  color: var(--muted-foreground);
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.actions :deep(button) {
  height: 48px;
  font-size: var(--fs-sm-plus);
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -8px;
}

.all-none {
  display: flex;
  gap: 8px;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-name::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: var(--theme);
}

.combos {
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--card);
  list-style: none;
}

.combo {
  display: flex;
  align-items: center;
  padding-right: 8px;
}

.combo + .combo {
  border-top: 1px solid var(--border);
}

.combo-check {
  flex: 1;
  min-width: 0;
  min-height: 60px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 8px 8px 14px;
  text-align: left;
}

.combo-check:active {
  background: var(--secondary);
}

.box {
  flex: none;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 2px solid var(--muted-foreground);
  border-radius: 8px;
}

.box.on {
  border-color: var(--primary);
  background: var(--primary);
  color: var(--primary-foreground);
}

.combo-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.combo-names {
  font-size: var(--fs-base-plus);
  font-weight: 700;
}

.combo-numbers {
  font-size: var(--fs-sm);
  color: var(--muted-foreground);
  font-variant-numeric: tabular-nums;
}

.play {
  flex: none;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--secondary);
}

.play:active {
  transform: scale(0.94);
}

@media (prefers-reduced-motion: reduce) {
  .theme {
    transition: none;
  }
}
</style>
