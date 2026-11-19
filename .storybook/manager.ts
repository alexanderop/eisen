import { addons } from 'storybook/manager-api'
import { create } from 'storybook/theming'

addons.setConfig({
  theme: create({
    base: 'dark',
    brandTitle: 'Eisen UI',
    colorPrimary: '#c8f031',
    colorSecondary: '#8aa81e',
    appBg: '#0b0c0e',
    appContentBg: '#0b0c0e',
    appPreviewBg: '#0b0c0e',
    barBg: '#15171b',
    appBorderColor: '#2b2f37',
  }),
})
