# Session resilience and the checkpoint protocol

**PURPOSE:** how work survives a usage-limit hit, a lost container, or any other interruption.
Read before implementing an idea file, and before writing one.

## The problem

A session can end at any moment — a usage limit, a reclaimed container, a closed window. Three
things are then at risk, and only one is code: uncommitted files; the PLACE in the work (which
step finished, which was half-done, what was next); and THE THINKING — why a thing was done that
way, the exact signature the next step must call, the dead end already ruled out. The last is the
expensive one, and it is nowhere on disk unless somebody wrote it down.

An interruption is normal and expected. The work is arranged so it costs at most ONE CHECKPOINT
of progress — never a session's worth.

## The rule, in one sentence

**At every moment, the work must be resumable from disk by a reader with NO memory of the
conversation.**

- **Durability** — nothing of value exists only in a context window. Commit AND PUSH at every
  checkpoint; the container is ephemeral and an unpushed commit dies with it.
- **Locatability** — one file holds the live state, at a path a fresh session can COMPUTE.
- **Continuity** — the reasoning is handed forward in writing, because the next reader has none.

## What earns a checkpoint file

**Full-lane work** (see "Two Lanes" in `.claude/CLAUDE.md`) — anything with an idea file, and
every ordered file of a part. Always. No exception for "this one looks quick".

**Short-lane work** does not: it is one commit end to end. Commit and push when done, and do not
start a second short-lane change before the first is pushed. If short-lane work GROWS into
full-lane work, writing the checkpoint file is the first thing that happens, before more code.

## Where it lives

```
.claude/checkpoints/<idea-file-basename>.md
```

The idea file's own basename, `.txt` becoming `.md`:

```
.claude/agents/ideas/grenade-launcher.txt          -> .claude/checkpoints/grenade-launcher.md
.claude/agents/ideas/narrative-rework-order-4.txt  -> .claude/checkpoints/narrative-rework-order-4.md
```

The path is DERIVED, so a session told nothing but "continue narrative-rework order 4" can
compute where to look. One ordered file gets one checkpoint file — a part does not share one.

Work run through `/feature` without an idea file uses `.claude/checkpoints/<feature-slug>.md`.

## Two files, two jobs — never duplicate one into the other

| File | Holds | Written by |
|---|---|---|
| `.claude/agents/ideas/*.txt` | THE PLAN — what, why, and the checkpoint BREAKDOWN. Effectively immutable while it is built. | `creative-game-designer`, before work starts |
| `.claude/checkpoints/*.md` | THE STATE — which checkpoints are reached, in which commit, what the next one must know. | whoever implements, continuously |

The checkpoint file never restates the spec: it refers to it by rule and criterion number (R4,
A11) and stays short enough to read in full at the start of every resumed session. Its shape is
fixed by `_TEMPLATE.md` in this folder — copy it rather than inventing a layout.

An idea file written before this protocol has no `CHECKPOINTS` section. The implementer derives
the breakdown from its TECHNICAL NOTES / IMPLEMENTATION PLAN and writes it straight into the
checkpoint file. A missing checkpoint list is not an open question and does not send the file
back to the designer.

## How big is a checkpoint

Small enough that losing it is cheap:

- one coherent artifact, usually one subsystem, usually one spoke dispatch;
- roughly 30–45 minutes of work — more than that, split it;
- it leaves `./gradlew build` GREEN (and `./gradlew test` green — the existing suite is the gate).
  One honest exception: a checkpoint may deliberately land an unregistered catalog row, an unwired
  renderer or an unused constant — the checkpoint file then says so and names the checkpoint that
  completes it. A red build nobody warned the next session about is never acceptable.

Between 4 and 10 checkpoints is normal for one idea file. Two means they are too big; twenty
means the file should have been split into `<part>-order-<n>.txt` files.

**Write "done" as a STATE, not an action** — that is what lets a resuming session CHECK whether
the step already happened instead of redoing it.

- good: *"`EnemyType.LURKER` exists with its stats in `EnemyConstants`, spawns from symbol `(`,
  and `./gradlew build` is green"*
- bad: *"add the lurker enemy"*

## The commit protocol — not negotiable

1. The checkpoint file is created and committed FIRST, as **CP0**, before any other file changes.
2. One commit per checkpoint, **pushed immediately**.
3. **The tick ships in the same commit as the work.** Ticking separately re-creates the exact
   ambiguity the file exists to remove.
4. Message prefix `<idea-basename>/CPn: <what changed>`.
5. Never leave more than one checkpoint's work uncommitted. A checkpoint that turns out bigger
   than it looked is SPLIT — `CP4a`, `CP4b` — never held.
6. Never `git add -A`. Stage named paths only, so nothing rides along.

## The handover note — where the thinking survives

Closing a checkpoint means writing, in the same commit, what the NEXT one cannot proceed without:

- the exact class, method signature, `register()` call, constant or string id the next step must
  use, where it lives, and what it expects;
- a decision taken inside this checkpoint that the idea file did not settle, and why;
- a dead end already ruled out, so it is not walked into twice;
- something learned about the ENVIRONMENT that cost time (a Gradle quirk, a missing SDK, a slow
  `balanceSim` run);
- a deliberate temporary state, and which checkpoint resolves it.

Not worth writing: a narration of what the diff already shows.

## Resuming — the first thing a session does

Before any edit, when picking up work already under way:

1. Read the checkpoint file in full — the source of truth for what is done.
2. `git log --oneline -20` — do file and history agree?
3. `git status` — is the tree clean?
4. Reconcile BEFORE continuing:

| Situation | Action |
|---|---|
| checkpoint ticked, no commit for it | the tick is wrong. Check the code: genuinely there → commit it; not there → untick and redo. |
| commit exists, checkpoint unticked | the cut landed between the two. Verify the commit against the step's DONE WHEN and tick it. Do not redo it. |
| tree is dirty | the cut landed mid-checkpoint; that work is UNVERIFIED. Finish it to the boundary and verify, or `git restore` it and redo. Never build on it unchecked; never discard it unread. |
| they agree, tree clean | start the first unticked step. |

5. **When the checkpoint file and the history disagree, THE HISTORY WINS.** The file is a
   description; the commits are the thing itself.

## The resume report — what the owner is told

The first message of a resumed session opens with four lines, not a paragraph:

- that the interruption was expected and the work was arranged for it;
- the last checkpoint reached, by number and commit sha;
- what was lost, honestly — normally "nothing committed was lost"; if a partial checkpoint was
  discarded, which one and what it held;
- the step being resumed now.

It is a status line, not a reassuring formula. It is owed on ANY resumption of in-flight work — a
session usually cannot tell a usage limit from a compaction from a fresh start.

## Spokes and the hub

The hub (`orchestrator`, or the main thread driving the pipeline) owns every commit. A spoke does
not commit; it reports back and the hub commits that work as the checkpoint, with the tick — one
writer, so the history never interleaves two half-finished steps. So ONE CHECKPOINT ROUGHLY EQUALS
ONE SPOKE DISPATCH, and the delegation contract asks each spoke for the handover facts.

## What this is not

Instructions, not machinery: no script, no hook — one markdown file per idea file, plus the
discipline of committing at its boundaries. Not a licence to skip splitting an oversized idea into
ordered files. And no other bar moves: the build, the testing policy, the `docs/` updates
CLAUDE.md requires and the `reviewer` gate are owed exactly as before. A reached checkpoint is not
"done" — the idea file is done when `reviewer` passes it.
