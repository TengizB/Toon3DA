# CP4 CONTRACT — balance-overhaul-order-5: real rarity (ability prune, tier counts, budgets, tags)

Spec: `/home/user/Toon3DA/.claude/agents/ideas/balance-overhaul-order-5.txt` — rules B5, B6, acceptance A4,
OVERRIDE CLAUSE ("R-ABILITY tier budgets (UNCOMMON 6 / RARE 12 / EPIC 20 / LEGENDARY 30) and WeaponTier
ability slot counts -> B5 counts and budgets; ability magnitudes re-tuned; pruned abilities removed from the
catalogue"). Live state: `/home/user/Toon3DA/.claude/checkpoints/balance-overhaul-order-5.md` (NOTES).
Project rules: `/home/user/Toon3DA/.claude/CLAUDE.md`. Weapon guide: `docs/weapon-creation-guide.txt`.

## What to build
1. **Counts (B5)** — `entity/WeaponTier`: ability counts COMMON 0, UNCOMMON 1, RARE 2, EPIC 3 (one of them
   from a MAJOR list), LEGENDARY 3 + its signature (replaces abilitySlots 0/1/2/3/5 and
   `standardSlotCount()`'s "4 standard + 1 signature"). Counts live in BalanceConfig SECTION 24 (data),
   WeaponTier reads them. The roller GUARANTEES the count (today it may drop abilities that do not fit the
   budget — after this change a tier always rolls exactly its count; if the budget cannot fit the count,
   that is a budget/magnitude error caught by the audit, not a silent short roll).
2. **Budgets (B5)** — SECTION 24: UNCOMMON 12, RARE 25, EPIC 40, LEGENDARY 60 PP, tolerance +/-20% (the
   SECTION 15 `TIER_ABILITY_PP_BUDGET_*` constants are REPLACED — delete or re-point, one definition only).
   A tier's roll must land inside [budget*(1-tol), budget*(1+tol)] (both a floor and a ceiling now — the
   point of B5 is that rarity is EXCITING, so a RARE that rolls 3 PP is a violation).
3. **MAJOR list** — a per-ability data flag (`isMajor()` / field), the EPIC tier draws >= 1 MAJOR. Choose
   the MAJOR set from the priced valuation (the biggest felt abilities), record it.
4. **Prune (B6)** — `ABILITY_MIN_FELT_PP = 8` (SECTION 24). Every ability whose priced PP
   (`GameMath.abilityPowerPoints` on `WeaponRoller.buildAbilityInstance(ability, level)`) at the EXPECTED
   weapon level for its drop depth is below 8 is CUT or MERGED into a stronger one. "Expected weapon level
   for its drop depth" = the on-curve weapon level (`expectedPlayerAtDepth(d).weaponLevel`) at the
   shallowest depth whose region can drop the lowest tier that may roll it — define precisely, record it.
   The survivors are RE-TUNED (magnitudes, in their existing constants) so the tier budgets are met with
   the B5 counts. Removing an enum constant: grep every use (HUD, inspect overlay, ItemWindow,
   AbilityWindow, shop, sim, Weapon behaviour switches/ifs, legendary signature tables) and remove the
   behaviour code with it. No save migration (runs are not saved mid-run). Record the CUT/MERGE list and
   the per-ability before/after PP in the handover (docs in CP6 need it).
   Abilities with real weapon behaviour (e.g. BURST_FIRE, OVERPENETRATION, STAGGER_ROUNDS) — keep their
   behaviour intact if they survive; if one is merged, the stronger survivor gains its effect as data.
5. **Ability tags (B6)** — every surviving ability carries a `BuildTag` (nullable only if genuinely
   tagless — prefer a tag; e.g. INCENDIARY -> PYRO, STAGGER_ROUNDS -> CLOSE_QUARTERS, SCAVENGER_ROUNDS ->
   SCAVENGER, LIFESTEAL -> VITALITY or BLADE…). `WeaponAbility` is in `entity`; BuildTag is in
   `progression` — check for an import cycle (progression already imports entity.DamageClass); if
   entity -> progression is acceptable in this codebase, fine; otherwise hold the tag as data keyed in a
   small table in `progression` (e.g. `AbilityTags.tagOf(WeaponAbility)` built once, EnumMap, no switch).
   Record which.
6. **Synergy** — extend `World.carriedBuildTags()` and `SimWorld.carriedBuildTags()` so carried weapons'
   ability tags count for B3 synergy (each carried weapon's AbilityInstance[] -> tags).
7. **R-ABILITY updated (`util/BalanceSchema.abilityBudgetResults`)** — every surviving ability priced
   and >= ABILITY_MIN_FELT_PP at its expected level; tier budgets monotonic; budgets = B5 values; plus
   (A4) a roll sweep: 30 seeds x 5 tiers x 3 depths (pick one depth per early/mid/deep region, e.g. 3,
   12, 22 — record) -> every roll has exactly the tier's count, EPIC has >= 1 MAJOR, LEGENDARY has its
   signature, and total PP lies in the tier band. Put the sweep in BalanceSchema as rule rows (so the
   report can print it) and assert it from the EXISTING audit methods
   `everyAbilityIsPricedWithinItsTierBudget` / `everyBudgetedRollStaysWithinItsTierCeiling` — UPDATE
   those two methods to the new rule (rename to say so if their names lie now). No new test method is
   needed; if one is truly needed, it goes in the existing BalanceAuditTest and you must say why in one
   line.
8. **Order-2 rarity damage multiplier stays** (B5). Rarity drop tables (which tiers drop where) are NOT
   changed.
9. **BalanceReport** — the ability table shows PP at expected level, MAJOR flag, tag, CUT/kept; a tier
   table shows count / budget band / sampled min-max PP.

## Boundaries
- No render work beyond keeping existing UI compiling (removed abilities disappear from lists by data).
  The inventory BUILD page / chips are CP5.
- No new weapons, no new mechanics (SCOPE OUT). A merged ability's added effect must be an existing
  mechanic.
- Never-a-switch on WeaponAbility for new logic; naming per CLAUDE.md; constants in BalanceConfig.
- No new test files; update existing tests only where the rule legitimately changed (say which).
- Do not commit; do not `git add`. Use the Edit tool for Java edits (the per-file code-reviewer hook runs).

## Build
Mirror only: `cd /tmp/claude-0/-home-user-Toon3DA/48c7ae85-305d-55bc-9e8b-bb4641640da1/scratchpad/mirror &&
./gradlew :core:test` then `./gradlew :lwjgl3:compileJava` then `./gradlew :core:balanceSim`. Baseline =
the CP3 numbers in the checkpoint NOTES (only StoryBarkTest red; balanceSim exit 0). Weapon power moves
with richer abilities: report every S-* line that moves and every R-* rule that needed attention (R-ROLE,
R-LADDER, R-BOSS-*, R-CREDITS read weapon power — if one goes red, fix by ability magnitudes, never by
changing those rules; if impossible, stop and report numbers).

## Handover
Files; CUT/MERGE list with before/after PP; MAJOR set; tag table; expected-level definition; sweep depths;
budgets/counts constants; tests updated; gate numbers; anything left for CP5/CP6.
