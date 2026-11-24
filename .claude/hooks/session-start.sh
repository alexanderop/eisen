#!/bin/bash
# The commit stamp and gate are git hooks in .githooks; a clone only runs them once this is set.
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
[ "$(git config --get core.hooksPath)" = .githooks ] || git config core.hooksPath .githooks
exit 0
