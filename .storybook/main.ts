import { defineMain } from '@storybook/vue3-vite/node'

export default defineMain({
  framework: '@storybook/vue3-vite',
  stories: ['../src/**/*.stories.ts'],
  addons: ['@storybook/addon-docs', '@storybook/addon-a11y'],
  core: { disableTelemetry: true },
  // Storybook reuses vite.config.ts. The PWA plugin would give the component library a service worker.
  viteFinal(config) {
    const plugins = (config.plugins ?? []).flat(Infinity as 1)
    config.plugins = plugins.filter(
      (plugin) =>
        !(plugin && 'name' in plugin && String(plugin.name).startsWith('vite-plugin-pwa')),
    )
    return config
  },
})
