<script setup lang="ts">
import { applyUpdate, updateDismissed, updateReady } from '../pwa'
import { Button } from './ui'

/** Tells the lifter that a new version is ready. It sits above the tab bar and never reloads by itself. */
</script>

<template>
  <!-- The live region is always there, so a screen reader announces the banner when it appears. -->
  <div role="status" aria-live="polite" aria-atomic="true">
    <div v-if="updateReady && !updateDismissed" class="prompt">
      <div class="text">
        <strong>New version ready</strong>
        <span class="muted">Takes a second. Your data stays.</span>
      </div>
      <Button size="md" @click="applyUpdate">Update</Button>
      <Button
        size="icon"
        variant="secondary"
        class="text-muted-foreground"
        aria-label="Later"
        @click="updateDismissed = true"
      >
        ✕
      </Button>
    </div>
  </div>
</template>

<style scoped>
.prompt {
  position: fixed;
  left: 50%;
  bottom: calc(var(--tabs-height) + env(safe-area-inset-bottom) + 12px);
  transform: translateX(-50%);
  width: min(448px, calc(100% - 24px));
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 10px 10px 16px;
  background: var(--secondary);
  border: 1px solid var(--primary);
  border-radius: 18px;
  box-shadow: 0 12px 32px rgb(0 0 0 / 0.5);
}

.text {
  flex: 1;
  display: flex;
  flex-direction: column;
  font-size: 15px;
}

.text .muted {
  font-size: 13px;
}
</style>
