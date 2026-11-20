<script setup lang="ts">
import { Button } from './button'
import { Drawer, DrawerClose, DrawerContent, DrawerHeader, DrawerTitle } from './drawer'

/**
 * A dialog that rises from the bottom edge, in reach of the thumb.
 * Mount it to open it. It closes on a swipe down, the backdrop, the close button and Escape.
 */
defineProps<{ title: string }>()
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
    >
      <DrawerHeader class="flex-row items-center justify-between gap-3 px-4 pt-3 pb-4 text-left!">
        <DrawerTitle class="text-xl font-extrabold">{{ title }}</DrawerTitle>
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
        class="flex flex-col gap-4 overflow-y-auto overscroll-contain px-4 pt-0.5 *:shrink-0 pb-[calc(env(safe-area-inset-bottom)+20px)]"
      >
        <slot />
      </div>
    </DrawerContent>
  </Drawer>
</template>
