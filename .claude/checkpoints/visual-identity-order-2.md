# CHECKPOINT — Visual Identity 2: grounded sprites (halo cut, contact shadows, muzzle light)

**PURPOSE:** the live resume record for `.claude/agents/ideas/visual-identity-order-2.txt`. This session may be cut
off at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues from the first step whose box
is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-1c332c6a-z0q18l`
**IDEA:** `.claude/agents/ideas/visual-identity-order-2.txt` — the spec. Rules R1-R10, acceptance A1-A11, referred to
by number and NOT repeated here.
**LANE:** full lane / `render` + `input` + `world` + `util` (+ docs).
**TESTS:** none — not balance-bearing (CLAUDE.md Testing Policy). Verified by build + run (capture harness).
**STARTED:** 2026-10-19
**BASELINE:** branched from `b10f75d` (order 1 IMPLEMENTED). Mirror build compiles; `test` = 480 tests, 1
PRE-EXISTING failure `narrative.StoryBarkTest` (bark.depth.core.2) present since b0aa2e5. "Green" = no other failure.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `visual-identity-order-2/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is UNVERIFIED work: finish it to the step's DONE WHEN and verify, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `visual-identity-order-2/CPn:`, tick in the same commit.
Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No allocation in any render path; every Disposable disposed (shadow textures per renderer; sheets by Main).
- Constants in EnemyConstants / RenderConstants / EffectConstants / ArtDirection; formulas in GameMath with comment block.
- register() rows in MuzzleLightCatalog, never a switch on ItemType. `input` never imports `render` (Consumer seam).
- Order-1 emissive exemption (R4 there) still holds; the weapon sprite gets no muzzle light.
- WallRenderer columns / floor rows run on WORKER threads: read only immutable or pre-snapshotted light state there.
- PNG files untouched (V1). No new tests. No persistence keys.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — EnemySpriteSheets (R1 cut, R2 session cache, R3 log); EnemyRenderer uses it, no longer disposes
      sheets; Main.dispose → disposeAll. DONE WHEN: A1-A3 hold on run; build green.
- [x] **CP2** — EnemyRenderer contact shadows (R4-R6, hover + lunge). DONE WHEN: A5, A6 by code/run; build green.
- [x] **CP3** — PropRenderer + ShopMachineRenderer contact shadows. DONE WHEN: A4 holds; build green.
- [x] **CP4** — MuzzleLightCatalog, GameMath.muzzleLightFalloff, AtmosphereGrade light term + clock, PlayerController
      weapon-fired listener wired by World; walls + floor/ceiling read it (props/enemies deliberately not yet).
      DONE WHEN: build green.
- [x] **CP5** — props, shop machine, enemies apply the light term; docs. DONE WHEN: A7-A9 on run; build + test at
      baseline; ready for reviewer.
- [x] **CP6** — reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV:** see `.claude/checkpoints/visual-identity-order-1.md` ENV + CP7 notes: build via the desktop MIRROR in the
  session scratchpad; on-screen checks via the scratchpad capture harness (`:capture:capture` with `-Dcap.script`,
  Xvfb :77 + openbox; title needs two taps at world (640,330), boot card CONTINUE at (640,576), forward at (1084,327)).
  Both are scratchpad-only and must be recreated in a fresh container.
- **CP1 handover:** `render/EnemySpriteSheets.get(path, r, g, b)` + `beginLoadBatch()/endLoadBatch()` (EnemyRenderer
  brackets its 9 gets; logs once per session when something was conditioned) + `disposeAll()` (Main.dispose after
  world.dispose). EnemyRenderer no longer owns `loadSheetOrFallback`/`generateFallbackSheet`. Measured on screen:
  "conditioned 9 sheets in 379 ms" (Xvfb/llvmpipe container, incl. PNG decode) — under R3's 400.
  DECISION: cut stays 0.50 for all sheets. The spoke flagged that 4.5-16% of texels sit at alpha 128-254 (they turn
  opaque). Offline composites (scratchpad/halo/*.png: original | cut 0.50 | cut 0.98 over a lit grey) show those are
  anti-aliased EDGE texels with body colour, not a dark glow: 0.50 gives a crisp silhouette with no outline, while a
  near-opaque cut chews the edges ragged. No per-path override table needed.
- **CP2 handover:** `render/ContactShadow` (static): `buildTexture()` (renderer owns + disposes), `alphaAtDepth(mood,
  depthTiles)`, `draw(batch, tex, wallRenderer, projectionColumnCount, projectionColumnWidth, centerWorldX,
  floorLineY, shadowWidth (already × WIDTH_FRACTION_*), depth, alpha)` — per-column quads, same z-test as sprites,
  LEAVES batch colour set (caller restores). `GameMath.contactShadowAlpha(r)`. RenderConstants.CONTACT_SHADOW_*.
  Enemy: floor line captured pre-hover/pre-lunge, width pre-lunge-scale, EYE_TYRANT × 0.55, afterimages unshadowed,
  dead enemies culled before this loop. A5/A6 by code read.
- **CP3 handover:** PropRenderer `contactShadowFractionForTile(char)`: isPropSolid → PROP 0.85; medical/armour/ammo/
  keycard pickup → PICKUP 0.60; everything else (decals, hazards, portal, corpse stains) 0 = none. Ground items:
  credit chips + weapon pickups → PICKUP × CONTACT_SHADOW_HOVER_ALPHA_SCALE (they FLOAT at eye height, so their
  shadow sits well below the sprite; at full alpha it read as stray blobs — DECISION by orchestrator, a natural
  extension of R6's hover rule). Spires none. Shop machine SHOP 0.90. Shadows never recorded as occluders.
  On-screen: verified by pixel sampling (scratchpad shots o2b vs p1): ovals 15-35% darker than the same floor,
  under the floating weapon pickups. NOTE: my first visual read ("pale, misplaced") was WRONG — near-black floor
  makes a faint dark ellipse look pale in a downscaled view; measure pixels before calling a bug.
- **CP4 handover:** `render/MuzzleLightCatalog` (ordinal table, default WARM, melee peak 0; all 14 R7 names exist).
  `AtmosphereGrade`: `pulseMuzzleLight(ItemType)` (restart, no stack), `update(dt)` (World.update, PLAYING only),
  `resetLight()` (in resolveLightingMood). Render-thread: after `evaluate(d, strength)` read `lightAddRed/Green/Blue`
  (zero when idle) — NEVER after `evaluateWeaponTint` (stale). Workers: `lightAddInto(d, out, off)` (snapshot only).
  Walls: `gradeScratch` is 6 floats ([3..5] light). Floor: `rowLightFalloffTable` × frame intensity × colour.
  Channel = clamp01(shade × tint + lightAdd), then red alert. Listener: `PlayerController.setWeaponFiredListener`
  right after the only `playPlayerWeapon` call in `tryFire`; dry fire / railgun charge / reload never reach it.
  Known: a pulse active at death stays frozen in the dying world (screen fades; new World resets) — acceptable.
- **CP5 handover:** Light applied to prop/shop/enemy bodies. DECISION (orchestrator, after on-screen measurement):
  R8's clamp01(s×tint + light) on the MULTIPLIER clamped the light away (+2..3/255 measured). Rebuilt as: base draw
  unchanged (clamped multiplier + red alert); light = a SECOND additive draw (GL_SRC_ALPHA, GL_ONE) of the same
  texture region with colour = lightAdd — i.e. albedo × light on top, only the final colour clamps. Floor/ceiling:
  `GameMath.applyRgbShadeAndLightToPackedRGBA`. Walls: Phase 3 additive pass (2 blend switches/frame), props +
  enemies: 2 switches per LIT sprite, shop reuses the following additive glow state. Peaks raised 0.35/0.35/0.40
  → 1.50/1.50/1.70 (dark R9 materials). Verified on screen by forcing `pulseMuzzleLight` via reflection in the
  capture harness (shots/L_sheet.png): visible warm / cyan / orange splash on near floor+ceiling, no whiteout.
  NOT verified on screen: an actual fire tap (start-room weapons are random per run), enemies lit (A7 enemy part),
  chaingun refresh (A8, code read: restart-not-stack), A9 by construction (HUD/overlays never read the grade).
- **CP6b (reviewer round-1 fixes):** AtmosphereGrade javadoc corrected; floating tile pickups (medical/armour/ammo/
  keycard) shadow at HOVER_ALPHA_SCALE via `contactShadowAlphaScaleForTile`; applyAlphaCut now chunked (16 KB reused
  scratch, `ENEMY_SPRITE_ALPHA_CUT_CHUNK_BYTES`) on a `duplicate()` view with RELATIVE bulk get/put (absolute bulk
  overloads need Android API 34). Per-byte absolute access was tried and measured 2040 ms — rejected. Same-machine
  comparison now (noisy VM): old whole-copy 1209 ms vs chunked 476-582 ms. Nits fixed. Session cut by usage limit
  between round 1 and these fixes; spoke had saved nothing, redone from scratch.
- **CP6:** reviewer round 2 PASS (after 45f26be + be462e6); timing wording corrected in the close-out. STATUS IMPLEMENTED 2026-10-20. Next: visual-identity-order-3 (typography).
