<script setup lang="ts">
/**
 * Shares a finished workout three ways: as a picture, as a link that shows it, or as Markdown.
 * Button props such as `variant` and `size` pass through to the button that opens the sheet.
 */
import { Image as ImageIcon, Link, Text } from '@lucide/vue'
import { computed, onBeforeUnmount, ref } from 'vue'
import { copyText } from '../clipboard'
import type { Workout } from '../domain/program'
import { recordIds } from '../domain/records'
import { sharedUrl, toShared, workoutMarkdown } from '../domain/share'
import { formatDate } from '../format'
import { canvasBlob, drawWorkoutImage } from '../shareImage'
import { state } from '../store'
import { Button, ListRow, Sheet } from './ui'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ workout: Workout; label?: string }>()

const open = ref(false)
const preview = ref<string | null>(null)
let image: Blob | null = null

type Action = 'image' | 'link' | 'markdown'
/** What the last tapped action came to, shown on its row for a moment. */
const result = ref<{ action: Action; text: string } | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined

const shared = computed(() =>
  toShared(
    props.workout,
    recordIds(
      state.history,
      state.history.findIndex((entry) => entry.id === props.workout.id),
    ),
  ),
)
const date = computed(() => formatDate(props.workout.startedAt))
const appUrl = computed(() => location.href.split('#')[0])
const fileName = computed(
  () => `eisen-${new Date(props.workout.startedAt).toISOString().slice(0, 10)}.png`,
)

function report(action: Action, text: string) {
  result.value = { action, text }
  clearTimeout(timer)
  timer = setTimeout(() => (result.value = null), 2500)
}

function describe(action: Action, idle: string): string {
  return result.value?.action === action ? result.value.text : idle
}

function forget() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = null
  image = null
}

async function show() {
  open.value = true
  result.value = null
  try {
    const site = appUrl.value.replace(/^https?:\/\//, '').replace(/\/(index\.html)?$/, '')
    image = await canvasBlob(drawWorkoutImage(shared.value, date.value, site))
    preview.value = URL.createObjectURL(image)
  } catch {
    forget()
  }
}

function close() {
  open.value = false
  forget()
}

function download(blob: Blob) {
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName.value
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}

/** The phone's share sheet where it takes a picture, a download everywhere else. */
async function shareImage() {
  if (!image) return
  const file = new File([image], fileName.value, { type: 'image/png' })
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: props.workout.name })
      return
    } catch (error) {
      // Closing the share sheet is a decision, not a failure.
      if (error instanceof DOMException && error.name === 'AbortError') return
    }
  }
  download(image)
  report('image', 'Saved to your downloads')
}

async function copyLink() {
  const copied = await copyText(sharedUrl(appUrl.value, shared.value))
  report('link', copied ? 'Link copied' : 'Copy failed')
}

async function copyMarkdown() {
  const copied = await copyText(workoutMarkdown(props.workout, date.value))
  report('markdown', copied ? 'Markdown copied' : 'Copy failed')
}

onBeforeUnmount(() => {
  clearTimeout(timer)
  forget()
})
</script>

<template>
  <Button variant="secondary" v-bind="$attrs" @click="show">{{ label ?? 'Share' }}</Button>

  <Sheet v-if="open" :title="`Share ${workout.name}`" @close="close">
    <img v-if="preview" class="preview" :src="preview" :alt="`${workout.name} on ${date} as a picture`" />
    <div class="actions" aria-live="polite">
      <ListRow
        :icon="ImageIcon"
        label="Picture"
        :description="describe('image', 'A card to post or send')"
        :disabled="!preview"
        @click="shareImage"
      />
      <ListRow
        :icon="Link"
        label="Link"
        :description="describe('link', 'Opens this workout in the browser')"
        @click="copyLink"
      />
      <ListRow
        :icon="Text"
        label="Markdown"
        :description="describe('markdown', 'Text for a chat or a note')"
        @click="copyMarkdown"
      />
    </div>
  </Sheet>
</template>

<style scoped>
.preview {
  align-self: center;
  width: auto;
  max-width: 100%;
  max-height: 46dvh;
  border-radius: var(--r-xl);
  border: 1px solid var(--border);
}

.actions {
  margin: 0 -16px;
  display: flex;
  flex-direction: column;
}

.actions > * + * {
  border-top: 1px solid var(--border);
}
</style>
