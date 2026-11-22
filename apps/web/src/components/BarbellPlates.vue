<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ plates: number[]; largest: number }>()

const sized = computed(() =>
  props.plates.map((plate) => ({
    plate,
    height: 26 + Math.sqrt(plate / props.largest) * 54,
    width: plate / props.largest > 0.3 ? 22 : 18,
  })),
)
</script>

<template>
  <div class="barbell" aria-hidden="true">
    <div class="sleeve">
      <div
        v-for="(item, index) in sized"
        :key="index"
        class="plate"
        :style="{ height: `${item.height}px`, width: `${item.width}px` }"
      >
        <span class="plate-label">{{ item.plate }}</span>
      </div>
    </div>
    <div class="collar" />
    <div class="shaft" />
  </div>
</template>

<style scoped>
.barbell {
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: row-reverse;
}

.shaft {
  width: 96px;
  height: 8px;
  background: var(--muted-foreground);
  border-radius: 4px 0 0 4px;
}

.collar {
  width: 8px;
  height: 22px;
  background: var(--muted-foreground);
  border-radius: 2px;
}

.sleeve {
  min-width: 96px;
  height: 12px;
  background: var(--border);
  border-radius: 0 4px 4px 0;
  display: flex;
  align-items: center;
  gap: 2px;
  padding-left: 2px;
}

.plate {
  flex: none;
  background: var(--primary);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

/* Reads bottom to top, like the print on a real plate seen from the side. */
.plate-label {
  writing-mode: vertical-rl;
  transform: rotate(180deg);
  color: var(--primary-foreground);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
</style>
