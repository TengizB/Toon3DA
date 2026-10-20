#!/usr/bin/env bash
# Runs the real game once under the capture driver and prints its [capture]/game log lines. See SKILL.md.
# usage: run.sh [--out DIR] [--mood MOOD_ID] [--fps N] [--probe cpu|sync] [--gclog FILE] '<script>'
#   --out   where shot:NAME PNGs go (default /tmp/toon3d-shots)
#   --mood  forces a lighting mood (cool_steel | clinical_white | amber_hot | corrupted_violet | core_white_violet)
#   --fps   frame cap; 0 = uncapped (use with timestart/timestop). Default 30.
#   --probe cpu|sync  turns on the dev RenderPerformanceProbe (-Dtoon3d.perfProbe): per-pass ms + draw calls
#                     logged every 5 s as "[perf] ..." lines, and a readout at the top centre of the screen
#   --gclog FILE      writes a JVM GC log (-Xlog:gc) to FILE; count events with grep -c "Pause"
# Prerequisite: setup.sh (re-run it in a fresh container).
set -euo pipefail

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
RUNTIME_DIR="${TOON3D_RUNTIME_DIR:-/tmp/toon3d-runtime}"
MIRROR="$RUNTIME_DIR/mirror"
OUT=/tmp/toon3d-shots
MOOD=""
FPS=30
PROBE=""
GCLOG=""
while [[ $# -gt 1 ]]; do
  case "$1" in
    --out)  OUT="$2"; shift 2 ;;
    --mood) MOOD="$2"; shift 2 ;;
    --fps)  FPS="$2"; shift 2 ;;
    --probe) PROBE="$2"; shift 2 ;;
    --gclog) GCLOG="$2"; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
SCRIPT="${1:?script required, e.g. '2:tap:640:390;4:tap:640:390;9:tap:640:144;13:shot:play;14:exit'}"
[[ -d "$MIRROR/capture" ]] || { echo "run setup.sh first" >&2; exit 2; }
mkdir -p "$OUT"
export DISPLAY="${DISPLAY_NUMBER:-:77}"

ARGS=(-q :capture:capture "-Dcap.out=$OUT" "-Dcap.fps=$FPS" "-Dcap.script=$SCRIPT")
[[ -n "$MOOD" ]] && ARGS+=("-Dtoon3d.forceMood=$MOOD")
[[ -n "$PROBE" ]] && ARGS+=("-Dtoon3d.perfProbe=$PROBE")
[[ -n "$GCLOG" ]] && ARGS+=("-Dcap.gclog=$GCLOG")

status=0
(cd "$MIRROR" && timeout 300 ./gradlew "${ARGS[@]}" 2>&1) \
  | grep -v -e 'ALSA lib' -e 'Picked up JAVA_TOOL_OPTIONS' -e '^\s*$' || status=$?

# The game writes its synthesised-audio WAV caches into assets/ on desktop: never commit them.
rm -rf "$REPO_ROOT/assets/game-audio" "$REPO_ROOT/assets/story-audio"
ls "$OUT"
exit $status
