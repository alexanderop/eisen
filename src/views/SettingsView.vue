<script setup lang="ts">
import { computed, ref } from 'vue'
import WeightStepper from '../components/WeightStepper.vue'
import { EXERCISE_NAMES, UNIT_DEFAULTS, WEIGHTED_IDS } from '../domain/program'
import { fmt } from '../format'
import { formatClock, stopRest } from '../rest'
import { exportData, importData, resetAll, setProgressWeight, setUnit, state } from '../store'

const defaults = computed(() => UNIT_DEFAULTS[state.settings.unit])
const message = ref('')
const confirmReset = ref(false)

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

function onReset() {
  if (!confirmReset.value) {
    confirmReset.value = true
    return
  }
  stopRest()
  resetAll()
  confirmReset.value = false
  message.value = 'All data erased.'
}
</script>

<template>
  <h1 class="page-title">Settings</h1>

  <section class="card group">
    <h2>Units</h2>
    <div class="segmented">
      <button :aria-pressed="state.settings.unit === 'kg'" :disabled="!!state.active" @click="setUnit('kg')">
        Kilograms
      </button>
      <button :aria-pressed="state.settings.unit === 'lb'" :disabled="!!state.active" @click="setUnit('lb')">
        Pounds
      </button>
    </div>
    <p v-if="state.active" class="muted small">Finish the current workout to change units.</p>
    <div class="row">
      <span>Bar weight</span>
      <WeightStepper
        v-model="state.settings.bar"
        :step="defaults.step"
        label="bar weight"
        :format="(value: number) => fmt(value)"
      />
    </div>
  </section>

  <section class="card group">
    <h2>Working weights</h2>
    <div v-for="id in WEIGHTED_IDS" :key="id" class="row">
      <span>{{ EXERCISE_NAMES[id] }}</span>
      <WeightStepper
        :model-value="state.progress[id].weight"
        :step="defaults.step"
        :min="defaults.step"
        :label="`${EXERCISE_NAMES[id]} weight`"
        :format="(value: number) => fmt(value)"
        @update:model-value="setProgressWeight(id, $event)"
      />
    </div>
  </section>

  <section class="card group">
    <h2>Rest timer</h2>
    <div class="row">
      <span>After a full set</span>
      <WeightStepper
        v-model="state.settings.restDone"
        :step="15"
        :min="15"
        label="rest after a full set"
        :format="formatClock"
      />
    </div>
    <div class="row">
      <span>After missed reps</span>
      <WeightStepper
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
  </section>

  <section class="card group">
    <h2>Your data</h2>
    <p class="muted small">Everything is stored on this device only.</p>
    <button class="btn" @click="download">Download backup</button>
    <label class="btn">
      Restore backup
      <input type="file" accept="application/json" hidden @change="onImport" />
    </label>
    <button class="btn btn-danger" @click="onReset">
      {{ confirmReset ? 'Tap again to erase everything' : 'Erase all data' }}
    </button>
    <p v-if="message" class="small" role="status">{{ message }}</p>
  </section>
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
  accent-color: var(--accent);
}

label.btn {
  cursor: pointer;
}
</style>
