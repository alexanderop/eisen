# Review rules for Eisen

For whoever reviews a change to this repo, human or agent. One reviewer, several rounds: a later
round reads what the earlier one found and checks the fixes, it does not start over.

Read this file from the last commit (`git show HEAD:REVIEW.md`, or `origin/main` for a branch), not
from the working copy. A change may not loosen the rules that judge it.

## How to review

1. Read the diff (`git diff HEAD`, or `git diff origin/main...` for a branch).
2. Go through the checklist below and check **only** the items whose trigger is in the diff.
3. Run `bin/check`. For a change a user can see or hear, run the journey of that feature from
   `.claude/skills/verify-eisen/features/` and look at the screenshots.
4. Report each finding with one tag and one sentence that says what breaks for the lifter:
   - **Important**: must be fixed before the commit. Wrong behaviour, lost data, a crash, a broken rule.
   - **Nit**: worth a line, not worth a round.
   - **Pre-existing**: was already wrong before this diff. Report it, do not hold the change for it.
5. Fix the Important ones, run the checks again. After three rounds that still fail, stop and tell
   the owner what fails and why.

## Checklist

Each item: the trigger you can read from a diff, the check, and where it comes from.

1. **Saved state.** Trigger: `app/state/State.kt`, or any `@Serializable` class, field or enum name.
   Check: a new field has a default; a renamed or removed one has a migration and a version bump;
   `loadState` still returns `Ok` for the old fixtures in `DomainTest`; no class name is written to
   `eisen.json`, `timer-run.json`, `wod-draft.json` or a share link. From: `e2b1e28`, `e1978fb`.
2. **Names Android stores.** Trigger: a class named in `AndroidManifest.xml` or `res/xml/` is moved
   or renamed; a notification channel id, SharedPreferences name, intent action or PendingIntent
   request code changes. Check: the old string value is kept. A running rest, the widget and the
   shortcuts point at the old name on every phone that updates. From: `e1978fb` (a rest that runs
   during the update loses its alarm).
3. **What the coach says and when.** Trigger: `Timer.kt` (`timerCues`), `Boxing.kt` (`boxingView`,
   `BOXING_MOVES`, themes), `TimerSystem.kt`, `TimerSounds.kt`. Check: every number a test pins and
   the diff changes has a reason in the commit message; times are counted per round, never for the
   whole timer; a muted emulator run shows the expected `EisenTimer: said <sound>` lines in logcat.
   From: `9c42ba1`, `f1aefbf`.
4. **Recordings.** Trigger: a new sound id or line, or a file under `apps/web/src/assets/voice/`.
   Check: every line has an mp3 (`BoxingTest` "has a recording in the shared assets"); a move clip's
   length in `BOXING_MOVES` is measured from the file, not assumed. From: `f1aefbf` (clips assumed
   650 ms were 0.7 to 1.8 s, the coach talked over itself).
5. **The frozen PWA.** Trigger: anything under `apps/web`. Check: only assets and voice scripts
   change; `pnpm test` still passes, because `deploy.yml` still runs it on every push.
   From: `67f1a09` (new Android-only recordings broke the Pages deploy).
6. **Package boundaries.** Trigger: a new import from another feature, or an edit to `featureEdges`
   or `uiEdges` in `ArchitectureTest`. Check: the new edge has a comment that says why, and it could
   not be solved by moving one function to `app/elm`. From: `e1978fb`.
7. **Pure code.** Trigger: `!!`, `throw`, a clock, a random number, or an `android`, `androidx`,
   `java.io`, `java.net` import in `core`, `feature/*` (not `ui`, `platform`), `app/elm`, `app/state`.
   Check: `RulesTest` passes without a new exception. From: `e2b1e28`.
8. **The checks themselves.** Trigger: the diff touches `RulesTest`, `ArchitectureTest`, `Steps.kt`,
   a pinned number in any test, `REVIEW.md`, `.githooks/`, `.claude/hooks/`, `bin/check` or
   `eisenctl`. Check: always **Important**, always named in the report with the reason, so the owner
   sees that a change edited its own judge. A test that fails sometimes is not retried, slept past or
   skipped: find the cause, or delete the test and write down why.
9. **Texts on screen.** Trigger: a string a user reads, or a content description, changes. Check:
   the journeys in `.claude/skills/verify-eisen/features/` use these texts as handles; the feature
   file is fixed in the same change.
10. **Build and dependencies.** Trigger: `build.gradle.kts`, `libs.versions.toml`, ProGuard rules.
    Check: the signed release build (`assembleRelease`, R8) succeeds, not only debug; a new
    dependency has a reason a small in-repo implementation cannot meet.
11. **Workflows.** Trigger: `.github/workflows/`. Check: `uvx zizmor --persona pedantic` is clean;
    actions stay pinned to a commit.

## Keeping this list worth reading

- A bug that reaches the owner becomes an item here or a test, with the commit it came from.
- An item needs a trigger a diff can show. "Be careful with timers" is not an item.
- When the list passes 15 items, merge or delete before adding. Delete an item whose check a test
  now makes automatically.
