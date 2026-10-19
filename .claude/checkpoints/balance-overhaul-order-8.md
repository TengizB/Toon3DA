# CHECKPOINT — balance-overhaul order 8: the content balance contract

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-8.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-b6b07eb5-sqyr5p`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-8.txt` — the spec. Its rules (G1-G5, K1-K18,
E1-E6, B1-B5) and acceptance criteria (A1-A8) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `util` (BalanceSchema RuleKind.ADMISSION, BalanceReport ADMISSION table) + content
declaration hardening (`enemy`, `entity`, `progression`, `level`) + docs + agent/process files.
**TESTS:** balance-bearing — extend `util/BalanceAuditTest` only (one method per C-rule). No new test
files. Both gates (`./gradlew test`, `./gradlew balanceSim`) at every checkpoint that moves code.
**STARTED:** 2026-10-19
**BASELINE:** branched from `de13c3e` (main, floor-twists merged, PR #308). Baseline gates recorded in NOTES at CP1.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-8/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-8/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- SCOPE OUT: no new content, no retuned number. An existing item failing a C-rule is fixed by a
  declaration or pricing row; a genuine out-of-band value becomes a WAIVER (reason + expiry).
- G5: a missing declaration / pricing row is never waivable.
- No new test files; one BalanceAuditTest method per C-rule; COVERAGE folded into ADMISSION (strictly stronger).
- Negative drill (E6) is run locally and REVERTED — dummy content is never committed.
- Every number in BalanceConfig, every formula in GameMath, register()/enum rows — never a switch.
- The audit stays headless (no LibGDX in anything a C-rule bootstraps).
- Process-file edits (CLAUDE.md, agents) are short pointers to the authority doc (single source).
- No model identifiers in commits or docs.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes; idea STATUS: IN PROGRESS.
- [ ] **CP1** — RuleKind.ADMISSION with C-ENEMY, C-FAMILY, C-VERB, C-BOSS; COVERAGE enemy/verb checks folded in.
      DONE WHEN: both gates green on current content.
- [ ] **CP2** — C-WEAPON, C-ABILITY, C-PERK, C-AMMO live; COVERAGE weapon/ammo checks folded in.
      DONE WHEN: both gates green.
- [ ] **CP3** — C-ITEM, C-STATUS, C-HAZARD, C-ROOM, C-GENERATOR live.
      DONE WHEN: both gates green.
- [ ] **CP4** — C-NODE, C-EVENT, C-SHOP, C-REGION live; BalanceReport ADMISSION table prints.
      DONE WHEN: A1, A7 hold; both gates green.
- [ ] **CP5** — declaration hardening (E3); C-CONSTANT with GRANDFATHERED / UNBANDED.
      DONE WHEN: A3, A4 hold; both gates green.
- [ ] **CP6** — negative drill for all 18 C-rules, messages recorded in the handover below.
      DONE WHEN: A2 holds (nothing from the drill committed).
- [ ] **CP7** — authority doc ADD CONTENT (G, K, X), knowledge doc, weapon guide, enemy doc,
      route/tileset/story cross-links.
      DONE WHEN: A5 holds.
- [ ] **CP8** — CLAUDE.md rule + row, the seven agent files, _TEMPLATE.txt.
      DONE WHEN: A6, A8 hold; both gates green.
- [ ] **CP9** — reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **SERIES ORDER (decided at CP0):** orders 4 (reviewer close pending) and 7 (waiting on owner
  playtest notes) are still IN PROGRESS. The owner invoked `/implement balance-overhaul-order-8`
  explicitly; order 8 adds rules and changes no number, and order 7 only tunes numbers, so it is built
  now. Per AS1, if order 7 later moves a rule, it changes here in the same commit; any BalanceConfig
  field order 7 adds after CP5 must be a rule subject or UNBANDED (C-CONSTANT is doing its job).
