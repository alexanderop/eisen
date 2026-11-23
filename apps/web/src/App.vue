<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import HoldTimer from './components/HoldTimer.vue'
import RestScreen from './components/RestScreen.vue'
import TimerScreen from './components/TimerScreen.vue'
import UpdatePrompt from './components/UpdatePrompt.vue'
import { sharedFromHash } from './domain/share'
import { hold } from './hold'
import { minimiseRestScreen, restScreenOpen } from './rest'
import { state } from './store'
import { timer } from './timer'
import HistoryView from './views/HistoryView.vue'
import HomeView from './views/HomeView.vue'
import SettingsView from './views/SettingsView.vue'
import SharedWorkoutView from './views/SharedWorkoutView.vue'
import TimerView from './views/TimerView.vue'
import WorkoutView from './views/WorkoutView.vue'
import WorkoutsView from './views/WorkoutsView.vue'

const TABS = [
  { id: 'home', label: 'Home', icon: 'M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z' },
  {
    id: 'workouts',
    label: 'Workouts',
    icon: 'M2 10h2v4H2zM5 7h3v10H5zM9 11h6v2H9zM16 7h3v10h-3zM20 10h2v4h-2z',
  },
  {
    id: 'timer',
    label: 'Timer',
    icon: 'M9.5 2h5v2.5h-5zM12 5.5a8 8 0 1 0 0 16a8 8 0 1 0 0-16zm0 2.75a5.25 5.25 0 1 1 0 10.5a5.25 5.25 0 1 1 0-10.5zM10.9 9.5h2.2v4.6h-2.2z',
  },
  { id: 'history', label: 'History', icon: 'M5 4h14v3H5zM5 10.5h14v3H5zM5 17h14v3H5z' },
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
const showRest = computed(() => showWorkout.value && restScreenOpen.value)
/** A running interval timer or its result covers everything, the workout included. */
const showTimer = computed(() => timer.run !== null || timer.result !== null)
const timerScreen = ref<InstanceType<typeof TimerScreen>>()
/** "Done" on a timer's result leads to the timer cards, not back into the setup it came from. */
const timerHome = ref(0)

function onTimerDone() {
  tab.value = 'timer'
  timerHome.value += 1
}

function isWorkoutEntry(): boolean {
  return (history.state as { workout?: boolean } | null)?.workout === true
}

function isRestEntry(): boolean {
  return (history.state as { rest?: boolean } | null)?.rest === true
}

function isTimerEntry(): boolean {
  return (history.state as { timer?: boolean } | null)?.timer === true
}

/** On top of whatever it covers, so the back button reaches the timer first. */
function pushTimerEntry() {
  history.pushState({ ...(history.state as object | null), timer: true }, '')
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

/** The rest screen gets one on top, so the back button minimises it and leaves the workout open. */
watch(
  showRest,
  (open) => {
    if (open && !isRestEntry()) history.pushState({ workout: true, rest: true }, '')
    else if (!open && isRestEntry()) history.back()
  },
  { immediate: true },
)

/** The timer gets one on top of everything: the back button asks before it ends a run. */
watch(
  showTimer,
  (open) => {
    if (open && !isTimerEntry()) pushTimerEntry()
    else if (!open && isTimerEntry()) history.back()
  },
  { immediate: true },
)

function onPopState() {
  if (showTimer.value && !isTimerEntry()) {
    // The timer stays on screen while it asks, so it keeps its entry.
    pushTimerEntry()
    timerScreen.value?.back()
    return
  }
  minimiseRestScreen()
  // Back from the rest screen's entry lands on the workout's: the workout stays.
  if (!isWorkoutEntry()) workoutOpen.value = false
}

/** A link to a shared workout carries the workout after the `#`. It shows before anything else. */
const shared = ref(sharedFromHash(location.hash))

function onHashChange() {
  shared.value = sharedFromHash(location.hash)
}

function closeShared() {
  shared.value = null
  history.replaceState(history.state, '', location.pathname + location.search)
}

onMounted(() => {
  window.addEventListener('popstate', onPopState)
  window.addEventListener('hashchange', onHashChange)
})
onBeforeUnmount(() => {
  window.removeEventListener('popstate', onPopState)
  window.removeEventListener('hashchange', onHashChange)
})

/** Every screen starts at the top instead of inheriting the last screen's scroll position. */
watch([tab, showWorkout, shared], () => window.scrollTo(0, 0), { flush: 'post' })
</script>

<template>
  <!-- Behind a running timer nothing is reachable, not by keyboard and not by a screen reader. -->
  <div class="behind" :inert="showTimer">
    <SharedWorkoutView v-if="shared" :shared="shared" @close="closeShared" />
    <WorkoutView v-else-if="showWorkout" @close="workoutOpen = false" />
    <template v-else>
      <main class="page">
        <HomeView v-if="tab === 'home'" @open="workoutOpen = true" />
        <WorkoutsView v-else-if="tab === 'workouts'" />
        <TimerView v-else-if="tab === 'timer'" :key="timerHome" />
        <HistoryView v-else-if="tab === 'history'" />
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
            <path :d="item.icon" fill="currentColor" fill-rule="evenodd" />
          </svg>
          {{ item.label }}
        </button>
      </nav>
      <!-- Not during a workout: an update can wait until the last set is done. -->
      <UpdatePrompt />
    </template>
    <RestScreen v-if="showRest" />
    <!-- Above every screen: a hold keeps running when the workout is closed with the back button. -->
    <HoldTimer v-if="hold.active" />
  </div>
  <!-- Above the hold and the rest: starting a timer ends both, and none can start under it. -->
  <TimerScreen v-if="showTimer" ref="timerScreen" @done="onTimerDone" />
</template>

<style scoped>
.behind {
  display: contents;
}

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
  grid-template-columns: repeat(5, 1fr);
  background: rgb(11 12 14 / 0.92);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--border);
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
  color: var(--muted-foreground);
}

.tabs button[aria-current='page'] {
  color: var(--primary);
}
</style>
