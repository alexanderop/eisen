import { reactive, toRaw, watch } from 'vue'
import { findWod, type Wod } from './domain/wod'
import { copyEdit, editOf, isChanged, newEdit, readEdit, type WodEdit, type WodFilter } from './domain/wodScreens'
import { newId, saveWod, state } from './store'
import { timer } from './timer'

/**
 * Where the WODs page is, and the builder's draft. It lives outside the components, so a tab switch
 * or a run and its result come back to the detail or the builder they left. The draft is also kept
 * in storage: a reload or a closed app offers it again.
 *
 * The detail and the builder sit on a history entry of their own, so the phone's back button
 * closes them instead of the app.
 */
export type WodScreen = { kind: 'list' } | { kind: 'detail'; id: string } | { kind: 'builder' }

const DRAFT_KEY = 'eisen:wod-draft'

function storedEdit(): WodEdit | null {
  try {
    return readEdit(localStorage.getItem(DRAFT_KEY))
  } catch {
    return null
  }
}

export const wods = reactive({
  screen: { kind: 'list' } as WodScreen,
  /** The builder's draft. Set while the builder is open, and after a reload until it is continued or discarded. */
  edit: storedEdit(),
  /** "Discard changes?" is open: `back` leaves to where the builder came from, `top` to the list. */
  discarding: null as 'back' | 'top' | null,
  /** The benchmark filter, for as long as the app is open. */
  filter: 'all' as WodFilter,
  /** The search of the WODs page, kept like the filter. */
  query: '',
  /** Where the list was scrolled to when a detail or the builder opened from it. */
  listScroll: 0,
  /** The exercise picker's filter and search, while the builder is open. */
  picker: { filter: 'WOD', query: '' },
  /** The WODs page is on screen: only then does the back button belong to it. */
  mounted: false,
})

watch(
  () => wods.edit,
  (current) => {
    try {
      if (current) localStorage.setItem(DRAFT_KEY, JSON.stringify(current))
      else localStorage.removeItem(DRAFT_KEY)
    } catch {
      // Storage blocked: the draft lives as long as the page.
    }
  },
  { deep: true },
)

function hasEntry(): boolean {
  return (history.state as { wods?: boolean } | null)?.wods === true
}

/** Remembers where the list stood, for coming back to it. */
function leaveList(): void {
  if (wods.screen.kind === 'list') wods.listScroll = window.scrollY
}

function pushEntry(): void {
  if (!hasEntry()) history.pushState({ wods: true }, '')
}

/** Set while the app itself goes back, so that popstate is not taken for the back button. */
let popping = false

function toList(): void {
  wods.screen = { kind: 'list' }
  if (hasEntry()) {
    popping = true
    history.back()
  }
}

export function openDetail(id: string): void {
  leaveList()
  wods.screen = { kind: 'detail', id }
  pushEntry()
}

/** How long to wait for the closing timer to take its history entry back. */
const TIMER_POP_MS = 400

/**
 * Opens a WOD's detail once the timer screen has closed, as after "Don't count this run". The
 * timer takes its history entry back as it closes, and that would land on the list: the detail
 * opens after it, or after a moment when the timer had no entry.
 */
export function returnToDetail(id: string): void {
  const open = () => {
    window.removeEventListener('popstate', open)
    clearTimeout(fallback)
    openDetail(id)
  }
  window.addEventListener('popstate', open)
  const fallback = setTimeout(open, TIMER_POP_MS)
}

export function closeDetail(): void {
  toList()
}

function openEdit(edit: WodEdit): void {
  wods.edit = edit
  wods.picker = { filter: 'WOD', query: '' }
  leaveList()
  wods.screen = { kind: 'builder' }
  pushEntry()
}

export function newWod(): void {
  openEdit(newEdit(newId()))
}

export function editWod(workout: Wod): void {
  openEdit(editOf(structuredClone(toRaw(workout))))
}

export function copyWod(workout: Wod): void {
  openEdit(copyEdit(structuredClone(toRaw(workout)), newId()))
}

/** "Continue" on the card of a draft from before a reload. */
export function continueEdit(): void {
  if (!wods.edit) return
  leaveList()
  wods.screen = { kind: 'builder' }
  pushEntry()
}

/** "Discard" on that card. */
export function dropEdit(): void {
  wods.edit = null
}

/**
 * Leaves the builder: asks first when there are changes, unless `force`. `top` goes to the list,
 * otherwise back to where the builder came from.
 */
export function leaveBuilder(force = false, top = false): void {
  const edit = wods.edit
  if (!edit) {
    toList()
    return
  }
  if (!force && isChanged(edit)) {
    wods.discarding = top ? 'top' : 'back'
    return
  }
  wods.discarding = null
  wods.edit = null
  const back = edit.back
  if (!top && back.kind === 'detail' && findWod(state.wods, back.id)) {
    wods.screen = back
    pushEntry()
  } else {
    toList()
  }
}

export function saveEdit(): void {
  if (!wods.edit) return
  const saved = saveWod(wods.edit.draft)
  wods.edit = null
  wods.screen = { kind: 'detail', id: saved.id }
  pushEntry()
}

/** The Timer tab tapped again: back to the top of the page. A builder with changes asks first. */
export function wodsHome(): void {
  if (wods.screen.kind === 'builder') leaveBuilder(false, true)
  else toList()
}

/** A history entry for the screen on display, after the page comes back from another tab. */
export function syncEntry(): void {
  if (wods.screen.kind !== 'list') pushEntry()
}

window.addEventListener('popstate', () => {
  if (popping) {
    popping = false
    return
  }
  // The timer's own entry, or one it put back: the timer handles those.
  if (hasEntry() || !wods.mounted || timer.run || timer.result) return
  if (wods.screen.kind === 'builder' && wods.edit && isChanged(wods.edit)) {
    pushEntry()
    wods.discarding = 'back'
  } else if (wods.screen.kind === 'builder') {
    leaveBuilder(true)
  } else {
    wods.screen = { kind: 'list' }
  }
})
