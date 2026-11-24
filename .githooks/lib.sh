# Shared by the git hooks, bin/check, the Stop hook and eisenctl. Source it; it runs nothing.
# A stamp is a file that holds the hash of the Android sources at the moment a check passed.
# It lives in the git directory, so no change can carry a stamp along with it.

eisen_root() { git rev-parse --show-toplevel; }
eisen_dir() { d="$(git rev-parse --git-path eisen)"; mkdir -p "$d"; (cd "$d" && pwd); }

# Everything the APK is built from: the Android project and the assets it packs.
# Sorted: git lists tracked files before new ones, and staging a new file must not change the hash.
eisen_source_hash() {
  (cd "$(eisen_root)" && git ls-files -co --exclude-standard -z -- apps/android apps/web/src/assets | sort -z \
    | xargs -0 shasum 2>/dev/null | shasum | cut -c1-16)
}

# 0 when the Android sources differ from the last commit.
eisen_dirty() { [ -n "$(cd "$(eisen_root)" && git status --porcelain -- apps/android apps/web/src/assets)" ]; }

eisen_stamp() { echo "$(eisen_source_hash)" > "$(eisen_dir)/$1"; }
eisen_stamped() { [ -f "$(eisen_dir)/$1" ] && [ "$(cat "$(eisen_dir)/$1")" = "$(eisen_source_hash)" ]; }

# What a commit of the current sources may claim: "emulator, tests", "tests", "emulator" or "none".
eisen_verified() {
  how=""
  eisen_stamped emulator && how="emulator"
  eisen_stamped tests && how="${how:+$how, }tests"
  echo "${how:-none}"
}
