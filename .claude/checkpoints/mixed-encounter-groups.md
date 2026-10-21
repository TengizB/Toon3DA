# CHECKPOINT — mixed encounter groups (no rooms of one enemy type)

**PURPOSE:** the live resume record for `.claude/agents/ideas/mixed-encounter-groups.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `claude/enemy-spawn-variety-7ftubh`
**IDEA:** `.claude/agents/ideas/mixed-encounter-groups.txt` — the spec. Rules V1-V9 and criteria A1-A13
are referred to by number below and are NOT repeated here.
**LANE:** full lane / `level` (planner, templates, placer) + `util` (BalanceConfig, BalanceSchema) + docs.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` with ONE method; both gates
(`./gradlew test` + `./gradlew balanceSim`); `LevelGeneratorSnapshotTest` data may be regenerated.
**STARTED:** 2026-10-21
**BASELINE:** branched from `af7bad6`. Mirror build (`/tmp/toon3d-runtime/mirror`, see run-game skill):
`:core:test` has ONE pre-existing failure (`narrative.StoryBarkTest`); `:core:balanceSim` exit 0
(baseline summary kept at /tmp/claude-0/baseline-summary.txt — not committed, container-local).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `mixed-encounter-groups/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `mixed-encounter-groups/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- register() rows / per-constant enum bodies — never a switch on template id, SlotFill or EncounterBehaviour.
- Every balance number in BalanceConfig, declared in BalanceSchema.CONSTANT_SUBJECTS (never GRANDFATHERED).
- Testing Policy: only BalanceAuditTest gets the one new method; never weaken an existing test.
- V1, V6, V7 are never loosened to make a gate pass (the owner's request).
- Planner stays pure and deterministic (all draws from the supplied Random).

## STEP LEDGER

- [x] **CP0** — this file + the idea file, committed and pushed before anything else changes.
- [ ] **CP1** — the seven GROUP_* numbers in BalanceConfig + CONSTANT_SUBJECTS -> DENSITY.
      DONE WHEN: `:core:test` shows only the baseline failure (C-CONSTANT green).
- [ ] **CP2** — EncounterBehaviour, SlotFill, Slot.fill, 8 template rows, instantiate V1-V3.
      DONE WHEN: build green; no `sameType`, no PACK.
- [ ] **CP3** — planner V4 / V5 / V6. DONE WHEN: scratch measurement meets A1-A5, A7-A8 (figures below).
- [ ] **CP4** — placer V7 + brood first-contact skip; densityVarietyResults + "room variety"; the one test.
      DONE WHEN: `:core:test` shows only the baseline failure.
- [ ] **CP5** — balanceSim green (tuning recorded), snapshot data regenerated.
- [ ] **CP6** — docs updated; A13 run check noted; reviewer gate; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- Scratch measurement harness (not committed): /tmp/claude-0/meas/Measure.java — compile against
  `/tmp/toon3d-runtime/mirror/core/build/classes/java/main` + the gdx jar.
