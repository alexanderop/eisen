<script setup lang="ts">
import { ArrowDown, ArrowUp } from '@lucide/vue'
import { computed } from 'vue'
import { loadFrom, loadIn, WOD_UNITS, type Wod, type WodUnit } from '../domain/wod'
import {
  amountText,
  amountWheel,
  DEFAULT_AMOUNTS,
  loadText,
  loadWheel,
  MAX_HINTS,
  WOD_UNIT_LABELS,
} from '../domain/wodScreens'
import { findExercise, state } from '../store'
import { Button, ChipGroup, ListRow, Sheet, Wheel } from './ui'

/** One movement of the builder's draft. Every change applies at once; "Done" only closes. */
const props = defineProps<{ draft: Wod }>()
const index = defineModel<number>('index', { required: true })
const emit = defineEmits<{ close: [] }>()

const item = computed(() => props.draft.items[index.value])
const exercise = computed(() => (item.value ? findExercise(item.value.exerciseId) : undefined))
const unit = computed(() => state.settings.unit)
const weighted = computed(() => exercise.value !== undefined && exercise.value.equipment !== 'bodyweight')
/** With a scheme the reps come from it, so a reps movement has no amount of its own. */
const byScheme = computed(() => item.value?.unit === 'reps' && props.draft.scheme.length > 0)
/** Max only exists in a Tabata, where the work time is the limit. */
const tabata = computed(() => props.draft.timer.mode === 'tabata')
const max = computed(() => tabata.value && item.value?.amount === 0)

const UNIT_OPTIONS = WOD_UNITS.map((value) => ({ value, label: WOD_UNIT_LABELS[value] }))

function setUnit(value: WodUnit) {
  if (!item.value || item.value.unit === value) return
  item.value.unit = value
  if (item.value.amount > 0) item.value.amount = DEFAULT_AMOUNTS[value]
}

function toggleMax() {
  if (item.value) item.value.amount = max.value ? DEFAULT_AMOUNTS[item.value.unit] : 0
}

/** The load as the sheet opened: it stays on the wheel, also when it is off the wheel's steps. */
const opened = item.value ? loadIn(item.value.load, unit.value) : 0

const load = computed({
  get: () => (item.value ? loadIn(item.value.load, unit.value) : 0),
  set: (value: number) => {
    if (item.value) item.value.load = loadFrom(value, unit.value)
  },
})

function move(direction: 1 | -1) {
  const target = index.value + direction
  const items = props.draft.items
  if (target < 0 || target >= items.length) return
  const [moved] = items.splice(index.value, 1)
  items.splice(target, 0, moved)
  index.value = target
}

function remove() {
  props.draft.items.splice(index.value, 1)
  emit('close')
}
</script>

<template>
  <Sheet v-if="item" :title="exercise?.name ?? 'Movement'" @close="emit('close')">
    <ChipGroup :model-value="item.unit" :options="UNIT_OPTIONS" label="Counted in" @update:model-value="setUnit" />

    <p v-if="byScheme" class="note">The reps follow the scheme: {{ draft.scheme.join('-') }}.</p>
    <div v-else-if="tabata" class="bleed">
      <ListRow label="Max" :description="MAX_HINTS[item.unit]" trailing="switch" :checked="max" @click="toggleMax">
        <template #leading><span /></template>
      </ListRow>
    </div>

    <div class="wheels" :class="{ two: !byScheme && !max && weighted }">
      <Wheel
        v-if="!byScheme && !max"
        :key="`${index}-${item.unit}`"
        v-model="item.amount"
        :values="amountWheel(item.unit, item.amount)"
        :header="WOD_UNIT_LABELS[item.unit]"
        :label="WOD_UNIT_LABELS[item.unit]"
        :format="(value: number) => amountText(item.unit, value)"
      />
      <Wheel
        v-if="weighted"
        :key="`${index}-load`"
        v-model="load"
        :values="loadWheel(exercise?.equipment ?? 'weight', unit, opened)"
        :header="`Load (${unit})`"
        label="Load"
        :format="(value: number) => loadText(value, unit)"
      />
    </div>

    <div v-if="draft.items.length > 1" class="order">
      <Button variant="secondary" :disabled="index === 0" @click="move(-1)">
        <ArrowUp :size="18" aria-hidden="true" />
        Move up
      </Button>
      <Button variant="secondary" :disabled="index === draft.items.length - 1" @click="move(1)">
        <ArrowDown :size="18" aria-hidden="true" />
        Move down
      </Button>
    </div>

    <div class="order">
      <Button variant="destructive" @click="remove">Remove</Button>
      <Button @click="emit('close')">Done</Button>
    </div>
  </Sheet>
</template>

<style scoped>
.bleed {
  margin: -8px -16px;
}

/* No icon, so the label starts where the chips do. */
.bleed :deep(.list-row) {
  gap: 0;
}

.note {
  padding: 12px 14px;
  border-radius: var(--r-lg);
  background: var(--secondary);
  color: var(--muted-foreground);
  font-size: var(--fs-sm-plus);
}

.wheels {
  display: grid;
  grid-template-columns: minmax(0, 220px);
  justify-content: center;
}

.wheels:empty {
  display: none;
}

.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.order {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
</style>
