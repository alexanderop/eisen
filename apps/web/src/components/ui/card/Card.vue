<script setup lang="ts">
import type { PrimitiveProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { cn } from 'cn'
import { Primitive } from 'reka-ui'

/** A raised surface that groups one topic. `as="button"` makes the whole card tappable. */
const props = withDefaults(defineProps<PrimitiveProps & { class?: HTMLAttributes['class'] }>(), {
  as: 'div',
})
</script>

<template>
  <Primitive
    data-slot="card"
    :as="as"
    :as-child="asChild"
    :class="cn(
      'bg-card text-card-foreground border-border rounded-[18px] border p-4',
      as === 'button' && 'w-full text-left',
      props.class,
    )"
  >
    <slot />
  </Primitive>
</template>
