# Eisen 5×5

An offline 5×5 barbell workout tracker that installs as a PWA.

**Live:** https://alexanderop.github.io/eisen/

- Workout A and B alternate: Hack Squat, Bench Press, Barbell Row / Hack Squat, Overhead Press, Deadlift
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

Built with Vue 3, Vite and vite-plugin-pwa. Pushing to `main` deploys to GitHub Pages.
