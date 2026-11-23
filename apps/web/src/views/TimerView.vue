<script setup lang="ts">
import { ref, watch } from 'vue'
import TimersPage from '../components/TimersPage.vue'
import WodsPage from '../components/WodsPage.vue'
import { Segmented } from '../components/ui'

type TimerPage = 'wods' | 'timers'
const PAGE_KEY = 'eisen:timer-page'

function savedPage(): TimerPage {
  try {
    return localStorage.getItem(PAGE_KEY) === 'timers' ? 'timers' : 'wods'
  } catch {
    return 'wods'
  }
}

/** The WODs or the plain timers. It opens on the one picked last. */
const page = ref<TimerPage>(savedPage())
watch(page, (current) => {
  try {
    localStorage.setItem(PAGE_KEY, current)
  } catch {
    // Storage blocked: the switch just starts on the WODs next time.
  }
})
const PAGES = [
  { value: 'wods' as const, label: 'WODs' },
  { value: 'timers' as const, label: 'Timers' },
]
</script>

<template>
  <component :is="page === 'wods' ? WodsPage : TimersPage">
    <h1 class="page-title">Timer</h1>
    <Segmented v-model="page" :options="PAGES" />
  </component>
</template>
