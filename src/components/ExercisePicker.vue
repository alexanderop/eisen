<script setup lang="ts">
import ExerciseImage from './ExerciseImage.vue'
import { Button, ChipGroup, ConfirmButton, Field, Input, Sheet } from './ui'
import { Check, Plus } from '@lucide/vue'
import { computed, ref } from 'vue'
import {
  EQUIPMENT,
  EQUIPMENT_LABELS,
  GROUPS,
  kindLabel,
  type Equipment,
  type Exercise,
  type Group,
} from '../domain/exercises'
import { recentExerciseIds } from '../domain/records'
import { addCustomExercise, deleteCustomExercise, exercises, state } from '../store'

const props = defineProps<{ exclude: string[] }>()
const emit = defineEmits<{ pick: [id: string]; close: [] }>()

const query = ref('')
const creating = ref(false)
const name = ref('')
const equipment = ref<Equipment>('barbell')

const ORDER: Group[] = ['Custom', ...GROUPS]
const GROUP_TITLES: Partial<Record<Group, string>> = { Custom: 'Your exercises' }

type Filter = Group | 'All'
const filter = ref<Filter>('All')
const filters = computed(() =>
  (['All', ...ORDER] as Filter[])
    .filter((group) => group !== 'Custom' || exercises.value.some((e) => e.group === 'Custom'))
    .map((group) => ({ value: group, label: group === 'Custom' ? 'Yours' : group })),
)

interface Section {
  key: string
  title: string
  /** Recent exercises come from every group, so their rows name it. */
  showGroup: boolean
  items: Exercise[]
}

const RECENT_LIMIT = 5

/** What the lifter trained last and has not added yet. Only on the unfiltered, unsearched list. */
const recent = computed<Exercise[]>(() => {
  if (filter.value !== 'All' || query.value.trim() !== '') return []
  return recentExerciseIds(state.history)
    .filter((id) => !props.exclude.includes(id))
    .slice(0, RECENT_LIMIT)
    .map((id) => exercises.value.find((exercise) => exercise.id === id))
    .filter((exercise) => exercise !== undefined)
})

const sections = computed<Section[]>(() => {
  const needle = query.value.trim().toLowerCase()
  // The lifter's own exercises stay listed even when already added, so they can be deleted here.
  const available = exercises.value.filter(
    (exercise) =>
      (exercise.group === 'Custom' || !props.exclude.includes(exercise.id)) &&
      (filter.value === 'All' || exercise.group === filter.value) &&
      exercise.name.toLowerCase().includes(needle),
  )
  const groups = ORDER.map((group) => ({
    key: group,
    title: GROUP_TITLES[group] ?? group,
    showGroup: false,
    items: available.filter((exercise) => exercise.group === group),
  }))
  return [{ key: 'recent', title: 'Recent', showGroup: true, items: recent.value }, ...groups].filter(
    (section) => section.items.length > 0,
  )
})

function subtitle(exercise: Exercise, showGroup: boolean): string {
  const kind = kindLabel(exercise)
  return showGroup ? `${kind} · ${GROUP_TITLES[exercise.group] ?? exercise.group}` : kind
}

function startCreating() {
  name.value = query.value.trim()
  creating.value = true
}

function create() {
  const exercise = addCustomExercise(name.value, equipment.value)
  if (exercise) emit('pick', exercise.id)
}

</script>

<template>
  <Sheet :title="creating ? 'New exercise' : 'Add exercise'" @close="emit('close')">
    <form v-if="creating" class="form" @submit.prevent="create">
      <Field label="Name">
        <Input v-model="name" maxlength="40" placeholder="Cable Lateral Raise" />
      </Field>
      <Field label="Type" as="div">
        <div class="types">
          <button
            v-for="option in EQUIPMENT"
            :key="option"
            type="button"
            :aria-pressed="equipment === option"
            @click="equipment = option"
          >
            {{ EQUIPMENT_LABELS[option] }}
          </button>
        </div>
      </Field>
      <Button class="w-full" :disabled="name.trim() === ''">Create and add</Button>
      <Button variant="secondary" class="w-full" type="button" @click="creating = false">Back to the list</Button>
    </form>

    <template v-if="!creating" #action>
      <Button size="sm" variant="secondary" class="rounded-full" @click="startCreating">
        <Plus />
        New
      </Button>
    </template>

    <template v-if="!creating">
      <Input v-model="query" type="search" placeholder="Search exercises" aria-label="Search exercises" />
      <ChipGroup v-model="filter" class="filters" :options="filters" label="Muscle group" bleed />
      <div class="list">
        <section v-for="section in sections" :key="section.key">
          <h3 class="eyebrow">{{ section.title }}</h3>
          <div v-for="exercise in section.items" :key="exercise.id" class="item">
            <button
              class="pick"
              :disabled="exclude.includes(exercise.id)"
              @click="emit('pick', exercise.id)"
            >
              <ExerciseImage :image="exercise.image" :name="exercise.name" />
              <span class="pick-text">
                <span class="pick-name">{{ exercise.name }}</span>
                <span class="muted kind">{{ subtitle(exercise, section.showGroup) }}</span>
              </span>
              <span v-if="exclude.includes(exercise.id)" class="muted state">
                <Check aria-hidden="true" />
                Added
              </span>
              <Plus v-else class="add" aria-hidden="true" />
            </button>
            <ConfirmButton
              v-if="exercise.group === 'Custom'"
              variant="ghost"
              size="md"
              label="Delete"
              confirm-label="Confirm"
              @confirm="deleteCustomExercise(exercise.id)"
            />
          </div>
        </section>
        <div v-if="sections.length === 0" class="none">
          <p class="muted">No exercise matches.</p>
          <Button variant="secondary" class="w-full" @click="startCreating">
            <Plus />
            {{ query.trim() ? `Create “${query.trim()}”` : 'Create your own exercise' }}
          </Button>
        </div>
      </div>
    </template>
  </Sheet>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.types {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.types button {
  min-height: 48px;
  border-radius: 12px;
  background: var(--secondary);
  color: var(--muted-foreground);
  font-weight: 600;
  font-size: 14px;
}

.types button[aria-pressed='true'] {
  background: var(--primary);
  color: var(--primary-foreground);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
  /* The sheet scrolls. A shrinking list would spill over what follows it. */
  flex: none;
}

.filters {
  flex: none;
}

.item {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--border);
}

.pick {
  flex: 1;
  min-width: 0;
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
}

.pick-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pick-name {
  font-weight: 600;
}

.kind {
  font-size: 13px;
}

.state {
  flex: none;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.state svg {
  width: 16px;
  height: 16px;
}

.add {
  flex: none;
  width: 20px;
  height: 20px;
  color: var(--primary);
}

.none {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px 0;
  text-align: center;
}
</style>
