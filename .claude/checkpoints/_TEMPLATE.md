# CHECKPOINT — <idea file or part, in a few words>

<!--
COPY THIS FILE TO .claude/checkpoints/<idea-file-basename>.md AND COMMIT IT
BEFORE ANY OTHER FILE CHANGES. That first commit is CP0.

The rules are in .claude/checkpoints/README.md.
This is the shape; keep the headings, delete this comment.

It holds the STATE. The idea file holds the PLAN and does not change while
the work is being built. Never restate the spec here — refer to it by rule
and criterion number. This file is read IN FULL at the start of every
resumed session, so keep it short enough that that stays true.
-->

**PURPOSE:** the live resume record for `.claude/agents/ideas/<name>.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `<branch>`
**IDEA:** `.claude/agents/ideas/<name>.txt` — the spec. Its rules (R…) and acceptance criteria (A…)
are referred to by number below and are NOT repeated here.
**LANE:** full lane / which subsystems (e.g. `enemy` + `render` + `narrative`).
**TESTS:** per the CLAUDE.md Testing Policy — balance-bearing (extend `BalanceAuditTest` /
`BalanceSimTest`, run `./gradlew balanceSim`) or none (say so, so it does not read as an oversight).
**STARTED:** YYYY-MM-DD
**BASELINE:** branched from `<sha>`; were `./gradlew build` and `./gradlew test` green before
anything changed? A red build after that point is ours.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `<idea>/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `<idea>/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted; split a big one
into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

The CLAUDE.md rules that actually bite here (touch-only, turn-based, cardinal lines, register()
not switch, no render allocations, constants + GameMath, tile-symbol rule, story string ids…) and
anything the spec forbids. One line each.

## STEP LEDGER

- [ ] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — <what it delivers>.
      DONE WHEN: <a STATE that can be checked, never an action>.
- [ ] **CP2** — …

## NOTES CARRIED FORWARD

What the next checkpoint cannot proceed without, appended as each one closes: the exact
class/method/constant/string id it must use and what it expects · a decision taken here that the
idea file did not settle, and why · a dead end already ruled out · something learned about the
environment that cost time · a deliberate temporary state and which checkpoint resolves it. Not a
narration of what the diff already shows.
