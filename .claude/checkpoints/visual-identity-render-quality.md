# CHECKPOINT — Visual identity follow-up: render quality (Full HD default, finer floor, material LOD)

**PURPOSE:** live resume record for an OWNER-DIRECTED follow-up to visual-identity order 1 (2026-10-20): "make Full
HD the default and check the changed things are not too pixelated; adjust if needed; give screens". No idea file —
the owner's request is the spec; decisions recorded below. Order 1/2 are IMPLEMENTED and untouched in scope.

**BRANCH:** `ccr-1c332c6a-z0q18l`   **LANE:** full (narrative settings default + render + util + docs)
**TESTS:** none (not balance-bearing). **BASELINE:** `57abdbc`; 480 tests, 1 pre-existing failure (StoryBarkTest).

## STEP LEDGER

- [x] **Q1** — RenderResolution.DEFAULT = FULL_HD; StorySettings falls back to it. Renderer field defaults stay HD
      (their comment ties them to a byte-identical rule; World always pushes the setting). DONE WHEN: a fresh save
      renders 1920 columns; build green.
- [x] **Q2** — per-tier floor backdrop divisor (HD 4 = performance tier, FULL_HD 2 = quality tier).
      DONE WHEN: Full HD backdrop is 960x540; frame cost measured; build green.
- [x] **Q3** — material LOD (box-filtered mip chain 64/32/16/8 per material, level chosen per backdrop ROW from the
      texel footprint). DONE WHEN: far floor/ceiling no longer speckle in captures; build green.
- [x] **Q4** — screens of all five moods at the new default, docs updated, reviewer PASS.

## NOTES CARRIED FORWARD

- **Measured (Q2 decision):** capture harness, uncapped, 4-core llvmpipe VM, start room: divisor 4 = 16.1 ms/frame,
  divisor 2 = 18.2 ms/frame at FULL_HD → +2 ms for 4x floor pixels (worker-threaded). HD keeps divisor 4 so a slow
  phone has a cheaper tier. Root cause of the owner's "too pixelated": the floor/ceiling backdrop is CPU-filled at
  1/4 resolution (HD 320x180, FULL_HD 480x270) and Nearest-upscaled; plus no mipmapping, so the fine order-1
  materials speckle at distance.
- **Q2+Q3 (one commit — both edit FloorCeilingRenderer's table builder, not separable):** `RenderResolution` tiers
  carry `floorBackdropScaleDivisor` (HD 4 / FULL_HD 2 = `FLOOR_BACKDROP_SCALE_DIVISOR_FULL_HD`). Mip chain 64/32/16/8
  per material in `SurfaceMaterialRegistry.getPixels(id, level)` (2x2 box filter, cached). Per-ROW level table from
  `GameMath.surfaceMaterialMipLevel(max(screen-X footprint, DEPTH footprint) × bias 1.0)`; the depth term is what
  matters (first cut used screen-X only and stayed level 0 everywhere — rejected). Table forced non-decreasing toward
  the horizon (the 15-tile clamp band has zero depth delta and would otherwise snap back sharp). FULL_HD levels:
  rows 0-176 L0 (to 2.9 tiles), L1 to 4.0, L2 to 5.7, L3 beyond. Frame cost with LOD: 18.5 ms (vs 18.2 without).
  Captured: shots/q_<mood>.png, cmp3.png (HD before vs FULL_HD after). Near rows keep crisp level-0 texels on
  purpose: the 64-texel materials are pixel art, matching the nearest-sampled walls.
- **Q4:** screens delivered (shots/before_after.png, floor_closeup.png). Reviewer PASS WITH FIXES, no blockers; fixed:
  boxFilterHalf single-letter locals renamed (bottomLeftTexel…), stale "byte-identical" comments reworded (HD keeps the
  historic DIMENSIONS; its pixels now differ because of LOD), unused getRowMipLevel removed, wall-renderer-guide tier
  table (FULL_HD default, 960x540). HONEST COST (replaces the "+2 ms" framing): old HD default 13-14.5 ms/frame vs new
  FULL_HD default 18-19.4 ms on the llvmpipe VM — ~+4.5 ms incl. the extra wall columns; the finer backdrop alone ~+2
  ms. OPEN for the owner: a frame-time capture on a real Android phone (not possible in this container). Not done
  (nits, accepted): renaming FLOOR_BACKDROP_SCALE_DIVISOR to _HD; the once-per-floor HD-then-FULL_HD rebuild.
