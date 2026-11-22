import type { Preview } from '@storybook/vue3-vite'
import { themes } from 'storybook/theming'
import '../src/style.css'

const preview: Preview = {
  tags: ['autodocs'],
  parameters: {
    layout: 'fullscreen',
    backgrounds: { disable: true },
    docs: { theme: themes.dark },
    controls: { expanded: true },
  },
  // The app is one phone-wide column. Every component is shown at that width.
  decorators: [
    () => ({
      template: '<div style="max-width: 390px; margin: 0 auto; padding: 16px"><story /></div>',
    }),
  ],
}

export default preview
