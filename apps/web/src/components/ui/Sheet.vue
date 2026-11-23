<script setup lang="ts">
import { Button } from './button'
import { Drawer, DrawerClose, DrawerContent, DrawerHeader, DrawerTitle } from './drawer'

/**
 * A dialog that rises from the bottom edge, in reach of the thumb.
 * Mount it to open it. It closes on a swipe down, the backdrop, the close button and Escape.
 * The `leading` slot puts something before the title, like a picture of what it is about, and
 * `actions` puts buttons between the title and the close button. `footer` stays pinned under the
 * part that scrolls.
 */
defineProps<{
  title: string
  /** Always as tall as a sheet gets, so what it shows can change without the sheet jumping. */
  tall?: boolean
}>()
const emit = defineEmits<{ close: [] }>()

function onOpen(open: boolean) {
  if (!open) emit('close')
}
</script>

<template>
  <Drawer :open="true" @update:open="onOpen">
    <DrawerContent
      :aria-describedby="undefined"
      class="mx-auto max-w-[480px] text-base data-[swipe-direction=down]:max-h-[92dvh] data-[swipe-direction=down]:rounded-t-3xl"
      :class="{ 'data-[swipe-direction=down]:h-[92dvh]': tall }"
    >
      <DrawerHeader class="flex-row items-center justify-between gap-3 px-4 pt-3 pb-4 text-left!">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <slot name="leading" />
          <DrawerTitle class="text-xl font-extrabold">{{ title }}</DrawerTitle>
        </div>
        <slot name="actions" />
        <DrawerClose as-child>
          <Button
            size="icon"
            variant="secondary"
            class="text-muted-foreground rounded-full"
            aria-label="Close"
          >
            ✕
          </Button>
        </DrawerClose>
      </DrawerHeader>
      <div
        class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto overscroll-contain px-4 pt-0.5 *:shrink-0"
        :class="$slots.footer ? 'pb-4' : 'pb-[calc(env(safe-area-inset-bottom)+20px)]'"
      >
        <slot />
      </div>
      <div
        v-if="$slots.footer"
        class="border-border flex shrink-0 flex-col gap-2.5 border-t px-4 pt-3 pb-[calc(env(safe-area-inset-bottom)+16px)]"
      >
        <slot name="footer" />
      </div>
    </DrawerContent>
  </Drawer>
</template>
