#!/bin/bash
# Stop hook: Claude may not finish a turn on Android sources that fail the cheap checks.
# Three failed rounds on the way, then it has to stop and tell the owner instead of trying again.
set -u
exec < /dev/null
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
. .githooks/lib.sh
dir="$(eisen_dir)"; hash="$(eisen_source_hash)"
if ! eisen_dirty || eisen_stamped tests; then /bin/rm -f "$dir/fails" "$dir/failed"; exit 0; fi
fails="$(cat "$dir/fails" 2>/dev/null || echo 0)"
# The owner has been told about exactly these sources: do not run again until they change.
[ "$fails" -ge 3 ] && [ "$(cat "$dir/failed" 2>/dev/null)" = "$hash" ] && exit 0
if out="$(bin/check 2>&1)"; then /bin/rm -f "$dir/fails" "$dir/failed"; exit 0; fi
fails=$((fails + 1)); echo "$fails" > "$dir/fails"; echo "$hash" > "$dir/failed"
{
  echo "bin/check failed (round $fails of 3):"
  # The failing tests and compiler errors first, then the end of the build's own summary.
  echo "$out" | grep -E '^FAILED |^       |^e: |^\s+(java|org|kotlin)\.[A-Za-z.]+(Error|Exception)' | head -20
  echo "$out" | grep -vE '^\s*$' | tail -6
  if [ "$fails" -ge 3 ]; then
    echo "Third failed round. Do not try another fix. Tell the owner which check fails, what you tried and what you think the cause is, then stop."
  else
    echo "Fix the cause. Do not skip, delete or loosen a test to get past this, and do not retry without a change."
  fi
} >&2
exit 2
