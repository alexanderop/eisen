<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { state } from './store'
import HistoryView from './views/HistoryView.vue'
import HomeView from './views/HomeView.vue'
import ProgressView from './views/ProgressView.vue'
import SettingsView from './views/SettingsView.vue'
import WorkoutView from './views/WorkoutView.vue'

const TABS = [
  { id: 'home', label: 'Home', icon: 'M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z' },
  { id: 'history', label: 'History', icon: 'M5 4h14v3H5zM5 10.5h14v3H5zM5 17h14v3H5z' },
  { id: 'progress', label: 'Progress', icon: 'M4 20V10h3.5v10zM10.25 20V4h3.5v16zM16.5 20v-7H20v7z' },
  {
    id: 'settings',
    label: 'Settings',
    icon: 'M4 6h9v2H4zM17 6h3v2h-3zM13 4.5h3v5h-3zM4 16h3v2H4zM11 16h9v2h-9zM7 14.5h3v5H7z',
  },
] as const

type Tab = (typeof TABS)[number]['id']

const tab = ref<Tab>('home')
/** Reopen an unfinished workout after a reload or after the phone closed the app. */
const workoutOpen = ref(state.active !== null)
const showWorkout = computed(() => workoutOpen.value && state.active !== null)

function isWorkoutEntry(): boolean {
  return (history.state as { workout?: boolean } | null)?.workout === true
}

/** The workout gets its own history entry so the phone's back button closes it, not the app. */
watch(
  showWorkout,
  (open) => {
    if (open && !isWorkoutEntry()) history.pushState({ workout: true }, '')
    else if (!open && isWorkoutEntry()) history.back()
  },
  { immediate: true },
)

function onPopState() {
  workoutOpen.value = false
}

onMounted(() => window.addEventListener('popstate', onPopState))
onBeforeUnmount(() => window.removeEventListener('popstate', onPopState))

/** Every screen starts at the top instead of inheriting the last screen's scroll position. */
watch([tab, showWorkout], () => window.scrollTo(0, 0), { flush: 'post' })
</script>

<template>
  <WorkoutView v-if="showWorkout" @close="workoutOpen = false" />
  <template v-else>
    <main class="page">
      <HomeView v-if="tab === 'home'" @open="workoutOpen = true" />
      <HistoryView v-else-if="tab === 'history'" />
      <ProgressView v-else-if="tab === 'progress'" />
      <SettingsView v-else />
    </main>
    <nav class="tabs" aria-label="Main">
      <button
        v-for="item in TABS"
        :key="item.id"
        :aria-current="tab === item.id ? 'page' : undefined"
        @click="tab = item.id"
      >
        <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
          <path :d="item.icon" fill="currentColor" />
        </svg>
        {{ item.label }}
      </button>
    </nav>
  </template>
</template>

<style scoped>
.tabs {
  position: fixed;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  height: calc(var(--tabs-height) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: rgb(11 12 14 / 0.92);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--line);
  z-index: 10;
}

.tabs button {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
}

.tabs button[aria-current='page'] {
  color: var(--accent);
}
</style>
