# CP6 CONTRACT — balance-overhaul-order-5: sim build-bias, S-BUILD, BUILD REPORT, all docs

Spec: `.claude/agents/ideas/balance-overhaul-order-5.txt` — TECHNICAL NOTES (sim line), A3, A6, A7, OVERRIDE
CLAUSE, DECISIONS D3-D6. Live state: `.claude/checkpoints/balance-overhaul-order-5.md` (NOTES CARRIED FORWARD:
every API, decision, flag). Rules: `.claude/CLAUDE.md` (Testing Policy: extend `sim/BalanceSimTest` only; docs).
Balance contract: `docs/game-balance-authority.txt` SECTION 7 (sim bands + CHANGE PROTOCOL).

## Part A — sim (balance-bearing; java-architect)
1. **Build-bias parameter** on `TacticalPolicy` (`BuildBias` enum or data: CLOSE / MARKSMAN / PYRO_DEMOLITION /
   BULWARK / NONE; each = a preferred tag set; NONE = today's CP2 behaviour). `choosePerk`: prefer the bias tags
   (then synergy, then most-ranked); `chooseKeystone`: the bias tag's keystone if offered. Should the bias also
   steer which weapon TACTICAL keeps/equips? Only if trivially available through the existing switch logic —
   otherwise leave weapons alone and say so. Plain TACTICAL = NONE, unchanged.
2. **BalanceSimulator**: add the four biased TACTICAL variants to the matrix as extra rows
   (`TACTICAL-CLOSE`, `TACTICAL-MARKSMAN`, `TACTICAL-PYRO-DEMO`, `TACTICAL-BULWARK`). Mind runtime: the slow gate
   must stay reasonable — if 200 seeds x 4 extra rows is too slow, use a SECTION 18 `SIM_BUILD_SEED_COUNT`
   (e.g. 60) and say so.
3. **S-BUILD band** (`BehavioralBands` + `RuleKind.SIM_BUILD` + SECTION 18 constants):
   - depth part: the four biased variants' median death depths within `SIM_BUILD_DEPTH_SPREAD` (0.25) of each
     other (max/min <= 1.25) — DEPTH-DERIVED: REPORTED (waived) while the navigation waiver on SIM_FAIR stands,
     using the exact same waiver mechanism + expiry text that waives SIM_FAIR TACTICAL median depth;
   - dominant-tag part (ENFORCED): plain TACTICAL's end-of-run dominant tag (PlayerBuild.highestRankTag at death /
     run end; runs with no perk picked are excluded and the count of excluded runs is printed) is no single tag on
     more than `SIM_BUILD_MAX_DOMINANT_TAG_SHARE` (0.40) of seeds. Guard against an empty sample like S-HEAVY does
     (minimum sample constant). If this fails honestly (TACTICAL leans into synergy, so its first gun decides),
     do not weaken it — report the distribution, and the hub decides.
4. **BalanceSimTest**: one new @Test method in the EXISTING file (`buildBiasesStayCloseAndNoTagDominates`),
   same assertBandsHold idiom. (This is the S-BUILD gate the spec names; balance-bearing, allowed.)
5. **SimReport BUILD REPORT**: per-bias outcomes (median depth, stalled, mean level reached); perk pick rate per
   offer (picked / offered per Perk); keystone pick rates; dominant tag at death distribution (plain TACTICAL).

## Part B — docs (A7)
- `docs/xp-level-progression.txt`: rewrite the level-up section — perks (tags, ranks, catalog), offers (B3
  synergy/pivot), keystones (levels, B4, catalog with gain/cost), the BUILD page; remove upgrade-card text.
- `docs/game-balance-authority.txt`: R-CARD / R-CARD-BREAKPOINT / total-PP invariant recorded REPLACED (override
  record: name, new rule, date); full R-PERK (PK1/PK2), R-KEYSTONE, R-CARRY text with D3/D6 waivers + expiries;
  R-ABILITY restated (B5 counts/budgets, ABILITY_MIN_FELT_PP, MAJOR rank, sweep) as override of the old tier
  budgets; S-BUILD band in SECTION 7; replace the stale UPGRADE CARDS living table with PERKS / BUILDS / ABILITY
  / TIERS tables (generate from `BalanceReport` output — run it); change log entry.
- `docs/game-balance-knowledge.txt`: SECTIONS 5 and 6 rewritten (level-up = perks/keystones; weapon abilities =
  pruned catalog, tiers); known-problems: add the flags from NOTES (IRON BULWARK ordinary-hit reflect vs heavy-only
  valuation; Double-Barrel redundant BURST_FIRE MAJOR; shop tier-up price doubling; unverified valuation model
  inputs; VITALITY at ~1% carry headroom; dead temp-armor pool / Trigger.ON_RELOAD).
- `docs/weapon-creation-guide.txt`: ability section — ranks (STANDARD/MAJOR/SIGNATURE), BuildTag per ability,
  tier counts/budgets, ABILITY_MIN_FELT_PP, how to add an ability now.
- `.claude/CLAUDE.md`: Project Structure `progression/` line (PerkDeck, Perk, Keystone, PlayerBuild, BuildTag
  replacing UpgradeCard*); the `sim` package row (S-BUILD -> eleven S-* bands); nothing else.

## Boundaries
No gameplay/runtime changes outside sim policies. No new test files. Do not commit / `git add`. Use the Edit
tool for Java.

## Verify
Mirror: `:core:test` (only StoryBarkTest red), `:lwjgl3:compileJava`, `:core:balanceSim` exit 0 (report every S-*
line incl. the new S-BUILD lines and runtime of the slow gate).

## Handover
Files; constants; S-BUILD numbers + dominant-tag distribution; BUILD REPORT excerpt; docs touched; anything for
the reviewer.
