<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import IconButton from './IconButton.vue'

/**
 * A dialog that rises from the bottom edge, in reach of the thumb.
 * Closes on the backdrop, the close button and Escape, and hands focus back to what opened it.
 */

defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()

const sheet = useTemplateRef<HTMLElement>('sheet')
let opener: Element | null = null

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => {
  opener = document.activeElement
  sheet.value?.focus()
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (opener instanceof HTMLElement) opener.focus()
})
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <section ref="sheet" class="sheet" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1">
      <header class="row">
        <h2>{{ title }}</h2>
        <IconButton label="Close" tone="muted" round @click="emit('close')">✕</IconButton>
      </header>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: rgb(0 0 0 / 0.6);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  overscroll-behavior: contain;
}

.sheet {
  width: 100%;
  max-width: 480px;
  max-height: 92dvh;
  overflow-y: auto;
  outline: none;
  background: var(--surface);
  border: 1px solid var(--line);
  border-bottom: 0;
  border-radius: 24px 24px 0 0;
  padding: 20px 16px calc(env(safe-area-inset-bottom) + 20px);
  display: flex;
  flex-direction: column;
  gap: 16px;
  animation: rise 0.18s ease-out;
}

h2 {
  font-size: 20px;
  font-weight: 800;
}

@keyframes rise {
  from {
    transform: translateY(24px);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sheet {
    animation: none;
  }
}
</style>
