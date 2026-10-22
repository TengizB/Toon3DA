"""The generated bestiary: one RIG + clip set per creature, 160 x 240 px canvas, ground line y = 232.

Usage:  python3 bestiary.py OUT_DIR [--preview DIR] [--stills] [--halo] [creature ...]
  default    bake every clip of every creature: <name>.atlas.png, <name>.glow.png, <name>.clips.json
  --preview  also write the owner previews into DIR: one GIF per clip (3x) + <name>_strip.png
  --stills   write only the rest pose as <name>.png (contact sheets); with --halo, the presentation
             form with a soft glow spill (the game alpha-cuts, so shipped art never has the halo)

A creature lives in its own <name>_rig.py (the rig function, its pose defaults and its clips) and is
one row in CREATURES below.
"""
import os
import sys

import kit
from warden_rig import WARDEN, WARDEN_CLIPS
from harvest_tick_rig import HARVEST_TICK, TICK_CLIPS
from iron_stalker_rig import IRON_STALKER, STALKER_CLIPS
from plague_hulk_rig import PLAGUE_HULK, HULK_CLIPS
from eye_tyrant_rig import EYE_TYRANT, TYRANT_CLIPS
from gore_biter_rig import GORE_BITER, BITER_CLIPS
from sump_wretch_rig import SUMP_WRETCH, WRETCH_CLIPS

# creature -> (rig, clips). A new creature is one <name>_rig.py plus one row here.
CREATURES = [
    (WARDEN, WARDEN_CLIPS),
    (HARVEST_TICK, TICK_CLIPS),
    (IRON_STALKER, STALKER_CLIPS),
    # order 5: the ABERRATIONS
    (PLAGUE_HULK, HULK_CLIPS),
    (EYE_TYRANT, TYRANT_CLIPS),
    (GORE_BITER, BITER_CLIPS),
    (SUMP_WRETCH, WRETCH_CLIPS),      # the first Mire Wraith design, kept for a new enemy (owner, 2026-10-22)
]

if __name__ == "__main__":
    args = sys.argv[1:]
    out = args.pop(0)
    preview = None
    if "--preview" in args:
        i = args.index("--preview"); preview = args[i + 1]; del args[i:i + 2]
    stills = "--stills" in args
    halo = "--halo" in args
    only = [a for a in args if not a.startswith("--")] or [rig.name for rig, _ in CREATURES]
    os.makedirs(out, exist_ok=True)
    for rig, clips in CREATURES:
        if rig.name not in only:
            continue
        if stills:
            img, _, _ = rig.render(rig.pose(), halo=halo)
            img.save(os.path.join(out, f"{rig.name}.png"), optimize=False)
            print("wrote", rig.name)
        else:
            kit.write_creature(rig, clips, out, preview_dir=preview)
