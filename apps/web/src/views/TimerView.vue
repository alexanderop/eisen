<script setup lang="ts">
import { Mic, Play, Timer } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import TimerCalls from '../components/TimerCalls.vue'
import { Button, Card, ConfirmButton, Field, Input, ListRow, Sheet, Wheel } from '../components/ui'
import {
  TIMER_MODES,
  TIMER_NAMES,
  timerSummary,
  type TimerConfig,
  type TimerMode,
  type TimerPreset,
} from '../domain/timer'
import { callsSummary, presetName, TIMER_DESCRIPTIONS, timerWheels, totalText } from '../domain/timerScreens'
import { haptic } from '../haptics'
import { addTimerPreset, deleteTimerPreset, state, updateTimerPreset } from '../store'
import { startTimer } from '../timer'

/**
 * The setup on screen: a copy of the config and of a preset's name, so nothing is stored until it
 * starts or is saved.
 */
const setup = ref<{ config: TimerConfig; presetId: string | null; name: string } | null>(null)

function openMode(mode: TimerMode) {
  setup.value = { config: { ...state.timerConfigs[mode] }, presetId: null, name: '' }
}

function openPreset(preset: TimerPreset) {
  setup.value = { config: { ...preset.config }, presetId: preset.id, name: preset.name }
}

/** The play button of a saved timer: straight into the lead-in, without the setup. */
function startPreset(preset: TimerPreset) {
  haptic('tap')
  startTimer(preset.config, preset.name)
}

watch(
  () => setup.value === null,
  () => window.scrollTo(0, 0),
)

const preset = computed(() => state.timerPresets.find((entry) => entry.id === setup.value?.presetId) ?? null)
const wheels = computed(() => (setup.value ? timerWheels(setup.value.config) : []))
/** The preset on screen differs from the one saved: in its numbers or its name. */
const changed = computed(() => {
  const current = setup.value
  const saved = preset.value
  if (!current || !saved) return false
  const name = current.name.trim()
  return (
    (name !== '' && name !== saved.name) ||
    (['work', 'rest', 'rounds'] as const).some((key) => current.config[key] !== saved.config[key])
  )
})

const voiceOpen = ref(false)
/** The name in the "Save as preset" sheet while it is open. */
const naming = ref<string | null>(null)

function start() {
  if (!setup.value) return
  haptic('tap')
  const name = preset.value ? setup.value.name.trim() || preset.value.name : TIMER_NAMES[setup.value.config.mode]
  startTimer(setup.value.config, name)
}

function save() {
  if (!setup.value || naming.value === null) return
  const saved = addTimerPreset(naming.value, setup.value.config)
  setup.value.presetId = saved.id
  setup.value.name = saved.name
  naming.value = null
  haptic('success')
}

function update() {
  if (!setup.value || !preset.value) return
  updateTimerPreset(preset.value.id, setup.value.name, setup.value.config)
  setup.value.name = preset.value.name
  haptic('success')
}

function onSaveAs() {
  if (!setup.value) return
  naming.value = presetName(
    setup.value.config.mode,
    state.timerPresets.map((entry) => entry.name),
  )
}

function remove() {
  if (preset.value) deleteTimerPreset(preset.value.id)
  setup.value = null
}
</script>

<template>
  <template v-if="setup">
    <Button class="back px-1" variant="ghost" size="md" @click="setup = null">‹ Timer</Button>

    <header class="setup-head">
      <h1 class="page-title">{{ TIMER_NAMES[setup.config.mode] }}</h1>
      <p class="muted">{{ TIMER_DESCRIPTIONS[setup.config.mode] }}</p>
    </header>

    <Field v-if="preset" label="Preset name">
      <Input v-model="setup.name" maxlength="40" enterkeyhint="done" />
    </Field>

    <Card as="section" class="wheels-card">
      <div class="wheels" :class="`count-${wheels.length}`">
        <Wheel
          v-for="wheel in wheels"
          :key="`${setup.config.mode}-${wheel.field}`"
          v-model="setup.config[wheel.field]"
          :values="wheel.values"
          :header="wheel.header"
          :label="wheel.header"
          :format="wheel.format"
          :spoken="wheel.spoken"
        />
      </div>
      <p class="total">{{ totalText(setup.config) }}</p>
    </Card>

    <Card as="section" class="voice">
      <ListRow :icon="Mic" label="Voice" :description="callsSummary(state.settings.timerCalls)" @click="voiceOpen = true" />
    </Card>

    <Button class="w-full start" @click="start">Start</Button>

    <Button v-if="!preset" variant="secondary" class="w-full" @click="onSaveAs">Save as preset</Button>
    <Button v-else variant="secondary" class="w-full" :disabled="!changed" @click="update">
      {{ changed ? 'Update preset' : 'Saved' }}
    </Button>
    <ConfirmButton
      v-if="preset"
      :key="preset.id"
      variant="ghost"
      class="w-full"
      label="Delete preset"
      confirm-label="Tap again to delete"
      @confirm="remove"
    />

    <Sheet v-if="voiceOpen" title="Voice" @close="voiceOpen = false">
      <div class="bleed">
        <TimerCalls />
      </div>
      <Button class="w-full" @click="voiceOpen = false">Done</Button>
    </Sheet>

    <Sheet v-if="naming !== null" title="Save as preset" @close="naming = null">
      <form class="name-form" @submit.prevent="save">
        <Field label="Name">
          <Input v-model="naming" maxlength="40" enterkeyhint="done" />
        </Field>
        <p class="muted hint">Start it again in one tap.</p>
        <Button type="submit" class="w-full" :disabled="naming.trim() === ''">Save</Button>
      </form>
    </Sheet>
  </template>

  <template v-else>
    <h1 class="page-title">Timer</h1>

    <div class="modes">
      <Card v-for="mode in TIMER_MODES" :key="mode" as="button" class="mode" @click="openMode(mode)">
        <strong>{{ TIMER_NAMES[mode] }}</strong>
        <span class="muted description">{{ TIMER_DESCRIPTIONS[mode] }}</span>
        <span class="summary">{{ timerSummary(state.timerConfigs[mode]) }}</span>
      </Card>
    </div>

    <section class="saved">
      <h2 class="eyebrow">Saved timers</h2>
      <p v-if="state.timerPresets.length === 0" class="muted empty">Save a timer to start it again in one tap.</p>
      <Card v-else as="ul" class="presets">
        <li v-for="item in state.timerPresets" :key="item.id" class="preset">
          <button class="preset-open" @click="openPreset(item)">
            <Timer class="preset-icon" :size="24" :stroke-width="1.75" aria-hidden="true" />
            <span class="preset-text">
              <span class="preset-name">{{ item.name }}</span>
              <span class="preset-detail">{{ TIMER_NAMES[item.config.mode] }} · {{ timerSummary(item.config) }}</span>
            </span>
          </button>
          <button class="play" :aria-label="`Start ${item.name}`" @click="startPreset(item)">
            <Play :size="22" :stroke-width="2.5" fill="currentColor" aria-hidden="true" />
          </button>
        </li>
      </Card>
    </section>
  </template>
</template>

<style scoped>
.back {
  align-self: flex-start;
}

.setup-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: -8px;
}

.wheels-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.wheels {
  display: grid;
  gap: 8px;
}

.count-1 {
  grid-template-columns: minmax(0, 200px);
  justify-content: center;
}

.count-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

/* Work goes up to "10:00", rest to "5:00" and rounds to "60": each wheel as wide as its numbers. */
.count-3 {
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1.1fr) minmax(0, 0.8fr);
}

.count-3 :deep(.centre) {
  font-size: min(calc(var(--row) * 0.66), 6.4vw);
}

.total {
  text-align: center;
  font-size: var(--fs-lg);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.voice,
.presets {
  padding: 4px 0;
  overflow: hidden;
}

.presets {
  margin: 0;
  list-style: none;
}

.preset {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-right: 12px;
}

.preset + .preset {
  border-top: 1px solid var(--border);
}

.preset-open {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 64px;
  padding: 10px 4px 10px 16px;
  text-align: left;
}

.preset-open:active {
  background: var(--secondary);
}

.preset-icon {
  flex: none;
}

.preset-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.preset-name {
  font-size: 16px;
  overflow-wrap: anywhere;
}

.preset-detail {
  font-size: 13px;
  color: var(--muted-foreground);
}

.play {
  flex: none;
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--primary);
  color: var(--primary-foreground);
}

.play:active {
  transform: scale(0.94);
}

/* Start stays in reach above the tab bar, also when a preset's name pushes the rest down. */
.start {
  position: sticky;
  bottom: calc(var(--tabs-height) + env(safe-area-inset-bottom) + 12px);
  z-index: 1;
  font-size: var(--fs-md);
  box-shadow: 0 0 0 6px var(--background);
}


.bleed {
  margin: 0 -16px;
}

.name-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hint {
  font-size: var(--fs-sm);
  text-align: center;
}

.modes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.mode {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 132px;
  padding: 14px;
}

.mode strong {
  font-size: var(--fs-lg);
  font-weight: 800;
  letter-spacing: -0.01em;
}

.mode .description {
  flex: 1;
  font-size: var(--fs-sm);
  line-height: 1.3;
}

.mode .summary {
  margin-top: 8px;
  color: var(--primary);
  font-size: var(--fs-base);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.mode:active {
  transform: scale(0.98);
}

.saved {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.empty {
  font-size: var(--fs-sm-plus);
}
</style>
