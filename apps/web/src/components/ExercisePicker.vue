<script setup lang="ts">
import ExerciseImage from './ExerciseImage.vue'
import { Button, ChipGroup, ConfirmButton, Field, Input, Sheet } from './ui'
import { computed, ref } from 'vue'
import {
  EQUIPMENT,
  EQUIPMENT_LABELS,
  GROUPS,
  kindLabel,
  type Equipment,
  type Group,
} from '../domain/exercises'
import { addCustomExercise, deleteCustomExercise, exercises } from '../store'

const props = defineProps<{ exclude: string[] }>()
const emit = defineEmits<{ pick: [id: string]; close: [] }>()

const query = ref('')
const creating = ref(false)
const name = ref('')
const equipment = ref<Equipment>('barbell')
const timed = ref(false)

const ORDER: Group[] = ['Custom', ...GROUPS]
const GROUP_TITLES: Partial<Record<Group, string>> = { Custom: 'Your exercises' }

type Filter = Group | 'All'
const filter = ref<Filter>('All')
const filters = computed(() =>
  (['All', ...ORDER] as Filter[])
    .filter((group) => group !== 'Custom' || exercises.value.some((e) => e.group === 'Custom'))
    .map((group) => ({ value: group, label: group === 'Custom' ? 'Yours' : group })),
)

const sections = computed(() => {
  const needle = query.value.trim().toLowerCase()
  // The lifter's own exercises stay listed even when already added, so they can be deleted here.
  const available = exercises.value.filter(
    (exercise) =>
      (exercise.group === 'Custom' || !props.exclude.includes(exercise.id)) &&
      (filter.value === 'All' || exercise.group === filter.value) &&
      exercise.name.toLowerCase().includes(needle),
  )
  return ORDER.map((group) => ({
    group,
    title: GROUP_TITLES[group] ?? group,
    items: available.filter((exercise) => exercise.group === group),
  })).filter((section) => section.items.length > 0)
})

function startCreating() {
  name.value = query.value.trim()
  creating.value = true
}

function create() {
  const exercise = addCustomExercise(name.value, equipment.value, timed.value)
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
      <button
        type="button"
        class="timed"
        role="switch"
        :aria-checked="timed"
        aria-describedby="timed-hint"
        @click="timed = !timed"
      >
        <span class="timed-text">
          <strong>Timed</strong>
          <span id="timed-hint" class="muted">Held for time, like a plank</span>
        </span>
        <span class="track" aria-hidden="true"><span class="thumb" /></span>
      </button>
      <Button class="w-full" :disabled="name.trim() === ''">Create and add</Button>
      <Button variant="secondary" class="w-full" type="button" @click="creating = false">Back to the list</Button>
    </form>

    <template v-else>
      <Input v-model="query" type="search" placeholder="Search exercises" aria-label="Search exercises" />
      <ChipGroup v-model="filter" class="filters" :options="filters" label="Muscle group" bleed />
      <Button variant="secondary" class="create w-full" @click="startCreating">
        {{
          query.trim() && sections.length === 0
            ? `Create “${query.trim()}”`
            : 'Create your own exercise'
        }}
      </Button>
      <div class="list">
        <section v-for="section in sections" :key="section.group">
          <h3 class="eyebrow">{{ section.title }}</h3>
          <div v-for="exercise in section.items" :key="exercise.id" class="item">
            <button
              class="pick"
              :disabled="exclude.includes(exercise.id)"
              @click="emit('pick', exercise.id)"
            >
              <ExerciseImage :image="exercise.image" :name="exercise.name" />
              <span class="pick-name">{{ exercise.name }}</span>
              <span class="muted kind">
                {{
                  exclude.includes(exercise.id)
                    ? 'Added'
                    : `${kindLabel(exercise)}${exercise.timed ? ' · Timed' : ''}`
                }}
              </span>
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
        <p v-if="sections.length === 0" class="muted none">No exercise matches.</p>
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

.timed {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  text-align: left;
}

.timed-text {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.timed-text .muted {
  font-size: 13px;
}

.track {
  flex: none;
  width: 52px;
  height: 32px;
  padding: 3px;
  border-radius: var(--r-full);
  background: var(--secondary);
  transition: background-color 0.15s ease;
}

.thumb {
  display: block;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--muted-foreground);
  transition: transform 0.15s ease;
}

.timed[aria-checked='true'] .track {
  background: var(--primary);
}

.timed[aria-checked='true'] .thumb {
  background: var(--primary-foreground);
  transform: translateX(20px);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
  /* The sheet scrolls. A shrinking list would spill over what follows it. */
  flex: none;
}

.create,
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
  min-height: 60px;
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  font-weight: 600;
}

.pick-name {
  flex: 1;
}

.kind {
  flex: none;
  font-size: 13px;
  font-weight: 400;
}

.none {
  padding: 24px 0;
  text-align: center;
}
</style>
