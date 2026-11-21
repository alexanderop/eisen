<script setup lang="ts">
import {
  Check,
  CircleArrowUp,
  Download,
  GitCommitHorizontal,
  Hourglass,
  RefreshCw,
  Scale,
  Tag,
  Timer,
  Trash2,
  Upload,
  Vibrate,
  Volume2,
  Weight,
} from '@lucide/vue'
import { applyUpdate, build, checkForUpdate, checkResult, updateReady } from '../pwa'
import { Button, ListRow, Sheet, Stepper } from '../components/ui'
import { computed, ref } from 'vue'
import ExerciseImage from '../components/ExerciseImage.vue'
import { stepFor, UNIT_DEFAULTS } from '../domain/program'
import type { Unit } from '../domain/units'
import { fmt } from '../format'
import { haptic, hapticsSupported } from '../haptics'
import { formatClock, stopRest } from '../rest'
import {
  exportData,
  findExercise,
  importData,
  progressOf,
  resetAll,
  setProgressWeight,
  setUnit,
  state,
} from '../store'

const defaults = computed(() => UNIT_DEFAULTS[state.settings.unit])
/** Every weighted exercise that is part of a workout, once. */
const weighted = computed(() => {
  const ids = new Set(state.routines.flatMap((routine) => routine.slots.map((slot) => slot.exerciseId)))
  return [...ids].flatMap((id) => {
    const exercise = findExercise(id)
    return exercise && exercise.equipment !== 'bodyweight' ? [exercise] : []
  })
})
const UNITS: { value: Unit; label: string }[] = [
  { value: 'kg', label: 'Kilograms' },
  { value: 'lb', label: 'Pounds' },
]
const unitLabel = computed(() => UNITS.find((unit) => unit.value === state.settings.unit)!.label)

/** What the open sheet changes: a fixed setting, or the working weight of one exercise. */
type Editing = 'unit' | 'bar' | 'restDone' | 'restMissed' | 'erase' | { exerciseId: string }
const editing = ref<Editing | null>(null)

interface NumberEditor {
  title: string
  label: string
  hint: string
  value: number
  step: number
  min: number
  format: (value: number) => string
  set: (value: number) => void
}

/** The number the open sheet steps through. */
const numberEditor = computed<NumberEditor | null>(() => {
  const target = editing.value
  const weight = (value: number) => fmt(value)
  if (target === 'bar') {
    return {
      title: 'Bar weight',
      label: 'bar weight',
      hint: 'The empty bar. Plates and warmups are worked out from it.',
      value: state.settings.bar,
      step: defaults.value.step,
      min: 0,
      format: weight,
      set: (value) => (state.settings.bar = value),
    }
  }
  if (target === 'restDone') {
    return {
      title: 'Rest after a full set',
      label: 'rest after a full set',
      hint: 'The timer starts when you log a set with all reps.',
      value: state.settings.restDone,
      step: 15,
      min: 15,
      format: formatClock,
      set: (value) => (state.settings.restDone = value),
    }
  }
  if (target === 'restMissed') {
    return {
      title: 'Rest after missed reps',
      label: 'rest after missed reps',
      hint: 'A longer rest gives the next set a fair chance.',
      value: state.settings.restMissed,
      step: 30,
      min: 30,
      format: formatClock,
      set: (value) => (state.settings.restMissed = value),
    }
  }
  if (typeof target !== 'object' || target === null) return null
  const exercise = findExercise(target.exerciseId)
  if (!exercise) return null
  const step = stepFor(exercise.equipment, state.settings.unit)
  return {
    title: exercise.name,
    label: `${exercise.name} weight`,
    hint: 'The weight your next workout starts with. Changing it clears missed attempts.',
    value: progressOf(exercise.id).weight,
    step,
    min: step,
    format: weight,
    set: (value) => setProgressWeight(exercise.id, value),
  }
})

function pickUnit(unit: Unit) {
  setUnit(unit)
  haptic('tap')
  editing.value = null
}

function toggle(setting: 'sound' | 'haptics') {
  state.settings[setting] = !state.settings[setting]
  haptic('tap')
}

const message = ref('')

function download() {
  const url = URL.createObjectURL(new Blob([exportData()], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `eisen-backup-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(url)
  message.value = 'Backup downloaded.'
}

async function onImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const restored = importData(await file.text())
  if (restored) stopRest()
  message.value = restored
    ? 'Backup restored.'
    : 'That file is not an Eisen backup. Nothing was changed.'
  input.value = ''
}

const builtAt = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
  build.builtAt,
)

const CHECK_TEXT = {
  checking: 'Checking…',
  latest: 'You have the latest version.',
  downloading: 'Downloading the new version…',
  offline: 'Could not check. Are you online?',
  unsupported: 'Updates need the installed app or a supported browser.',
} as const

function onReset() {
  stopRest()
  resetAll()
  editing.value = null
  message.value = 'All data erased.'
}
</script>

<template>
  <h1 class="page-title">Settings</h1>

  <div class="list">
    <section>
      <h2>Weights</h2>
      <ListRow
        :icon="Scale"
        label="Unit"
        :description="state.active ? 'Finish the current workout to change it.' : undefined"
        :value="unitLabel"
        :disabled="!!state.active"
        @click="editing = 'unit'"
      />
      <ListRow :icon="Weight" label="Bar weight" :value="fmt(state.settings.bar)" @click="editing = 'bar'" />
    </section>

    <section>
      <h2>Working weights</h2>
      <p v-if="weighted.length === 0" class="note">No weighted exercises in your workouts.</p>
      <ListRow
        v-for="exercise in weighted"
        :key="exercise.id"
        :label="exercise.name"
        :value="fmt(progressOf(exercise.id).weight)"
        @click="editing = { exerciseId: exercise.id }"
      >
        <template #leading>
          <ExerciseImage :image="exercise.image" :name="exercise.name" :size="36" />
        </template>
      </ListRow>
    </section>

    <section>
      <h2>Rest timer</h2>
      <ListRow
        :icon="Timer"
        label="After a full set"
        :value="formatClock(state.settings.restDone)"
        @click="editing = 'restDone'"
      />
      <ListRow
        :icon="Hourglass"
        label="After missed reps"
        :value="formatClock(state.settings.restMissed)"
        @click="editing = 'restMissed'"
      />
      <ListRow
        :icon="Volume2"
        label="Sound when rest ends"
        trailing="switch"
        :checked="state.settings.sound"
        @click="toggle('sound')"
      />
      <ListRow
        v-if="hapticsSupported"
        :icon="Vibrate"
        label="Vibrate on taps"
        trailing="switch"
        :checked="state.settings.haptics"
        @click="toggle('haptics')"
      />
    </section>

    <section>
      <h2>Your data</h2>
      <ListRow
        :icon="Download"
        label="Download backup"
        description="Everything is stored on this device only."
        trailing="none"
        @click="download"
      />
      <ListRow :icon="Upload" label="Restore backup" as="label" trailing="none">
        <input type="file" accept="application/json" hidden @change="onImport" />
      </ListRow>
      <ListRow
        :icon="Trash2"
        label="Erase all data"
        tone="destructive"
        trailing="none"
        @click="editing = 'erase'"
      />
      <p v-if="message" class="note" role="status">{{ message }}</p>
    </section>

    <section>
      <h2>About</h2>
      <ListRow
        v-if="updateReady"
        :icon="CircleArrowUp"
        label="Update now"
        description="A new version is ready. Your data stays."
        tone="accent"
        @click="applyUpdate"
      />
      <ListRow
        v-else
        :icon="RefreshCw"
        label="Check for updates"
        :description="checkResult ? CHECK_TEXT[checkResult] : undefined"
        :disabled="checkResult === 'checking'"
        trailing="none"
        @click="checkForUpdate"
      />
      <ListRow :icon="Tag" label="Version" :value="build.version" as="div" trailing="none" />
      <ListRow
        :icon="GitCommitHorizontal"
        label="Build"
        :description="builtAt"
        :value="build.commit"
        as="div"
        trailing="none"
      />
    </section>

    <p class="footer">Eisen 5×5</p>
  </div>

  <Sheet v-if="editing === 'unit'" title="Unit" @close="editing = null">
    <div class="options">
      <button
        v-for="unit in UNITS"
        :key="unit.value"
        class="option"
        role="radio"
        :aria-checked="unit.value === state.settings.unit"
        @click="pickUnit(unit.value)"
      >
        <span>{{ unit.label }}</span>
        <Check v-if="unit.value === state.settings.unit" :size="22" :stroke-width="2.5" aria-hidden="true" />
      </button>
    </div>
    <p class="muted hint">Your weights are converted and rounded to what the plates can load.</p>
  </Sheet>

  <Sheet v-if="numberEditor" :title="numberEditor.title" @close="editing = null">
    <div class="number">
      <Stepper
        :model-value="numberEditor.value"
        :step="numberEditor.step"
        :min="numberEditor.min"
        :label="numberEditor.label"
        :format="numberEditor.format"
        @update:model-value="numberEditor.set($event)"
      />
    </div>
    <p class="muted hint">{{ numberEditor.hint }}</p>
    <Button class="w-full" @click="editing = null">Done</Button>
  </Sheet>

  <Sheet v-if="editing === 'erase'" title="Erase all data?" @close="editing = null">
    <p class="muted">
      This deletes every workout, your weights and your settings from this device. It cannot be undone.
      Download a backup first if you might want them back.
    </p>
    <Button variant="destructive" class="w-full" @click="onReset">Erase everything</Button>
    <Button variant="secondary" class="w-full" @click="editing = null">Cancel</Button>
  </Sheet>
</template>

<style scoped>
/* Rows run to the screen edge, out of the page's 16px padding. */
.list {
  margin: 0 -16px;
}

section {
  padding: 10px 0;
  border-top: 6px solid var(--card);
}

section:first-child {
  border-top: 0;
  padding-top: 0;
}

h2 {
  padding: 10px 16px 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--muted-foreground);
}

.note {
  padding: 6px 16px;
  font-size: 13px;
  color: var(--muted-foreground);
}

.footer {
  padding: 28px 16px 8px;
  border-top: 6px solid var(--card);
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--muted-foreground);
}

.options {
  display: flex;
  flex-direction: column;
}

.option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 0 4px;
  font-size: 17px;
  text-align: left;
  border-bottom: 1px solid var(--border);
}

.option:last-child {
  border-bottom: 0;
}

.option svg {
  color: var(--primary);
}

.number {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}

.number :deep(output) {
  min-width: 132px;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.hint {
  font-size: 13px;
  text-align: center;
}
</style>
