#!/usr/bin/env sh
# One implementation on both platforms; Linux still needs its own actual acceptance.
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec pwsh -NoProfile -File "$script_dir/local-demo.ps1" "$@"
