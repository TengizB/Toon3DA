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
- [ ] **Q2** — per-tier floor backdrop divisor (HD 4 = performance tier, FULL_HD 2 = quality tier).
      DONE WHEN: Full HD backdrop is 960x540; frame cost measured; build green.
- [ ] **Q3** — material LOD (box-filtered mip chain 64/32/16/8 per material, level chosen per backdrop ROW from the
      texel footprint). DONE WHEN: far floor/ceiling no longer speckle in captures; build green.
- [ ] **Q4** — screens of all five moods at the new default, docs updated, reviewer PASS.

## NOTES CARRIED FORWARD

- **Measured (Q2 decision):** capture harness, uncapped, 4-core llvmpipe VM, start room: divisor 4 = 16.1 ms/frame,
  divisor 2 = 18.2 ms/frame at FULL_HD → +2 ms for 4x floor pixels (worker-threaded). HD keeps divisor 4 so a slow
  phone has a cheaper tier. Root cause of the owner's "too pixelated": the floor/ceiling backdrop is CPU-filled at
  1/4 resolution (HD 320x180, FULL_HD 480x270) and Nearest-upscaled; plus no mipmapping, so the fine order-1
  materials speckle at distance.
