#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Authorization is external (gh auth). Never store tokens or overwrite a different project.
gh auth status
if test -n "$(git status --porcelain)"; then
  echo 'Commit or review local changes before publishing.' >&2
  exit 1
fi
if git remote get-url origin >/dev/null 2>&1; then
  echo 'Origin exists; refusing to replace it. Push reviewed changes to that Nabd repository explicitly.' >&2
  exit 1
fi
gh repo create Nabd-News --private --source=. --remote=origin --push
# The push triggers Nabd Quality; do not start a duplicate run.
gh run list --workflow android.yml --limit 5
