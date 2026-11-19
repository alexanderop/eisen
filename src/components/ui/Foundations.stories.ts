import type { Meta, StoryObj } from '@storybook/vue3-vite'

/**
 * The tokens every component is built from. They live in `src/style.css` as CSS variables.
 * Dark only: the app is used in a gym, at arm's length, often with one hand.
 */
const meta: Meta = { title: 'Foundations/Tokens', tags: ['!autodocs'] }

export default meta
type Story = StoryObj

const COLORS = [
  ['--bg', 'Page background'],
  ['--surface', 'Cards, sheets, inputs'],
  ['--surface-2', 'Controls on a surface'],
  ['--line', 'Borders and dividers'],
  ['--text', 'Main text'],
  ['--muted', 'Secondary text'],
  ['--accent', 'The main action, success, selection'],
  ['--accent-ink', 'Text on accent'],
  ['--warn', 'Missed reps, deload'],
  ['--danger', 'Destructive actions'],
]

export const Colors: Story = {
  render: () => ({
    setup: () => ({ COLORS }),
    template: `
      <div style="display: grid; gap: 10px">
        <div v-for="[token, use] in COLORS" :key="token" style="display: flex; gap: 12px; align-items: center">
          <div :style="{ background: 'var(' + token + ')', width: '52px', height: '52px', flex: 'none', borderRadius: 'var(--radius-md)', border: '1px solid var(--line)' }" />
          <div><strong>{{ token }}</strong><p class="muted" style="font-size: 13px">{{ use }}</p></div>
        </div>
      </div>`,
  }),
}

const TYPE = ['2xl', 'xl', 'lg', 'md', 'base-plus', 'base', 'sm-plus', 'sm', 'xs']

export const Typography: Story = {
  render: () => ({
    setup: () => ({ TYPE }),
    template: `
      <div style="display: grid; gap: 14px">
        <h1 class="page-title">Page title</h1>
        <p class="eyebrow">Eyebrow label</p>
        <p class="muted">Muted supporting text</p>
        <hr style="width: 100%; border: 0; border-top: 1px solid var(--line)" />
        <div v-for="size in TYPE" :key="size" style="display: flex; gap: 12px; align-items: baseline">
          <span class="muted" style="width: 132px; font-size: 13px; flex: none">--text-{{ size }}</span>
          <span :style="{ fontSize: 'var(--text-' + size + ')', fontWeight: 700 }">82.5kg</span>
        </div>
      </div>`,
  }),
}

export const SpacingAndRadius: Story = {
  render: () => ({
    template: `
      <div style="display: grid; gap: 10px">
        <div v-for="n in 6" :key="n" style="display: flex; gap: 12px; align-items: center">
          <span class="muted" style="width: 88px; font-size: 13px">--space-{{ n }}</span>
          <div :style="{ width: 'var(--space-' + n + ')', height: '16px', background: 'var(--accent)' }" />
        </div>
        <div style="display: flex; gap: 12px; margin-top: 16px">
          <div v-for="size in ['md', 'lg', 'xl', 'full']" :key="size" style="text-align: center">
            <div :style="{ width: '64px', height: '64px', background: 'var(--surface-2)', borderRadius: 'var(--radius-' + size + ')' }" />
            <span class="muted" style="font-size: 12px">{{ size }}</span>
          </div>
        </div>
        <div style="display: flex; gap: 12px; align-items: end; margin-top: 16px">
          <div v-for="size in ['sm', 'md', 'lg']" :key="size" style="text-align: center">
            <div :style="{ width: '64px', height: 'var(--control-' + size + ')', background: 'var(--surface-2)', borderRadius: 'var(--radius-md)' }" />
            <span class="muted" style="font-size: 12px">control-{{ size }}</span>
          </div>
        </div>
      </div>`,
  }),
}
