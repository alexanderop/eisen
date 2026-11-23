import { describe, expect, it } from 'vitest'
import { catalogExercise } from './exercises'
import { MOTION_FRAMES, MOTION_STILL_FRAME, MOTIONS, motionOf } from './motions'

/** The strips there are so far, as data URLs. More arrive later, so none is required. */
const strips = import.meta.glob<string>('../assets/motions/*.webp', { eager: true, query: '?inline', import: 'default' })

/** Width and height from the header of a WebP file, in any of its three kinds. */
function webpSize(bytes: Uint8Array): [number, number] {
  const tag = String.fromCharCode(...bytes.slice(12, 16))
  const le = (at: number, length: number) => bytes.slice(at, at + length).reduceRight((sum, byte) => sum * 256 + byte, 0)
  if (tag === 'VP8 ') return [le(26, 2) & 0x3fff, le(28, 2) & 0x3fff]
  if (tag === 'VP8L') {
    const bits = le(21, 4)
    return [(bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1]
  }
  if (tag === 'VP8X') return [le(24, 3) + 1, le(27, 3) + 1]
  throw new Error(`not a WebP: ${tag}`)
}

describe('motions', () => {
  it('finds the strip of an exercise, shared where the movement is the same', () => {
    expect(motionOf('burpee')).toBe('burpee')
    expect(motionOf('chinups')).toBe('pull-up')
    expect(motionOf('sprint')).toBe(motionOf('run'))
    expect(motionOf('squat')).toBeUndefined()
    expect(motionOf('custom-1')).toBeUndefined()
  })

  it('only names exercises of the catalog', () => {
    expect(Object.keys(MOTIONS).filter((id) => !catalogExercise(id))).toEqual([])
  })

  it('stands still on a frame of the strip', () => {
    expect(MOTION_STILL_FRAME).toBeGreaterThanOrEqual(0)
    expect(MOTION_STILL_FRAME).toBeLessThan(MOTION_FRAMES)
  })

  it('has strips of square frames side by side, each for an exercise', () => {
    const names = new Set(Object.values(MOTIONS))
    for (const [path, url] of Object.entries(strips)) {
      const name = path.split('/').at(-1)!.replace('.webp', '')
      expect(names).toContain(name)
      const bytes = Uint8Array.from(atob(url.split(',')[1]), (char) => char.charCodeAt(0))
      const [width, height] = webpSize(bytes)
      expect(width, name).toBe(height * MOTION_FRAMES)
    }
  })
})
