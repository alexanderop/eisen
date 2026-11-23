<script setup lang="ts">
import { Plus } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { findWod, WOD_LIBRARY, wodName, type Wod } from '../domain/wod'
import { filterWods, noMatchText, WOD_FILTERS } from '../domain/wodScreens'
import { searchText, searchWods } from '../domain/wodSearch'
import { haptic } from '../haptics'
import { findExercise, state } from '../store'
import { startWod } from '../timer'
import {
  closeDetail,
  continueEdit,
  copyWod,
  dropEdit,
  editWod,
  newWod,
  openDetail,
  syncEntry,
  wods,
} from '../wods'
import { Button, Card, ChipGroup } from './ui'
import WodBuilder from './WodBuilder.vue'
import WodCard from './WodCard.vue'
import WodDetail from './WodDetail.vue'
import WodSearch from './WodSearch.vue'

/** The chips sit over both lists, so the search and its suggestions see what the page shows. */
const mine = computed(() => filterWods(state.wods, wods.filter, findExercise))
const benchmarks = computed(() => filterWods(WOD_LIBRARY, wods.filter, findExercise))
const shown = computed(() => [...mine.value, ...benchmarks.value])
const searching = computed(() => searchText(wods.query) !== '')
/** While searching one list over both, best match first. */
const results = computed(() => searchWods(shown.value, wods.query, findExercise))

/** A suggested WOD opens; the search is done with, so the list is whole again on the way back. */
function openSuggestion(id: string) {
  wods.query = ''
  openDetail(id)
}

const detail = computed(() =>
  wods.screen.kind === 'detail' ? (findWod(state.wods, wods.screen.id) ?? null) : null,
)

// A WOD deleted elsewhere, or a stale screen after an import: back to the list.
watch(
  () => wods.screen.kind === 'detail' && !detail.value,
  (gone) => {
    if (gone) closeDetail()
  },
  { immediate: true },
)

// A detail or the builder opens at the top; back on the list it stands where it was left.
watch(
  () => wods.screen.kind,
  (kind) => window.scrollTo(0, kind === 'list' ? wods.listScroll : 0),
  { flush: 'post' },
)

onMounted(() => {
  wods.mounted = true
  syncEntry()
})
onBeforeUnmount(() => (wods.mounted = false))

function start(workout: Wod) {
  haptic('tap')
  startWod(workout)
}
</script>

<template>
  <WodBuilder v-if="wods.screen.kind === 'builder' && wods.edit" :edit="wods.edit" />

  <WodDetail
    v-else-if="detail"
    :workout="detail"
    @back="closeDetail"
    @edit="editWod(detail)"
    @copy="copyWod(detail)"
    @start="start(detail)"
  />

  <template v-else>
    <slot />

    <!-- Pinned while the list scrolls. The chips sit above the field, so its suggestions never cover them. -->
    <div class="finder wod-finder">
      <ChipGroup v-model="wods.filter" :options="WOD_FILTERS" label="Show" bleed />
      <WodSearch v-model="wods.query" :wods="shown" @open="openSuggestion" />
    </div>

    <Card v-if="wods.edit" as="section" class="draft">
      <p class="draft-text">
        Continue editing <strong>{{ wodName(wods.edit.draft) }}</strong>
      </p>
      <div class="draft-actions">
        <Button variant="secondary" @click="dropEdit">Discard</Button>
        <Button @click="continueEdit">Continue</Button>
      </div>
    </Card>

    <Button variant="secondary" class="w-full new" @click="newWod">
      <Plus :size="20" :stroke-width="2.5" aria-hidden="true" />
      New WOD
    </Button>

    <section v-if="searching" class="group" aria-label="Search results">
      <WodCard
        v-for="item in results"
        :key="item.id"
        :workout="item"
        @open="openDetail(item.id)"
        @start="start(item)"
      />
      <div v-if="results.length === 0" class="none">
        <p>{{ noMatchText(wods.query, wods.filter) }}</p>
        <div class="none-actions">
          <Button v-if="wods.filter !== 'all'" variant="secondary" @click="wods.filter = 'all'">Show all</Button>
          <Button variant="secondary" @click="wods.query = ''">Clear search</Button>
        </div>
      </div>
    </section>

    <template v-else>
      <section v-if="mine.length > 0" class="group">
        <h2 class="eyebrow">My WODs</h2>
        <WodCard
          v-for="item in mine"
          :key="item.id"
          :workout="item"
          @open="openDetail(item.id)"
          @start="start(item)"
        />
      </section>

      <section class="group">
        <h2 class="eyebrow">Benchmarks</h2>
        <WodCard
          v-for="item in benchmarks"
          :key="item.id"
          :workout="item"
          @open="openDetail(item.id)"
          @start="start(item)"
        />
      </section>
    </template>
  </template>
</template>

<style scoped>
.new {
  font-size: var(--fs-md);
}

.group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.finder {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: -8px -16px 0;
  padding: calc(env(safe-area-inset-top) + 8px) 16px 8px;
  background: var(--background);
}

.none-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.none {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 0;
  color: var(--muted-foreground);
  font-size: var(--fs-base-plus);
  text-align: center;
  overflow-wrap: anywhere;
}

.draft {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-color: color-mix(in srgb, var(--primary) 45%, var(--border));
}

.draft-text {
  font-size: var(--fs-base-plus);
  overflow-wrap: anywhere;
}

.draft-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
</style>

<style>
/* What scrolls into view, by focus or by a link, stops below the pinned search, not under it. */
html:has(.wod-finder) {
  scroll-padding-top: 150px;
}
</style>
