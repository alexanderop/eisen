<script setup lang="ts">
defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div class="backdrop" @click.self="emit('close')" @keydown.esc="emit('close')">
    <section class="sheet" role="dialog" aria-modal="true" :aria-label="title">
      <header class="row">
        <h2>{{ title }}</h2>
        <button class="close" aria-label="Close" @click="emit('close')">✕</button>
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
}

.sheet {
  width: 100%;
  max-width: 480px;
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

.close {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--muted);
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
