<script setup lang="ts">
import { applyUpdate, build, checkForUpdate, checkResult, updateReady } from '../pwa'
import { Button, Card, ConfirmButton, Segmented, Stepper } from '../components/ui'
import { computed, ref } from 'vue'
import { stepFor, UNIT_DEFAULTS } from '../domain/program'
import type { Unit } from '../domain/units'
import { fmt } from '../format'
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
  latest: 'You have the latest version.',
  downloading: 'Downloading the new version…',
  offline: 'Could not check. Are you online?',
  unsupported: 'Updates need the installed app or a supported browser.',
} as const

function onReset() {
  stopRest()
  resetAll()
  message.value = 'All data erased.'
}
</script>

<template>
  <h1 class="page-title">Settings</h1>

  <Card as="section" class="group">
    <h2>Units</h2>
    <Segmented
      :model-value="state.settings.unit"
      :options="UNITS"
      :disabled="!!state.active"
      @update:model-value="setUnit"
    />
    <p v-if="state.active" class="muted small">Finish the current workout to change units.</p>
    <div class="row">
      <span>Bar weight</span>
      <Stepper
        v-model="state.settings.bar"
        :step="defaults.step"
        label="bar weight"
        :format="(value: number) => fmt(value)"
      />
    </div>
  </Card>

  <Card as="section" class="group">
    <h2>Working weights</h2>
    <p v-if="weighted.length === 0" class="muted small">No weighted exercises in your workouts.</p>
    <div v-for="exercise in weighted" :key="exercise.id" class="row">
      <span>{{ exercise.name }}</span>
      <Stepper
        :model-value="progressOf(exercise.id).weight"
        :step="stepFor(exercise.equipment, state.settings.unit)"
        :min="stepFor(exercise.equipment, state.settings.unit)"
        :label="`${exercise.name} weight`"
        :format="(value: number) => fmt(value)"
        @update:model-value="setProgressWeight(exercise.id, $event)"
      />
    </div>
  </Card>

  <Card as="section" class="group">
    <h2>Rest timer</h2>
    <div class="row">
      <span>After a full set</span>
      <Stepper
        v-model="state.settings.restDone"
        :step="15"
        :min="15"
        label="rest after a full set"
        :format="formatClock"
      />
    </div>
    <div class="row">
      <span>After missed reps</span>
      <Stepper
        v-model="state.settings.restMissed"
        :step="30"
        :min="30"
        label="rest after missed reps"
        :format="formatClock"
      />
    </div>
    <label class="row">
      <span>Sound when rest ends</span>
      <input v-model="state.settings.sound" type="checkbox" />
    </label>
  </Card>

  <Card as="section" class="group">
    <h2>Your data</h2>
    <p class="muted small">Everything is stored on this device only.</p>
    <Button variant="secondary" class="w-full" @click="download">Download backup</Button>
    <Button variant="secondary" class="w-full" as="label">
      Restore backup
      <input type="file" accept="application/json" hidden @change="onImport" />
    </Button>
    <ConfirmButton class="w-full"
     
      label="Erase all data"
      confirm-label="Tap again to erase everything"
      @confirm="onReset"
    />
    <p v-if="message" class="small" role="status">{{ message }}</p>
  </Card>

  <Card as="section" class="group">
    <h2>About</h2>
    <div class="row">
      <span>Version</span>
      <strong>{{ build.version }}</strong>
    </div>
    <div class="row">
      <span>Build</span>
      <span class="muted">{{ build.commit }} · {{ builtAt }}</span>
    </div>
    <template v-if="updateReady">
      <p class="small" role="status">A new version is ready.</p>
      <Button class="w-full" @click="applyUpdate">Update now</Button>
    </template>
    <template v-else>
      <Button
        variant="secondary"
        class="w-full"
        :disabled="checkResult === 'checking'"
        @click="checkForUpdate"
      >
        {{ checkResult === 'checking' ? 'Checking…' : 'Check for updates' }}
      </Button>
      <p v-if="checkResult && checkResult !== 'checking'" class="muted small" role="status">
        {{ CHECK_TEXT[checkResult] }}
      </p>
    </template>
  </Card>
</template>

<style scoped>
.group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

h2 {
  font-size: 17px;
  font-weight: 700;
}

.small {
  font-size: 13px;
}

input[type='checkbox'] {
  width: 24px;
  height: 24px;
  accent-color: var(--primary);
}
</style>
