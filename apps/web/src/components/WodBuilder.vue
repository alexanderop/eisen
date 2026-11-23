<script setup lang="ts">
import { ChevronRight, Hash, Plus, Repeat, Timer } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { TIMER_NAMES, WOD_MODES, type TimerMode } from '../domain/timer'
import { roundsWheel, timerWheels, type TimerField } from '../domain/timerScreens'
import { findWod, MAX_WOD_ITEMS, SCHEMES, wod, wodName } from '../domain/wod'
import {
  itemSummary,
  newItem,
  parseScheme,
  SCHEME_LABELS,
  schemeChoice,
  switchMode,
  type WodEdit,
} from '../domain/wodScreens'
import { findExercise, state } from '../store'
import { leaveBuilder, saveEdit, wods } from '../wods'
import ExerciseImage from './ExerciseImage.vue'
import ExercisePicker from './ExercisePicker.vue'
import { Button, Card, ChipGroup, Field, Input, ListRow, Segmented, Sheet, Wheel } from './ui'
import WodCard from './WodCard.vue'
import WodItemSheet from './WodItemSheet.vue'

/**
 * The WOD builder: one page from name to preview. It edits a draft; nothing is stored until Save.
 * The draft itself is kept in storage as it changes, see `wods.ts`.
 */
const props = defineProps<{ edit: WodEdit }>()
const draft = computed(() => props.edit.draft)

const isNew = computed(() => !state.wods.some((entry) => entry.id === draft.value.id))
const backLabel = computed(() => {
  const back = props.edit.back
  const target = back.kind === 'detail' ? findWod(state.wods, back.id) : undefined
  return target ? wodName(target) : 'WODs'
})

const MODES = WOD_MODES.map((mode) => ({ value: mode, label: TIMER_NAMES[mode] }))

function setMode(mode: TimerMode) {
  const next = switchMode(draft.value, props.edit.memory, mode)
  Object.assign(props.edit.draft, next.draft)
  props.edit.memory = next.memory
}

const forTime = computed(() => draft.value.timer.mode === 'forTime')

/** One number of the type: a row that opens its wheel in a sheet. */
interface NumberRow {
  id: TimerField | 'wodRounds'
  header: string
  values: number[]
  format: (value: number) => string
  spoken?: (value: number) => string
  value: number
  set: (value: number) => void
}

const timerRows = computed<NumberRow[]>(() =>
  timerWheels(draft.value.timer).map((wheel) => ({
    id: wheel.field,
    header: wheel.header,
    values: wheel.values,
    format: wheel.format,
    spoken: wheel.spoken,
    value: draft.value.timer[wheel.field],
    set: (value: number) => (props.edit.draft.timer[wheel.field] = value),
  })),
)

const roundsRow = computed<NumberRow>(() => ({
  id: 'wodRounds',
  header: 'Rounds',
  values: roundsWheel(draft.value.rounds),
  format: String,
  spoken: (value) => `${value} rounds`,
  value: draft.value.rounds,
  set: (value: number) => (props.edit.draft.rounds = value),
}))

const openId = ref<NumberRow['id'] | null>(null)
const openRow = computed(() => [roundsRow.value, ...timerRows.value].find((row) => row.id === openId.value) ?? null)

const SCHEME_OPTIONS = [
  { value: 'same', label: 'Same each round' },
  ...SCHEME_LABELS.map((label, index) => ({ value: String(index), label })),
  { value: 'custom', label: 'Custom' },
]

/** The scheme chip on. Kept apart from the scheme, so "Custom" stays on while its field is empty. */
const choice = ref(schemeChoice(draft.value.scheme))
const custom = ref(choice.value === 'custom' ? draft.value.scheme.join('-') : '')

function setChoice(value: string) {
  choice.value = value
  if (value === 'same') props.edit.draft.scheme = []
  else if (value === 'custom') props.edit.draft.scheme = parseScheme(custom.value)
  else props.edit.draft.scheme = [...SCHEMES[Number(value)]]
}

watch(custom, (text) => {
  if (choice.value === 'custom') props.edit.draft.scheme = parseScheme(text)
})

// Back on For Time, the scheme it had comes back with it.
watch(
  () => draft.value.timer.mode,
  () => {
    choice.value = schemeChoice(draft.value.scheme)
    custom.value = choice.value === 'custom' ? draft.value.scheme.join('-') : ''
  },
)

const unit = computed(() => state.settings.unit)

const picking = ref(false)
/** The movement whose editor sheet is open. */
const editing = ref<number | null>(null)

/** Several movements at once, in the order they were tapped. Each row is there to tap and set. */
function onPick(ids: string[]) {
  picking.value = false
  for (const id of ids) {
    const exercise = findExercise(id)
    if (exercise && props.edit.draft.items.length < MAX_WOD_ITEMS) {
      props.edit.draft.items.push(newItem(exercise, draft.value.timer.mode))
    }
  }
}

const preview = computed(() => wod(draft.value))
</script>

<template>
  <Button class="back px-1" variant="ghost" size="md" @click="leaveBuilder()">‹ {{ backLabel }}</Button>
  <h1 class="page-title title">{{ isNew ? 'New WOD' : 'Edit WOD' }}</h1>

  <Field label="Name">
    <Input v-model="draft.name" maxlength="40" placeholder="Name your WOD" enterkeyhint="done" />
  </Field>

  <Field label="Type" as="div">
    <Segmented :model-value="draft.timer.mode" :options="MODES" @update:model-value="setMode" />
  </Field>

  <Card as="section" class="numbers">
    <template v-if="forTime">
      <ListRow
        v-if="draft.scheme.length > 0"
        :icon="Hash"
        label="Scheme"
        :value="draft.scheme.join('-')"
        trailing="none"
        as="div"
      />
      <ListRow v-else :icon="Repeat" label="Rounds" :value="String(draft.rounds)" @click="openId = 'wodRounds'" />
      <div class="schemes">
        <p class="eyebrow" aria-hidden="true">Reps per round</p>
        <ChipGroup :model-value="choice" :options="SCHEME_OPTIONS" label="Reps per round" bleed @update:model-value="setChoice" />
        <Input
          v-if="choice === 'custom'"
          v-model="custom"
          inputmode="numeric"
          placeholder="30-20-10"
          aria-label="Reps per round, separated by dashes"
          enterkeyhint="done"
        />
      </div>
    </template>
    <ListRow
      v-for="row in timerRows"
      :key="row.id"
      :icon="row.id === 'rounds' ? Repeat : Timer"
      :label="row.header"
      :value="row.format(row.value)"
      @click="openId = row.id"
    />
  </Card>

  <section class="group">
    <h2 class="eyebrow">Movements</h2>
    <Card v-if="draft.items.length > 0" as="ul" class="items">
      <li v-for="(item, index) in draft.items" :key="`${index}-${item.exerciseId}`">
        <button class="item" @click="editing = index">
          <ExerciseImage
            :image="findExercise(item.exerciseId)?.image"
            :name="findExercise(item.exerciseId)?.name ?? '?'"
          />
          <span class="item-text">
            <strong>{{ findExercise(item.exerciseId)?.name ?? 'Deleted exercise' }}</strong>
            <span class="muted">{{ itemSummary(draft, item, unit) }}</span>
          </span>
          <ChevronRight class="chevron" :size="18" aria-hidden="true" />
        </button>
      </li>
    </Card>
    <button v-if="draft.items.length < MAX_WOD_ITEMS" class="add" @click="picking = true">
      <Plus :size="20" :stroke-width="2.5" aria-hidden="true" />
      Add movement
    </button>
  </section>

  <section class="group">
    <h2 class="eyebrow">Preview</h2>
    <WodCard :workout="preview" preview />
  </section>

  <div class="dock">
    <Button class="w-full save" :disabled="draft.items.length === 0" @click="saveEdit">
      {{ draft.items.length === 0 ? 'Add a movement to save' : 'Save' }}
    </Button>
  </div>

  <Sheet v-if="openRow" :title="openRow.header" @close="openId = null">
    <div class="wheel-box">
      <Wheel
        :model-value="openRow.value"
        :values="openRow.values"
        :header="openRow.header"
        :label="openRow.header"
        :format="openRow.format"
        :spoken="openRow.spoken"
        @update:model-value="openRow.set"
      />
    </div>
    <Button class="w-full" @click="openId = null">Done</Button>
  </Sheet>

  <WodItemSheet v-if="editing !== null" v-model:index="editing" :draft="edit.draft" @close="editing = null" />

  <ExercisePicker
    v-if="picking"
    v-model:filter="wods.picker.filter"
    v-model:query="wods.picker.query"
    :exclude="[]"
    multiple
    @pick-many="onPick"
    @close="picking = false"
  />

  <Sheet v-if="wods.discarding" title="Discard changes?" @close="wods.discarding = null">
    <p class="muted">What you changed in this WOD is not saved.</p>
    <Button variant="destructive" class="w-full" @click="leaveBuilder(true, wods.discarding === 'top')">Discard</Button>
    <Button variant="secondary" class="w-full" @click="wods.discarding = null">Keep editing</Button>
  </Sheet>
</template>

<style scoped>
.back {
  align-self: flex-start;
}

.title {
  margin-top: -8px;
}

.numbers {
  padding: 4px 0;
  overflow: hidden;
}

.numbers > :deep(.list-row + .list-row),
.schemes + :deep(.list-row) {
  border-top: 1px solid var(--border);
}

.schemes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 16px 12px;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.items {
  margin: 0;
  padding: 0;
  list-style: none;
  overflow: hidden;
}

li + li {
  border-top: 1px solid var(--border);
}

.item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 64px;
  padding: 10px 12px;
  text-align: left;
}

.item:active {
  background: var(--secondary);
}

.item-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.item-text strong {
  font-size: var(--fs-base-plus);
}

.item-text .muted {
  font-size: var(--fs-sm);
}

.chevron {
  flex: none;
  color: var(--muted-foreground);
}

.add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: var(--control-lg);
  border: 2px dashed var(--border);
  border-radius: var(--r-xl);
  color: var(--primary);
  font-weight: 700;
}

.add:active {
  background: var(--secondary);
}

.save {
  font-size: var(--fs-md);
}

.wheel-box {
  display: grid;
  grid-template-columns: minmax(0, 240px);
  justify-content: center;
}

.wheel-box :deep(.header) {
  display: none;
}
</style>
