import { onBeforeUnmount, onMounted } from 'vue'

/**
 * Keeps the screen on while the component that calls this is shown.
 * The phone drops the lock when the app goes to the background, so it is taken again on return.
 */
export function useWakeLock(): void {
  let lock: WakeLockSentinel | null = null
  let mounted = true

  async function request() {
    if (document.visibilityState !== 'visible' || !('wakeLock' in navigator)) return
    try {
      const taken = await navigator.wakeLock.request('screen')
      if (mounted) lock = taken
      else void taken.release()
    } catch {
      // Denied, for example in battery saver mode. Everything works without it.
    }
  }

  onMounted(() => {
    void request()
    document.addEventListener('visibilitychange', request)
  })

  onBeforeUnmount(() => {
    mounted = false
    document.removeEventListener('visibilitychange', request)
    void lock?.release()
  })
}
