import { registerSW } from 'virtual:pwa-register'
import { ref } from 'vue'

/** What is running: the release, the commit it was built from and when. */
export const build = {
  version: __APP_VERSION__,
  commit: __APP_COMMIT__,
  builtAt: new Date(__APP_BUILT_AT__),
}

/** A new version is downloaded and waits for the lifter to switch to it. */
export const updateReady = ref(false)
/** The banner was closed. The update stays on offer in the settings. */
export const updateDismissed = ref(false)

export type CheckResult = 'checking' | 'latest' | 'downloading' | 'offline' | 'unsupported'
/** Outcome of the last check the lifter asked for. */
export const checkResult = ref<CheckResult | null>(null)

/** An installed app is resumed, never reloaded, so it has to ask for updates by itself. */
const CHECK_EVERY_MS = 60 * 60 * 1000
const CHECK_ON_RESUME_AFTER_MS = 5 * 60 * 1000

let registration: ServiceWorkerRegistration | undefined
let workerUrl = ''
let lastCheck = Date.now()

const activate = registerSW({
  immediate: true,
  onNeedRefresh() {
    updateReady.value = true
    updateDismissed.value = false
    checkResult.value = null
  },
  onRegisteredSW(url, registered) {
    if (!registered) return
    workerUrl = url
    registration = registered
    setInterval(() => void poll(), CHECK_EVERY_MS)
    document.addEventListener('visibilitychange', () => {
      const stale = Date.now() - lastCheck > CHECK_ON_RESUME_AFTER_MS
      if (document.visibilityState === 'visible' && stale) void poll()
    })
  },
})

/**
 * Asks the server for a new service worker. The worker file is fetched past the cache first,
 * so an offline phone or a deploy in progress does not break the registration.
 */
async function poll(): Promise<CheckResult> {
  if (!registration) return 'unsupported'
  if (registration.installing) return 'downloading'
  if (navigator.onLine === false) return 'offline'
  lastCheck = Date.now()
  try {
    const response = await fetch(workerUrl, { cache: 'no-store' })
    if (response.status !== 200) return 'offline'
    await registration.update()
    return registration.installing || registration.waiting ? 'downloading' : 'latest'
  } catch {
    return 'offline'
  }
}

/** The check behind the button in the settings. */
export async function checkForUpdate(): Promise<void> {
  checkResult.value = 'checking'
  checkResult.value = await poll()
}

/** Switches to the new version and reloads. Workouts are saved on every tap, so nothing is lost. */
export function applyUpdate(): void {
  void activate(true)
}
