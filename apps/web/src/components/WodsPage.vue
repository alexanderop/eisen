<script setup lang="ts">
import { Plus } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { findWod, WOD_LIBRARY, wodName, type Wod } from '../domain/wod'
import { filterWods, WOD_FILTERS } from '../domain/wodScreens'
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

const benchmarks = computed(() => filterWods(WOD_LIBRARY, wods.filter, findExercise))

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

watch(
  () => wods.screen.kind,
  () => window.scrollTo(0, 0),
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

    <section v-if="state.wods.length > 0" class="group">
      <h2 class="eyebrow">My WODs</h2>
      <WodCard
        v-for="item in state.wods"
        :key="item.id"
        :workout="item"
        @open="openDetail(item.id)"
        @start="start(item)"
      />
    </section>

    <section class="group">
      <h2 class="eyebrow">Benchmarks</h2>
      <ChipGroup v-model="wods.filter" :options="WOD_FILTERS" label="Show" bleed />
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
