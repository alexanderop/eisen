# Eisen 5×5

An offline 5×5 barbell workout tracker that installs as a PWA.

**Live:** https://alexanderop.github.io/eisen/

- Starts with a 5×5 program (Workout A and B) that you can change freely
- Build your own workouts from 45 exercises, or add your own exercises
- Sets, reps, weight and the step per exercise are yours to set
- Tap a set to log it, tap again for every missed rep
- Rest timer after each set
- Weight goes up after a completed exercise and drops 10% after three misses in a row
- Warmup and plate calculator, history, progress chart, kg and lb
- All data stays on your device, with JSON backup and restore

## Develop

```sh
pnpm install
pnpm dev
pnpm test
pnpm build
```

## Design system

The UI is built on [shadcn-vue](https://www.shadcn-vue.com) (Reka UI and Tailwind CSS 4).
Its components are copied into `src/components/ui/<name>/` and adjusted for a phone in a gym:
larger tap targets, a dark-only palette and lime as the primary colour.

- From shadcn-vue: `Button`, `Card`, `Badge`, `Input`, `Drawer`, `Tabs`, `ToggleGroup`.
- Composed from them in `src/components/ui/*.vue`: `Sheet` (a Drawer), `Segmented` (Tabs),
  `ChipGroup` (a ToggleGroup), `Stepper`, `ConfirmButton`, plus the app's own `SetButton` and `Field`.
- Add another one with `pnpm dlx shadcn-vue@latest add <name>`.

Colours use the shadcn variable names (`--primary`, `--card`, `--muted-foreground`) in `src/style.css`.

```sh
pnpm storybook        # browse and try every component at http://localhost:6006
pnpm build-storybook  # also runs in CI, so a broken story fails the build
```

Views import from the barrel: `import { Button, Card } from '../components/ui'`.

Built with Vue 3, Vite, Tailwind CSS and vite-plugin-pwa. Pushing to `main` deploys to GitHub Pages.
