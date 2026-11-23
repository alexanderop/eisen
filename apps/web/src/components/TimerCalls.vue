<script setup lang="ts">
/**
 * What the voice says during an interval timer: one setting for every timer.
 * Shown in the voice sheet of a timer's setup and in Settings. The rows run edge to edge.
 * For boxing only what a round has is shown: the coach calls the combinations, always.
 */
import { AudioLines, Contrast, Hourglass, Repeat, Volume2 } from '@lucide/vue'
import { RadioGroupItem, RadioGroupRoot } from 'reka-ui'
import { computed, useId } from 'vue'
import { MINUTE_CALLS, type TimerCalls } from '../domain/timer'
import { MINUTE_CALL_LABELS } from '../domain/timerScreens'
import { haptic } from '../haptics'
import { state } from '../store'
import { playSample } from '../timer'
import { ListRow } from './ui'

const props = defineProps<{ boxing?: boolean }>()

const calls = computed(() => state.settings.timerCalls)

const MINUTE_OPTIONS = MINUTE_CALLS.map((minutes) => ({ value: String(minutes), label: MINUTE_CALL_LABELS[minutes] }))

const minutes = computed({
  get: () => String(calls.value.minutes),
  set: (value: string) => {
    calls.value.minutes = Number(value) as TimerCalls['minutes']
    haptic('tap')
  },
})

const titleId = useId()

const minutesHint = computed(() => {
  const every = calls.value.minutes
  if (every === 0) return 'The minutes left are not called.'
  return every === 1 ? '“Four minutes left”, every minute.' : `“Four minutes left”, every ${every} minutes.`
})

type Switch = Exclude<keyof TimerCalls, 'minutes'>

const ALL_SWITCHES: { key: Switch; label: string; description: string; icon: typeof Contrast }[] = [
  { key: 'halfway', label: 'Halfway', description: 'At half of the whole timer.', icon: Contrast },
  { key: 'final', label: 'Last 30 and 10 seconds', description: 'Before the timer ends.', icon: Hourglass },
  { key: 'phases', label: 'Rounds and rest', description: '“Round three”, “Rest”, “Last round”.', icon: Repeat },
  { key: 'beeps', label: '3-2-1 beeps', description: 'Into every change of phase.', icon: AudioLines },
]

const switches = computed(() =>
  props.boxing
    ? ALL_SWITCHES.filter((item) => item.key !== 'halfway').map((item) =>
        item.key === 'final' ? { ...item, description: 'Before every round ends.' } : item,
      )
    : ALL_SWITCHES,
)

function toggle(key: Switch) {
  calls.value[key] = !calls.value[key]
  haptic('tap')
}
</script>

<template>
  <div class="calls">
    <p v-if="boxing" class="coach">
      The coach always calls the combinations. Time left and halfway are not called in a boxing round.
    </p>
    <div v-else class="minutes">
      <p :id="titleId" class="label">Time left, every</p>
      <RadioGroupRoot v-model="minutes" class="choices" orientation="horizontal" loop :aria-labelledby="titleId">
        <RadioGroupItem v-for="option in MINUTE_OPTIONS" :key="option.value" :value="option.value" class="choice">
          {{ option.label }}
        </RadioGroupItem>
      </RadioGroupRoot>
      <p class="hint">{{ minutesHint }}</p>
    </div>
    <ListRow
      v-for="item in switches"
      :key="item.key"
      :icon="item.icon"
      :label="item.label"
      :description="item.description"
      trailing="switch"
      :checked="calls[item.key]"
      @click="toggle(item.key)"
    />
    <p class="applies">Applies to all timers.</p>
    <ListRow
      :icon="Volume2"
      label="Play sample"
      description="Says “Halfway”, so you can set the volume."
      tone="accent"
      trailing="none"
      @click="playSample"
    />
  </div>
</template>

<style scoped>
.coach {
  padding: 0 16px 6px;
  font-size: var(--fs-sm-plus);
  color: var(--muted-foreground);
}

.minutes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 16px 10px;
}

.label {
  font-size: 16px;
}

.hint,
.applies {
  font-size: var(--fs-sm);
  color: var(--muted-foreground);
}

.applies {
  padding: 8px 16px 0;
}

/* Looks like the app's segmented switch, but is a radio group: one of four, arrow keys move. */
.choices {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  height: 44px;
  padding: 4px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--card);
}

.choice {
  border-radius: 9px;
  font-size: var(--fs-sm-plus);
  font-weight: 600;
  color: var(--muted-foreground);
}

.choice[data-state='checked'] {
  background: var(--secondary);
  color: var(--foreground);
}
</style>
