# CHECKPOINT — generated props 5: security camera from the ceiling

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-prop-art-order-5.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-910ecd8d-nj8inu`
**IDEA:** `.claude/agents/ideas/generated-prop-art-order-5.txt` (+ master `generated-prop-art.txt`) — the spec. Rules
(R…, PS…) and acceptance criteria (A…) are referred to by number below and are NOT repeated here.
**LANE:** full lane — kit (`.claude/skills/enemy-art`) + `render` (+ `render/propvisual`, new) + `tileset/render.tilesetgfx` registrations + docs.
**TESTS:** none — not balance-bearing (Testing Policy; V7). Verified by build + run-game screenshots.
**STARTED:** 2026-10-23
**BASELINE:** branched from the order-4 head (fa0e3c5; order 4 CP4 still waits on the owner).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-prop-art-order-5/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is UNVERIFIED work: finish it to the step's DONE WHEN, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-prop-art-order-5/CPn:`, with this file's tick in the
same commit. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No gameplay change: '#' stays solid, same placement and rules (V4). The pan is cosmetic.
- No new kit materials expected; if one is needed it is appended LAST; every earlier prop sheet re-bakes byte-identical.
- R1: full-height canvas (PROP_CAMERA_HEIGHT 1.0), camera painted in the top part, everything below transparent.
- R2: ceiling-mounted sheet -> no contact shadow in BOTH prop passes (registry flag, not a switch).
- No allocation in render(); constants in RenderConstants. Old generateCameraTexture + its registry row deleted (PS6).
- No new tests.

## STEP LEDGER

- [x] **CP0** — idea + this file committed and pushed.
- [x] **CP1** — camera baked (make_camera + PROPS row); previews (still + GIF) sent; earlier sheets unchanged.
- [x] **CP2** — registerCeilingSprite + isCeilingMounted, no contact shadow in both passes, height 1.0, old generator deleted; build green.
- [ ] **CP3** — in-engine screenshots (two moods + an enemy behind the camera) sent; docs updated.
- [ ] **CP4** — owner approval in DECISIONS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD
- CP1: make_camera() + PROPS row "camera"; no kit change, so every earlier sheet re-bakes cmp-identical (checked).
  48 frames @ 0.1 s (not the idea's 24: a 2.4 s full sweep read too fast for a security camera; 4.8 s now).
  Body ends at row 78 of 256 (R1). Scan cone swing 0.32 rad and faded 14 px from the canvas sides (at 0.55 it
  was cut by the canvas edge). Bake ~20 s.
- CP2: GeneratedPropRegistry.registerCeilingSprite("prop_camera", "camera") + isCeilingMounted(sheetId);
  GeneratedPropSheet.isCeilingMounted() cached at construction; PropRenderer.contactShadowFraction(char, sheet)
  used by both passes. generateCameraTexture + its registry row deleted (no camera PNG existed). Build green
  except the known StoryBarkTest. A3 holds by construction: the occluder buffer re-draws only the prop's alpha.
