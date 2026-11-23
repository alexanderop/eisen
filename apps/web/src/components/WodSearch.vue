<script setup lang="ts">
import { Dumbbell, Search, X } from '@lucide/vue'
import { computed, ref, useTemplateRef } from 'vue'
import type { Wod } from '../domain/wod'
import { typedParts } from '../domain/wodScreens'
import { suggestWods, type WodSuggestion } from '../domain/wodSearch'
import { findExercise } from '../store'
import { Input } from './ui'

/**
 * The search field of the WODs page, with suggestions under it while it has focus and text:
 * a WOD opens, a movement becomes the search. The page filters by the text as it is typed.
 * `wods` are those the page shows under its chips, so a suggestion is always there to find.
 */
const query = defineModel<string>({ required: true })
const props = defineProps<{ wods: Wod[] }>()
const emit = defineEmits<{ open: [id: string] }>()

const field = useTemplateRef<InstanceType<typeof Input>>('field')
const focused = ref(false)
/** Closed by the keyboard's search action, Escape or a movement picked, until the next keystroke. */
const closed = ref(false)
/** The suggestion the arrow keys are on. */
const active = ref(-1)

const suggestions = computed(() => suggestWods(props.wods, query.value, findExercise))
const open = computed(() => focused.value && !closed.value && suggestions.value.length > 0)

function input(): HTMLInputElement | undefined {
  return field.value?.$el as HTMLInputElement | undefined
}

function onInput() {
  closed.value = false
  active.value = -1
}

function onKeydown(event: KeyboardEvent) {
  const count = suggestions.value.length
  if (event.key === 'Escape') {
    closed.value = true
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    if (count === 0) return
    event.preventDefault()
    closed.value = false
    const step = event.key === 'ArrowDown' ? 1 : -1
    active.value = active.value === -1 && step === -1 ? count - 1 : (active.value + step + count) % count
    return
  }
  if (event.key === 'Enter' && open.value && active.value >= 0) {
    event.preventDefault()
    choose(suggestions.value[active.value])
  }
}

function choose(suggestion: WodSuggestion) {
  if (suggestion.kind === 'wod') {
    done()
    emit('open', suggestion.id)
    return
  }
  query.value = suggestion.text
  done()
}

/** The keyboard's search action: the list closes, the search stays. */
function done() {
  closed.value = true
  active.value = -1
  input()?.blur()
}

function clear() {
  query.value = ''
  active.value = -1
  input()?.focus()
}

const optionId = (index: number) => `wod-suggestion-${index}`
</script>

<template>
  <form class="search" role="search" @submit.prevent="done">
    <Search class="icon" :size="20" aria-hidden="true" />
    <Input
      ref="field"
      v-model="query"
      class="field"
      type="search"
      enterkeyhint="search"
      autocomplete="off"
      placeholder="Search WODs or movements"
      aria-label="Search WODs"
      role="combobox"
      aria-autocomplete="list"
      aria-controls="wod-suggestions"
      :aria-expanded="open"
      :aria-activedescendant="open && active >= 0 ? optionId(active) : undefined"
      @input="onInput"
      @keydown="onKeydown"
      @focus="focused = true"
      @blur="focused = false"
    />
    <button v-if="query" type="button" class="clear" aria-label="Clear search" @pointerdown.prevent @click="clear">
      <X :size="20" aria-hidden="true" />
    </button>

    <ul v-show="open" id="wod-suggestions" class="suggestions" role="listbox" aria-label="Suggestions">
      <li
        v-for="(suggestion, index) in suggestions"
        :id="optionId(index)"
        :key="`${suggestion.kind}-${suggestion.id}`"
        class="option"
        :class="{ active: index === active }"
        role="option"
        :aria-selected="index === active"
        @pointerdown.prevent
        @click="choose(suggestion)"
      >
        <Dumbbell v-if="suggestion.kind === 'movement'" class="kind" :size="18" aria-hidden="true" />
        <span class="text">
          <template v-for="(part, at) in typedParts(suggestion.text, query)" :key="at">
            <strong v-if="at === 1">{{ part }}</strong>
            <template v-else>{{ part }}</template>
          </template>
        </span>
        <span class="detail">{{ suggestion.detail }}</span>
      </li>
    </ul>
  </form>
</template>

<style scoped>
.search {
  position: relative;
}

.icon {
  position: absolute;
  top: 14px;
  left: 14px;
  color: var(--muted-foreground);
  pointer-events: none;
}

.field {
  padding-left: 44px;
  padding-right: 44px;
  font-size: var(--fs-base-plus);
}

/* The field has its own clear button. */
.field::-webkit-search-cancel-button {
  appearance: none;
}

.clear {
  position: absolute;
  top: 0;
  right: 0;
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  color: var(--muted-foreground);
}

.suggestions {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 20;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--popover);
  box-shadow: 0 12px 32px rgb(0 0 0 / 0.5);
}

.option {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 14px;
  cursor: pointer;
  font-size: var(--fs-base-plus);
  font-weight: 500;
}

.option.active,
.option:active {
  background: var(--secondary);
}

.kind {
  flex: none;
  color: var(--primary);
}

.text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.text strong {
  font-weight: 900;
}

.detail {
  flex: none;
  max-width: 50%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--muted-foreground);
  font-size: var(--fs-sm-plus);
}
</style>
