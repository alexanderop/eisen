<script setup lang="ts">
import { computed } from 'vue'
import { THEME_NAMES } from '../domain/boxing'
import { planOf, roundDetail, timeline } from '../domain/boxingScreens'
import { holdText } from '../domain/program'
import type { TimerConfig } from '../domain/timer'
import { totalText } from '../domain/timerScreens'

/**
 * The whole boxing workout to scale: a block per round in its theme colour, the rests as thin dark
 * lines between them, and a split round in lighter and darker stretches. With `select` a tap on a
 * round opens it.
 */
const props = defineProps<{ config: TimerConfig; selectable?: boolean }>()
const emit = defineEmits<{ select: [index: number] }>()

const blocks = computed(() => timeline(props.config))
const plan = computed(() => planOf(props.config))
const many = computed(() => props.config.rounds > 8)
</script>

<template>
  <div class="timeline" :class="{ many }">
    <div class="strip">
      <template v-for="block in blocks" :key="`${block.kind}-${block.index}`">
        <component
          :is="selectable ? 'button' : 'span'"
          v-if="block.kind === 'work'"
          class="round"
          :style="{ flexGrow: block.seconds, '--theme': block.color }"
          :aria-label="
            selectable
              ? `Round ${block.index + 1}, ${THEME_NAMES[block.theme]}, ${roundDetail(plan.rounds[block.index])}`
              : undefined
          "
          :aria-hidden="selectable ? undefined : 'true'"
          @click="selectable && emit('select', block.index)"
        >
          <span
            v-for="(stretch, at) in block.stretches"
            :key="at"
            class="stretch"
            :class="stretch.focus"
            :style="{ flexGrow: stretch.share }"
          />
          <span v-if="!many" class="number" aria-hidden="true">{{ block.index + 1 }}</span>
        </component>
        <span v-else class="rest" :style="{ flexGrow: block.seconds }" aria-hidden="true" />
      </template>
    </div>
    <p class="total">
      <span class="sr-only">{{ config.rounds }} {{ config.rounds === 1 ? 'round' : 'rounds' }} of {{ holdText(config.work) }}. </span>{{ totalText(config) }}
    </p>
  </div>
</template>

<style scoped>
.timeline {
  display: flex;
  align-items: center;
  gap: 12px;
}

.strip {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
  height: 44px;
}

.round {
  position: relative;
  container-type: inline-size;
  flex-basis: 0;
  min-width: 6px;
  display: flex;
  overflow: hidden;
  border-radius: 8px;
  background: var(--theme);
}

button.round:active {
  filter: brightness(0.85);
}

button.round:focus-visible {
  outline: 2px solid var(--foreground);
  outline-offset: 2px;
}

/* Speed lighter, power darker: the stretches read as stripes in the round's own colour. */
.stretch {
  flex-basis: 0;
}

.stretch.speed {
  background: color-mix(in srgb, var(--theme) 55%, white);
}

.stretch.power {
  background: color-mix(in srgb, var(--theme) 62%, black);
}

.number {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: rgb(0 0 0 / 0.62);
  font-size: var(--fs-sm-plus);
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}

/* A rest: as long as it lasts, but only a thin dark line, so the rounds stand out. */
.rest {
  flex-basis: 0;
  min-width: 3px;
  align-self: center;
  height: 4px;
  margin: 0 2px;
  border-radius: 2px;
  background: var(--border);
}

/* A round too narrow for its number goes without it. */
@container (max-width: 17px) {
  .number {
    display: none;
  }
}

.many .round {
  border-radius: 5px;
}

.many .rest {
  min-width: 2px;
  margin: 0 1px;
}

.total {
  flex: none;
  font-size: var(--fs-base);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
