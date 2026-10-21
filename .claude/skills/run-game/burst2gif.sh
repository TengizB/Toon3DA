#!/usr/bin/env bash
# Turns a run.sh burst (burst:NAME:COUNT:INTERVAL -> DIR/NAME_00.png, NAME_01.png, ...) into an animated GIF for
# the owner. DEV TOOL (see SKILL.md), never part of the build.
# usage: burst2gif.sh DIR NAME [--crop WxH+X+Y] [--scale PERCENT] [--delay CENTISECONDS] [--label TEXT] [--out FILE]
#   --crop   window-pixel crop applied to every frame (the PNGs are the 1278x695 back buffer, origin top-left)
#   --scale  resize after the crop (default 100)
#   --delay  per-frame delay in 1/100 s; match the burst INTERVAL for real time (default 5 = 0.05 s)
#   --label  text stamped top-left on every frame (default NAME)
#   --out    output path (default DIR/NAME.gif)
# Alpha is dropped (-alpha off): the shots keep the framebuffer alpha, which would turn translucent UI pale.
# Concatenate several GIFs into one sequence with:  convert a.gif b.gif c.gif sequence.gif
set -euo pipefail
DIR="${1:?DIR}"; NAME="${2:?NAME}"; shift 2
CROP=""; SCALE=100; DELAY=5; LABEL="$NAME"; OUTFILE="$DIR/$NAME.gif"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --crop)  CROP="$2"; shift 2 ;;
    --scale) SCALE="$2"; shift 2 ;;
    --delay) DELAY="$2"; shift 2 ;;
    --label) LABEL="$2"; shift 2 ;;
    --out)   OUTFILE="$2"; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
shopt -s nullglob
FRAMES=("$DIR/${NAME}"_[0-9][0-9].png)
[[ ${#FRAMES[@]} -gt 0 ]] || { echo "no frames $DIR/${NAME}_NN.png" >&2; exit 1; }
OPS=(-alpha off)
[[ -n "$CROP" ]] && OPS+=(-crop "$CROP" +repage)
OPS+=(-resize "${SCALE}%" -fill '#ffd27a' -undercolor '#000000a0' -pointsize 16 -gravity northwest -annotate +6+6 "$LABEL")
convert -delay "$DELAY" -loop 0 "${FRAMES[@]}" "${OPS[@]}" -layers Optimize "$OUTFILE"
echo "$OUTFILE (${#FRAMES[@]} frames)"
