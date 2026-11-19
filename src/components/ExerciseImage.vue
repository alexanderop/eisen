<script setup lang="ts">
import { computed } from 'vue'

/**
 * The picture of an exercise. The images are dark equipment on a transparent background,
 * so they always sit on a pale surface. `tile` is a small square for lists,
 * `banner` a wide header for a card. Without an image a tile shows the first letter
 * and a banner renders nothing.
 */
const props = withDefaults(
  defineProps<{
    /** File name in `src/assets/exercises`, without extension. */
    image?: string
    /** Exercise name, for the letter shown when there is no image. */
    name: string
    variant?: 'tile' | 'banner'
    /** Edge length of a tile in px. */
    size?: number
  }>(),
  { variant: 'tile', size: 44 },
)

const urls = import.meta.glob<string>('../assets/exercises/*.webp', {
  eager: true,
  import: 'default',
})

const src = computed(() =>
  props.image ? urls[`../assets/exercises/${props.image}.webp`] : undefined,
)
</script>

<template>
  <span v-if="variant === 'banner' && src" class="banner">
    <img :src="src" alt="" />
  </span>
  <span
    v-else-if="variant === 'tile'"
    class="tile"
    :class="{ empty: !src }"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <img v-if="src" :src="src" alt="" loading="lazy" />
    <template v-else>{{ name.charAt(0).toUpperCase() }}</template>
  </span>
</template>

<style scoped>
.tile,
.banner {
  flex: none;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--tile);
}

.tile {
  border-radius: 10px;
}

.tile img {
  width: 88%;
  height: 88%;
  object-fit: contain;
}

.empty {
  background: var(--surface-2);
  color: var(--muted);
  font-weight: 700;
}

.banner {
  height: 168px;
}

.banner img {
  height: 160px;
  width: 160px;
  object-fit: contain;
}
</style>
