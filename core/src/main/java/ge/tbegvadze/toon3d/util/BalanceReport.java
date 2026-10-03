package ge.tbegvadze.toon3d.util;

/**
 * DEV-ONLY balance auditor — the human-readable VIEW of {@link BalanceSchema}. Prints the
 * "living table" of every current weapon and enemy with its computed Power Score / Threat
 * Points, flags any content that falls OUTSIDE its role band, checks the golden ratio, and
 * lists the ACTIVE WAIVERS. This is the prose companion to
 * {@code docs/game-balance-authority.txt}: the numbers here come straight from
 * {@link BalanceConfig} through {@link GameMath} and the registries in {@link BalanceSchema},
 * so they cannot drift from the real game values — when {@code BalanceConfig} changes, rerun
 * this and regenerate the doc tables.
 *
 * <p>The report can WARN but cannot GATE. Enforcement lives in {@code BalanceAuditTest}
 * (core test source set), which runs the same schema under {@code ./gradlew test} and fails
 * the build on any unwaived violation.
 *
 * <p>This class is NOT part of the game loop. It touches no LibGDX render state and only
 * calls the pure contract formulas in {@link GameMath}, so it runs headless:
 * <pre>./gradlew core:run -PmainClass=ge.tbegvadze.toon3d.util.BalanceReport</pre>
 * (or run {@link #main(String[])} directly from an IDE). It exists purely to keep the
 * balance contract honest — the table can't lie.
 */
public final class BalanceReport {

    private BalanceReport() {}

    public static void main(String[] commandLineArguments) {
        printHeader();
        printWeaponTable();
        System.out.println();
        printEnemyTable();
        System.out.println();
        printWaiversTable();
        System.out.println();
        printBlockTable();
        System.out.println();
        printModifiersTable();
        System.out.println();
        printMoveSetTable();
        System.out.println();
        printUpgradeCardTable();
        System.out.println();
        printXpPacingTable();
        System.out.println();
        printScarcityTable();
        System.out.println();
        printSupplyDensityTable();
        System.out.println();
        printEliteRewardTable();
        System.out.println();
        printCreditEconomyTable();
        System.out.println();
        printShopPricingTable();
        System.out.println();
        printEncounterTable();
        System.out.println();
        printRegionDangerTable();
        System.out.println();
        printLadderTable();
        System.out.println();
        printAbilityPricingTable();
        System.out.println();
        printHazardTable();
        System.out.println();
        printTelegraphAudit();
        System.out.println();
        printBossRulesetTable();
        System.out.println();
        printRouteEconomicsTable();
        System.out.println();
        printTrajectoryTable();
        System.out.println();
        printLegend();
    }

    /**
     * ROUTE ECONOMICS (order 7) — the priced map: every node type, affix and mystery outcome with its
     * threat, its resource delta, its EV, and the risk pips that EV derives.
     */
    private static void printRouteEconomicsTable() {
        int depth = 5;
        ge.tbegvadze.toon3d.route.NodeEconomicsRegistry ledger = BalanceSchema.routeLedger();
        ge.tbegvadze.toon3d.route.RouteEconomicsModel.ModelFloor floor = BalanceSchema.routeModelFloor();
        ge.tbegvadze.toon3d.route.RouteEconomicsModel.NodePrice combat =
                ge.tbegvadze.toon3d.route.RouteEconomicsModel.standardCombat(ledger, floor, depth);
        System.out.println("ROUTE ECONOMICS (order 7) — the node EV ledger at depth " + depth
                + "; EV in power points, threat in TP. permanent-power weight "
                + String.format("%.1f", BalanceConfig.ROUTE_PERMANENT_POWER_WEIGHT)
                + ", risk " + String.format("%.1f", BalanceConfig.ROUTE_RISK_POWER_POINTS_PER_STANDARD_FLOOR)
                + " PP per standard floor");
        System.out.printf("%-24s %8s %8s %8s %8s %8s %-11s %-6s%n",
                "subject", "threat", "resDelta", "progress", "reward", "EV", "pips", "in?");
        System.out.println("------------------------------------------------------------------------------------");
        for (ge.tbegvadze.toon3d.route.NodeEconomics row : ledger.all()) {
            ge.tbegvadze.toon3d.route.RouteEconomicsModel.NodePrice priced =
                    ge.tbegvadze.toon3d.route.RouteEconomicsModel.price(ledger, row, floor, depth);
            String pips = "-";
            String verdict = "-";
            if (row.kind() == ge.tbegvadze.toon3d.route.NodeEconomics.Kind.NODE) {
                int derived = BalanceSchema.derivedDangerTierIndexOf(ledger, floor, row);
                ge.tbegvadze.toon3d.route.DangerTier declared =
                        ge.tbegvadze.toon3d.route.RouteRegistries.nodeTypes().get(row.nodeType()).dangerTier();
                pips = ge.tbegvadze.toon3d.route.DangerTier.values()[derived].name();
                verdict = declared.ordinal() == derived ? "OK" : "MISMATCH";
            }
            System.out.printf("%-24s %8.0f %8.2f %8.2f %8.2f %8.2f %-11s %-6s%n",
                    row.id(), priced.threatCost, priced.resourceDeltaPowerPoints, priced.progressPowerPoints,
                    priced.rewardPowerPoints, priced.expectedValue, pips, verdict);
        }
        System.out.println("------------------------------------------------------------------------------------");
        System.out.printf("  standard COMBAT node EV = %.2f. calm discount band %.0f-%.0f%%; risk premium band %.2f-%.2f;"
                        + " mystery EV tolerance ±%.0f%%%n",
                combat.expectedValue, BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MIN * 100f,
                BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MAX * 100f, BalanceConfig.ROUTE_RISK_PREMIUM_MIN,
                BalanceConfig.ROUTE_RISK_PREMIUM_MAX, BalanceConfig.ROUTE_MYSTERY_EV_TOLERANCE * 100f);
        System.out.println("  AFFIX rows are MULTIPLIERS folded onto their host ELITE node; MYSTERY rows are the hidden table.");
        for (BalanceSchema.RuleResult result : BalanceSchema.riskPremiumResults()) {
            System.out.printf("  risk premium %-26s %5.2f  %s%n", result.subject, result.value,
                    result.satisfied ? "OK" : "OUT-OF-BAND");
        }
        for (BalanceSchema.RuleResult result : BalanceSchema.calmCostResults()) {
            System.out.printf("  %-38s %5.2f  %s%n", result.subject, result.value,
                    result.satisfied ? "OK" : "OUT-OF-BAND");
        }
    }

    /**
     * TRAJECTORY (order 7) — the JOURNEY as the audited unit: the worst cumulative reading each path
     * policy produces over the seed sweep, i.e. the game's real difficulty range end to end.
     */
    private static void printTrajectoryTable() {
        System.out.println("TRAJECTORY (order 7) — cumulative bands over " + BalanceConfig.ROUTE_TRAJECTORY_SEED_COUNT
                + " real maps, sampled at every region boundary; worst reading per policy");
        System.out.printf("%-42s %9s %-18s %-6s%n", "policy / metric", "worst", "band", "in?");
        System.out.println("------------------------------------------------------------------------------------");
        for (BalanceSchema.RuleResult result : BalanceSchema.trajectoryResults()) {
            System.out.printf("%-42s %9.2f [%7.2f,%7.2f] %-6s%n", result.subject, result.value,
                    result.bandMinimum, result.bandMaximum, result.satisfied ? "OK" : "OUT-OF-BAND");
        }
        System.out.println("------------------------------------------------------------------------------------");
        for (BalanceSchema.RuleResult result : BalanceSchema.routeGuaranteeResults()) {
            System.out.printf("  R-ROUTE-GUARANTEES %-30s %4.0f violation(s)  %s%n",
                    result.subject, result.value, result.satisfied ? "OK" : "FAIL");
            System.out.println("    " + result.detail);
        }
        System.out.println("  SAFEST rides the generous edge and DEADLIEST the starved edge — both ENDS must stay fair.");
    }

    private static void printHeader() {
        System.out.println("====================================================================================");
        System.out.println(" toon3D BALANCE AUTHORITY — LIVING TABLE (generated by BalanceReport)");
        System.out.println(" Reference player DPT = " + BalanceConfig.REFERENCE_PLAYER_DPT
                + "   eHP = " + BalanceConfig.REFERENCE_PLAYER_EHP);
        System.out.println("====================================================================================");
    }

    // -----------------------------------------------------------------------------------
    // MATCHUP + SCENARIO tables (balance-overhaul order 3, R-ROLE — replaced the R-WEAPON power table).
    // MATCHUP: the strength-adjusted multiplier per class x trait (E = EFFECTIVE, R = RESISTED).
    // SCENARIO: turns to clear (S8: bruiser hits taken) per weapon per scenario at each R-ROLE depth,
    // read from WeaponRoleModel — the same table the build-gating audit evaluates. '*' marks the
    // scenario's best class, '!' an ammo-infeasible clear, '-' a scenario the weapon cannot clear.
    // -----------------------------------------------------------------------------------
    private static void printWeaponTable() {
        System.out.println("MATCHUP (registry: MatchupCatalog — multiplier at MATCHUP_STRENGTH "
                + BalanceConfig.MATCHUP_STRENGTH + ")");
        StringBuilder header = new StringBuilder(String.format("%-10s", "trait"));
        for (ge.tbegvadze.toon3d.entity.DamageClass damageClass : ge.tbegvadze.toon3d.entity.DamageClass.values()) {
            header.append(String.format(" %10s", damageClass.name()));
        }
        System.out.println(header);
        System.out.println("------------------------------------------------------------------------------------------------");
        ge.tbegvadze.toon3d.entity.MatchupTable matchups = ge.tbegvadze.toon3d.entity.MatchupCatalog.shared();
        for (ge.tbegvadze.toon3d.enemy.EnemyTrait trait : ge.tbegvadze.toon3d.enemy.EnemyTrait.values()) {
            StringBuilder row = new StringBuilder(String.format("%-10s", trait.name()));
            for (ge.tbegvadze.toon3d.entity.DamageClass damageClass : ge.tbegvadze.toon3d.entity.DamageClass.values()) {
                ge.tbegvadze.toon3d.entity.MatchupOutcome outcome = matchups.classify(damageClass, trait);
                String marker = outcome == ge.tbegvadze.toon3d.entity.MatchupOutcome.EFFECTIVE ? "E"
                        : outcome == ge.tbegvadze.toon3d.entity.MatchupOutcome.RESISTED ? "R" : " ";
                row.append(String.format(" %9.2f%s", matchups.multiplier(damageClass, trait), marker));
            }
            System.out.println(row);
        }
        System.out.println();
        java.util.List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        for (int depth : BalanceConfig.ROLE_SCENARIO_DEPTHS) {
            WeaponRoleModel.Table table = WeaponRoleModel.table(depth);
            System.out.println("SCENARIO (R-ROLE, depth " + depth + ") — turns to clear; S8 = bruiser hits taken");
            StringBuilder scenarioHeader = new StringBuilder(String.format("%-22s %-9s", "weapon", "class"));
            for (WeaponRoleModel.RoleScenario scenario : scenarios) scenarioHeader.append(String.format(" %7s", scenario.id));
            System.out.println(scenarioHeader);
            System.out.println("------------------------------------------------------------------------------------------------");
            java.util.List<WeaponRoleModel.RoleWeapon> weapons = WeaponRoleModel.weapons();
            for (int weaponIndex = 0; weaponIndex < weapons.size(); weaponIndex++) {
                WeaponRoleModel.RoleWeapon weapon = weapons.get(weaponIndex);
                StringBuilder row = new StringBuilder(String.format("%-22s %-9s", weapon.displayName,
                        weapon.damageClass.name()));
                for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
                    WeaponRoleModel.Cell cell = table.cells[weaponIndex][scenarioIndex];
                    if (!cell.cleared) {
                        row.append(String.format(" %7s", "-"));
                        continue;
                    }
                    boolean best = cell.eligible() && cell.score <= table.overallBest(scenarioIndex);
                    String marker = best ? "*" : (cell.ammoFeasible ? " " : "!");
                    row.append(String.format(" %6.2f%s", cell.score, marker));
                }
                System.out.println(row);
            }
            StringBuilder legend = new StringBuilder("  scenarios:");
            for (WeaponRoleModel.RoleScenario scenario : scenarios) {
                legend.append(" ").append(scenario.label()).append(" (").append(scenario.archetype.displayName()).append(");");
            }
            System.out.println(legend);
            StringBuilder supply = new StringBuilder("  ammo per average COMBAT floor:");
            for (java.util.Map.Entry<ge.tbegvadze.toon3d.item.AmmoType, Float> entry : table.supplyPerFloor.entrySet()) {
                supply.append(String.format(" %s %.1f;", entry.getKey(), entry.getValue()));
            }
            System.out.println(supply);
            System.out.println();
        }
        for (BalanceSchema.RuleResult result : BalanceSchema.weaponRoleResults()) {
            System.out.printf("  %-44s %8.2f %-11s %s%n", result.subject, result.value,
                    result.satisfied ? "OK" : "OUT-OF-BAND", result.detail);
        }
    }

    // -----------------------------------------------------------------------------------
    // ENEMIES — threat points vs role band, plus the golden-ratio (TTD/TTK) sanity check.
    // Iterates EnemyType.values() with the bands registered in BalanceSchema, so a NEW
    // archetype appears here (and in the build-gating audit) automatically — the
    // anti-"shipped unpriced" coverage rule. Golden ratio uses the player's SUSTAINED
    // reference DPT for TTK (contract decision) and reference eHP for TTD; CHAFF is
    // pack-exempt and MINI_ELITE is a deliberate spike (both print "exempt").
    // -----------------------------------------------------------------------------------
    private static void printEnemyTable() {
        System.out.println("ENEMIES (registry: EnemyType.values() through BalanceSchema bands)");
        System.out.println("  cycleDPT = order-5 cycle-averaged effective DPT (basic + priced specials); trueTP = cycle-averaged Threat Points.");
        System.out.println("  eHP = matchup-neutral (R8 / R-LADDER read it); pricedHP = trait-aware eHP TP prices (order 3, CP2).");
        System.out.printf("%-17s %-11s %-9s %5s %8s %6s %5s %8s %8s %10s %-6s %9s %-6s%n",
                "enemy", "role", "trait", "eHP", "pricedHP", "atkDmg", "cad", "cycleDPT", "posMult", "trueTP", "in?",
                "kill|die", "R8?");
        System.out.println("------------------------------------------------------------------------------------");
        for (ge.tbegvadze.toon3d.enemy.EnemyType enemyType : ge.tbegvadze.toon3d.enemy.EnemyType.values()) {
            if (enemyType.role() == ge.tbegvadze.toon3d.enemy.EnemyRole.BOSS) continue; // SECTION 14 ruleset
            printEnemyRow(enemyType);
        }
    }

    private static void printEnemyRow(ge.tbegvadze.toon3d.enemy.EnemyType enemyType) {
        // Order 5: enemy eHP runs through the shared survivability primitive (EnemyType.neutralEffectiveHitPoints),
        // not a hard-coded raw-HP shortcut — with all-zero mitigation today it still equals raw HP. Order 3
        // (CP2) prices TP on the trait-aware eHP (EnemyType.effectiveHitPoints), printed beside it.
        float enemyEffectiveHitPoints = enemyType.neutralEffectiveHitPoints();
        float pricedEffectiveHitPoints = enemyType.effectiveHitPoints();
        float cycleAveragedDamagePerTurn = enemyType.cycleAveragedDamagePerTurn();
        float threatPoints = enemyType.baseThreatPoints();
        float[] threatBand = BalanceSchema.threatPointBand(enemyType.role());
        boolean insideBand = threatBand != null
                && threatPoints >= threatBand[0] && threatPoints <= threatBand[1];
        String bandVerdictText = threatBand == null ? "n/a"
                : bandVerdict(insideBand, threatPoints, threatBand[0]);

        // R8 (balance-overhaul order 1): depth-1 hits to kill (R8 reference hit) | hits to die (205 eHP).
        int hitsToKill = BalanceSchema.enemyHitsToKill(enemyType);
        int hitsToDie = BalanceSchema.enemyHitsToDie(enemyType);
        float[] hitBand = BalanceSchema.enemyHitBand(enemyType.role());
        String hitVerdictText = hitBand == null ? "n/a"
                : (hitsToKill >= hitBand[0] && hitsToKill <= hitBand[1]
                        && hitsToDie >= hitBand[2] && hitsToDie <= hitBand[3]) ? "OK" : "OUT";

        System.out.printf("%-17s %-11s %-9s %5.0f %8.1f %6d %5d %8.2f %8.2f %10.1f %-6s %4d|%-4d %-6s%n",
                enemyType.displayName(), enemyType.role().name(), enemyType.trait().name(),
                enemyEffectiveHitPoints, pricedEffectiveHitPoints,
                enemyType.attackDamage(), enemyType.attackCadenceTurns(), cycleAveragedDamagePerTurn,
                enemyType.positionalMultiplier(), threatPoints, bandVerdictText,
                hitsToKill, hitsToDie, hitVerdictText);
    }

    // -----------------------------------------------------------------------------------
    // WAIVERS — every explicit, reasoned exception to a schema rule. A waiver is the ONLY
    // way a value may sit outside its band without failing the build; keeping the list
    // printed here keeps each one visible until its expiry condition is met.
    // -----------------------------------------------------------------------------------
    private static void printWaiversTable() {
        System.out.println("WAIVERS (registry: BalanceSchema.activeWaivers())");
        System.out.println("------------------------------------------------------------------------------------");
        java.util.List<BalanceSchema.Waiver> waivers = BalanceSchema.activeWaivers();
        if (waivers.isEmpty()) {
            System.out.println("  (none — every value is inside its band)");
            return;
        }
        for (BalanceSchema.Waiver waiver : waivers) {
            System.out.printf("  %-14s %s%n", "[" + waiver.kind + "]", waiver.subject);
            System.out.println("    reason:  " + waiver.reason);
            System.out.println("    expires: " + waiver.expiryCondition);
        }
    }

    // -----------------------------------------------------------------------------------
    // BLOCK & DEFEND (strategy-combat-order-3) — Block is transient eHP. Priced as "turns of
    // survival bought" = baseBlock / REFERENCE_PLAYER_DPT. A single DEFEND should buy ~1 turn
    // (survTurns ≈ 1), so a defend does NOT inflate a role's TTK out of its golden-ratio band:
    // Block decays after BLOCK_DECAY_TURNS and is capped at BLOCK_MAX, so it can never compound
    // into immortality, and status DoT bypasses it entirely (the build answer to turtles).
    // -----------------------------------------------------------------------------------
    private static void printBlockTable() {
        System.out.println("BLOCK & DEFEND (order-3)");
        System.out.printf("%-12s %8s %8s %7s %10s %-6s%n",
                "role", "baseBlk", "blkMax", "decay", "survTurns", "in?");
        System.out.println("------------------------------------------------------------------");
        printBlockRow("SOLDIER",    BalanceConfig.DEFEND_BLOCK_GAIN_SOLDIER);
        printBlockRow("BRUISER",    BalanceConfig.DEFEND_BLOCK_GAIN_BRUISER);
        printBlockRow("MINI_ELITE", BalanceConfig.DEFEND_BLOCK_GAIN_MINI_ELITE);
        System.out.println("survTurns = baseBlk / REFERENCE_PLAYER_DPT (turns of survival one DEFEND buys).");
        System.out.println("in? = OK when survTurns is in [0.6, 2.0] — ~1 turn bought, never a stall.");
        System.out.printf("BRUISER also braces on a fixed cadence every %d turns; CHAFF/BOSS never brace.%n",
                BalanceConfig.DEFEND_BRUISER_CADENCE_TURNS);
        System.out.printf("Turtle trigger: HP <= %.0f%% and the player is positioned to hit next turn.%n",
                BalanceConfig.DEFEND_HP_THRESHOLD_FRACTION * 100f);
    }

    private static void printBlockRow(String roleName, int baseBlock) {
        float survivalTurns = baseBlock / BalanceConfig.REFERENCE_PLAYER_DPT;
        boolean inBand = survivalTurns >= 0.6f && survivalTurns <= 2.0f;
        System.out.printf("%-12s %8d %8d %7d %10.2f %-6s%n",
                roleName, baseBlock, BalanceConfig.BLOCK_MAX, BalanceConfig.BLOCK_DECAY_TURNS,
                survivalTurns, inBand ? "OK" : "OUT!");
    }

    // -----------------------------------------------------------------------------------
    // TACTICAL MODIFIERS (order-6) — the Vulnerable/Weak/Exposed powers and the backstab payoff.
    // Each is a pure GameMath multiplier/flag folded into the shared mitigation pipeline. This table
    // reports each power's multiplier at full effect, its cap, and its effect on a reference SOLDIER's
    // TTK (turns-to-kill = ceil(eHP / DPT)) or on the marine's incoming damage, so the whole layer is
    // visibly inside the balance contract (a stacked Vulnerable must not multiply a boss into a 2-turn
    // kill; backstab must stay a conditional nudge, not a mandatory combo).
    // -----------------------------------------------------------------------------------
    private static void printModifiersTable() {
        // Reference SOLDIER eHP — midpoint of the roster's soldier HP, no Block, for a clean TTK read.
        float referenceEnemyEHP = BalanceConfig.REVENANT_MAX_HEALTH;
        float dpt               = BalanceConfig.REFERENCE_PLAYER_DPT;
        int   baseTTK           = (int) Math.ceil(referenceEnemyEHP / dpt);

        float vulnMult    = GameMath.vulnerableDamageMultiplier(
                EffectConstants.VULNERABLE_MAX_STACKS, EffectConstants.VULNERABLE_DAMAGE_PERCENT);
        int   vulnTTK     = (int) Math.ceil(referenceEnemyEHP / (dpt * vulnMult));
        float weakMult    = GameMath.weakDamageMultiplier(true, EffectConstants.WEAK_DAMAGE_PERCENT);
        float backstab    = GameMath.backstabDamageMultiplier(true, EffectConstants.BACKSTAB_DAMAGE_PERCENT);
        int   backstabTTK = (int) Math.ceil(referenceEnemyEHP / (dpt * backstab));

        System.out.println("TACTICAL MODIFIERS (order-6)");
        System.out.printf("%-12s %8s %6s %-40s%n", "power", "mult", "cap", "effect");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%-12s %8s %6d  target takes +dmg; TTK %d -> %d turns%n",
                "VULNERABLE", String.format("x%.2f", vulnMult),
                EffectConstants.VULNERABLE_MAX_STACKS, baseTTK, vulnTTK);
        System.out.printf("%-12s %8s %6s  attacker deals -%d%% (softens a telegraphed hit)%n",
                "WEAK", String.format("x%.2f", weakMult), "1hit/refresh", EffectConstants.WEAK_DAMAGE_PERCENT);
        System.out.printf("%-12s %8s %6s  next hit ignores Block; lasts %d turn(s), 1 hit%n",
                "EXPOSED", "-", "1 hit", EffectConstants.EXPOSED_DURATION);
        System.out.printf("%-12s %8s %6s  from behind facing; TTK %d -> %d turns%n",
                "BACKSTAB", String.format("x%.2f", backstab), "conditional", baseTTK, backstabTTK);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Reference SOLDIER eHP = %.0f, player DPT = %.0f. VULNERABLE is stack-capped so even%n",
                referenceEnemyEHP, dpt);
        System.out.println("at max stacks it shaves ~1 turn off a soldier, never trivializing a boss's eHP.");
        System.out.println("BACKSTAB is priced as a conditional (like crit): it needs positioning, so its");
        System.out.println("reliable value is self-limiting and it stays a nudge, not an execution check.");
    }

    // -----------------------------------------------------------------------------------
    // SPECIAL MOVE-SETS (order-5) — each scripted archetype's ability cadence and catalogue.
    // A move-set changes an enemy's EFFECTIVE threat: buff/debuff/area moves raise its blended DPT and
    // summons add spawned TP. The hard summon caps bound that headroom so the encounter budget holds.
    // This table lists which archetypes are scripted; the TP bands above still gate their base stats.
    // -----------------------------------------------------------------------------------
    private static void printMoveSetTable() {
        System.out.println("SPECIAL MOVE-SETS (order-5)");
        System.out.println("------------------------------------------------------------------------------------");
        System.out.printf("%-16s %-11s %-9s %s%n", "archetype", "role", "cadence", "abilities");
        System.out.println("------------------------------------------------------------------------------------");
        for (ge.tbegvadze.toon3d.enemy.EnemyType type : ge.tbegvadze.toon3d.enemy.EnemyType.values()) {
            ge.tbegvadze.toon3d.enemy.SpecialAbility[] moveSet = type.moveSet();
            if (moveSet.length == 0) continue; // trivial chaff/boss — no scripted specials
            StringBuilder abilities = new StringBuilder();
            for (int index = 0; index < moveSet.length; index++) {
                if (index > 0) abilities.append(", ");
                abilities.append(moveSet[index].name());
            }
            System.out.printf("%-16s %-11s %-9d %s%n",
                    type.displayName(), type.role().name(),
                    type.specialAbilityCadenceTurns(), abilities);
        }
        System.out.println("------------------------------------------------------------------------------------");
        System.out.println("cadence = enemy turns between specials; every damaging special is telegraphed 1 turn ahead.");
    }

    // -----------------------------------------------------------------------------------
    // UPGRADE CARDS (idea 5) — every level-up card priced in power points (PP) vs the budget.
    // PP = %-gain to reference DPT (offence) or reference eHP (survivability), computed straight
    // from the card's stat deltas through GameMath. Each card MUST land in the budget band, so the
    // total PP at level L is build-independent (= L * budget). Trade-off cards net to ~budget.
    // -----------------------------------------------------------------------------------
    private static void printUpgradeCardTable() {
        float budget    = GameBalance.LEVEL_UP_BUDGET_PP;
        float tolerance = GameBalance.LEVEL_UP_BUDGET_TOLERANCE;
        float bandMinimum = budget * (1f - tolerance);
        float bandMaximum = budget * (1f + tolerance);

        System.out.println("UPGRADE CARDS (idea 5) — budget = " + String.format("%.0f", budget)
                + " PP   band = " + String.format("%.1f-%.1f", bandMinimum, bandMaximum)
                + " PP   (offered " + GameBalance.LEVEL_UP_CARDS_OFFERED + "/level)");
        System.out.printf("%-20s %-9s %-13s %8s %-8s %-6s %-8s%n",
                "card", "pool", "lever", "PP", "tradeoff", "in?", "maxBP");
        System.out.println("------------------------------------------------------------------------------------");

        boolean allInBand = true;
        boolean allCross  = true;
        for (ge.tbegvadze.toon3d.progression.UpgradeCard card
                : ge.tbegvadze.toon3d.progression.UpgradeCard.values()) {
            float powerPoints = card.estimatedPowerPoints();
            boolean inBand = powerPoints >= bandMinimum && powerPoints <= bandMaximum;
            allInBand &= inBand;
            // R-CARD-BREAKPOINT: the deepest integer TTK/TTD breakpoint the card crosses vs the region soldier.
            int bestBreakpoint = ge.tbegvadze.toon3d.util.BalanceSchema.cardBestBreakpoint(card);
            allCross &= bestBreakpoint >= 1;
            System.out.printf("%-20s %-9s %-13s %8.1f %-8s %-6s %-8s%n",
                    card.displayName, card.pool.name(), card.lever.name(), powerPoints,
                    card.isTradeOff() ? "YES" : "no",
                    bandVerdict(inBand, powerPoints, bandMinimum),
                    bestBreakpoint >= 1 ? ("+" + bestBreakpoint) : "NONE");
        }
        System.out.println("------------------------------------------------------------------------------------");
        System.out.println("INVARIANT: every card in band => total PP at level L = L * budget (build-independent).  "
                + (allInBand ? "ALL CARDS IN BAND" : "*** OUT-OF-BAND CARD(S) — RE-PRICE ***"));
        System.out.println("R-CARD-BREAKPOINT (order 4): each card crosses >= 1 TTK/TTD breakpoint vs the region soldier.  "
                + (allCross ? "ALL CROSS" : "*** CARD CROSSES NO BREAKPOINT — RE-PRICE ***"));
    }

    // -----------------------------------------------------------------------------------
    // XP PACING (new-game-balancr order 4) — R-XP-PACE. A floor's roster XP (XP_PER_THREAT_POINT *
    // fill-target * floor TP budget) over the geometric level requirement at the expected level must
    // land in [XP_FLOOR_YIELD_MIN, MAX] at every depth — leveling is paced, not a hope.
    // -----------------------------------------------------------------------------------
    private static void printXpPacingTable() {
        System.out.println("XP PACING (order 4) — floor XP / xpRequired(expected level)  (band "
                + String.format("%.1f-%.1f", BalanceConfig.XP_FLOOR_YIELD_MIN, BalanceConfig.XP_FLOOR_YIELD_MAX)
                + " level-ups/floor)");
        System.out.println("  per-enemy XP = " + BalanceConfig.XP_PER_THREAT_POINT
                + " * depth-scaled TP ; curve = " + BalanceConfig.XP_BASE_REQUIREMENT + " * "
                + BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL + "^(level-1) (geometric, tracks enemy compound)");
        System.out.printf("%-6s %8s %10s %10s %8s %-8s%n",
                "depth", "expLvl", "avail XP", "req XP", "yield", "in band?");
        System.out.println("------------------------------------------------------------------------------------");
        for (int depth = 1; depth <= 15; depth++) {
            int expectedLevel = GameMath.expectedLevelAtDepth(BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, depth);
            int requiredXp = GameMath.xpRequiredForLevelGeometric(BalanceConfig.XP_BASE_REQUIREMENT,
                    BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL, expectedLevel);
            float availableXp = ge.tbegvadze.toon3d.util.BalanceSchema.floorRosterXp(depth);
            float yield = requiredXp <= 0 ? 0f : availableXp / requiredXp;
            boolean inBand = yield >= BalanceConfig.XP_FLOOR_YIELD_MIN && yield <= BalanceConfig.XP_FLOOR_YIELD_MAX;
            String verdict = inBand ? "OK" : (yield < BalanceConfig.XP_FLOOR_YIELD_MIN ? "STARVED" : "RUNAWAY");
            System.out.printf("%-6d %8d %10.0f %10d %8.3f %-8s%n",
                    depth, expectedLevel, availableXp, requiredXp, yield, verdict);
        }
    }

    private static String bandVerdict(boolean insideBand, float value, float bandMinimum) {
        if (insideBand) {
            return "OK";
        }
        return value < bandMinimum ? "UNDER" : "OVER";
    }

    // -----------------------------------------------------------------------------------
    // ENCOUNTER BUDGET — the floor TP budget and the planned roster's composition (idea 4).
    // Plans a roster per depth via EncounterBudgetPlanner and checks idea 4's rules:
    //   anchor present, no single TYPE over the per-type cap, both ranged AND melee present.
    // Uses a fixed seed so the report is reproducible run-to-run.
    // -----------------------------------------------------------------------------------
    private static void printEncounterTable() {
        System.out.println("ENCOUNTER (balance-overhaul order 2) — COMBAT floor: body target filled with group templates "
                + "under a Threat-Point CAP (base " + String.format("%.0f", BalanceConfig.FLOOR_BASE_THREAT_POINT_BUDGET)
                + " TP at depth 1)");
        System.out.printf("%-6s %8s %8s %6s %6s %-34s %-14s %-9s %-7s %-7s%n",
                "depth", "cap", "spent", "bodies", "target", "groups", "anchor", "maxType%", "mix?", "rules?");
        System.out.println("------------------------------------------------------------------------------------");
        int[] depths = {1, 2, 3, 5, 8, 15, 25};
        for (int depth : depths) {
            printEncounterRow(depth);
        }
    }

    private static void printEncounterRow(int depth) {
        // Deterministic seed per depth so the audit is stable.
        ge.tbegvadze.toon3d.level.EncounterBudgetPlanner planner =
                new ge.tbegvadze.toon3d.level.EncounterBudgetPlanner(depth, new java.util.Random(1234L + depth));
        ge.tbegvadze.toon3d.level.EncounterBudgetPlanner.Plan plan = planner.plan();

        java.util.List<ge.tbegvadze.toon3d.enemy.EnemyType> roster = plan.enemies();

        // Largest single-type TP fraction of budget.
        java.util.EnumMap<ge.tbegvadze.toon3d.enemy.EnemyType, Float> byType =
                new java.util.EnumMap<>(ge.tbegvadze.toon3d.enemy.EnemyType.class);
        boolean hasRanged = false;
        boolean hasMelee  = false;
        for (ge.tbegvadze.toon3d.enemy.EnemyType type : roster) {
            byType.merge(type, plan.threatOf(type), Float::sum);
            if (type.isRanged()) hasRanged = true; else hasMelee = true;
        }
        float maxTypeFraction        = 0f;   // informational: largest single-type share
        float maxFillTypeFraction    = 0f;    // cap check: largest NON-anchor type share
        for (java.util.Map.Entry<ge.tbegvadze.toon3d.enemy.EnemyType, Float> entry : byType.entrySet()) {
            float fraction = entry.getValue() / plan.floorBudget();
            maxTypeFraction = Math.max(maxTypeFraction, fraction);
            if (entry.getKey() != plan.anchor()) {
                maxFillTypeFraction = Math.max(maxFillTypeFraction, fraction);
            }
        }

        boolean anchorOk = plan.anchor() != null;
        // Anchor is exempt from the per-type cap; fill types must respect it.
        boolean typeOk   = maxFillTypeFraction <= BalanceConfig.GROUP_MAX_SINGLE_TYPE_FRACTION + 0.001f;
        boolean mixOk    = hasRanged && hasMelee;
        boolean allRules = anchorOk && typeOk && mixOk;

        String anchorName = plan.anchor() == null ? "(none)" : plan.anchor().displayName();
        StringBuilder shapes = new StringBuilder();
        for (ge.tbegvadze.toon3d.level.EncounterGroup group : plan.groups()) {
            if (shapes.length() > 0) shapes.append(' ');
            shapes.append(group.templateId, 0, Math.min(4, group.templateId.length())).append(group.size());
        }
        System.out.printf("%-6d %8.0f %8.0f %6d %6d %-34s %-14s %-9.0f %-7s %-7s%n",
                depth, plan.floorBudget(), plan.spentThreatPoints(), roster.size(), plan.bodyTarget(),
                shapes, anchorName, maxTypeFraction * 100f,
                mixOk ? "OK" : "NO", allRules ? "OK" : "CHECK");
    }

    // -----------------------------------------------------------------------------------
    // REGION DANGER DIAL (R-REGION, order 5) — macro pacing as a budgeted number. Each route region
    // scales the depth-ramped floor budget by REGION_TP_BUDGET_MULTIPLIER on top of the depth curve, so
    // a lethal region spends MORE Threat Points (more bodies) at the same depth. The sweep prints the
    // region-scaled floor budget at each region's entry depth, showing C/D measurably out-spending A.
    // Per-fight fairness (depth-coupling) is region-INDEPENDENT and shown holding in every lane.
    // -----------------------------------------------------------------------------------
    private static void printRegionDangerTable() {
        float[] multipliers = BalanceConfig.REGION_TP_BUDGET_MULTIPLIER;
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        System.out.println("REGION DANGER DIAL (R-REGION, order 5) — TP multiplier applied ON TOP of the depth curve");
        System.out.printf("%-8s %-16s %8s %8s %10s %-6s%n",
                "region", "name", "dial", "entryD", "regionBudget", "dial?");
        System.out.println("------------------------------------------------------------------------------------");
        for (int region = 0; region < multipliers.length; region++) {
            int entryDepth = region * band + 1;
            float regionBudget = GameMath.regionScaledFloorThreatPointBudget(
                    BalanceConfig.FLOOR_BASE_THREAT_POINT_BUDGET,
                    BalanceConfig.ENEMY_HEALTH_GROWTH, BalanceConfig.ENEMY_DAMAGE_GROWTH,
                    entryDepth, multipliers, band);
            boolean dialInBand = multipliers[region] >= BalanceConfig.REGION_TP_BUDGET_MULTIPLIER_MIN
                    && multipliers[region] <= BalanceConfig.REGION_TP_BUDGET_MULTIPLIER_MAX;
            System.out.printf("%-8s %-16s %8.2f %8d %10.0f %-6s%n",
                    regionLetter(region), regionName(region), multipliers[region], entryDepth,
                    regionBudget, dialInBand ? "OK" : "OUT");
        }
        System.out.println("  DIAL: monotonic non-decreasing (A<=B<=C<=D); C/D out-dial A by >= "
                + BalanceConfig.REGION_TP_LETHAL_MARGIN + " (measurably lethal).");
        System.out.println("  FAIRNESS: the dial scales the BUDGET (body count), NOT per-enemy threat — "
                + "the per-fight power ladder (R-LADDER L1) holds in every region lane.");
    }

    /** Route region letter A..D for a 0-based region index (clamped past D — endless "The Breach"). */
    private static String regionLetter(int region) {
        switch (region) {
            case 0:  return "A";
            case 1:  return "B";
            case 2:  return "C";
            default: return "D";
        }
    }

    /** Route region name for a 0-based region index (clamped past D). */
    private static String regionName(int region) {
        switch (region) {
            case 0:  return ge.tbegvadze.toon3d.util.RouteMapConstants.REGION_A_NAME;
            case 1:  return ge.tbegvadze.toon3d.util.RouteMapConstants.REGION_B_NAME;
            case 2:  return ge.tbegvadze.toon3d.util.RouteMapConstants.REGION_C_NAME;
            default: return ge.tbegvadze.toon3d.util.RouteMapConstants.REGION_D_NAME;
        }
    }

    // -----------------------------------------------------------------------------------
    // ABILITY PRICING — every weapon ability's PP price at level 1 and level 10, plus the per-tier
    // ability-PP budgets (new-game-balancr order 2). Rarity buys abilities within these budgets;
    // the WeaponRoller never overspends a tier's ceiling. Iterated straight from GameMath so the
    // table cannot drift from the roller.
    // -----------------------------------------------------------------------------------
    private static void printAbilityPricingTable() {
        System.out.println("ABILITY PRICING — PP cost per ability (budget = PP a tier may spend on abilities)");
        System.out.print("  tier budgets: ");
        for (ge.tbegvadze.toon3d.entity.WeaponTier tier : ge.tbegvadze.toon3d.entity.WeaponTier.values()) {
            System.out.printf("%s=%.0f  ", tier.displayName,
                    ge.tbegvadze.toon3d.entity.WeaponRoller.tierAbilityPowerPointBudget(tier));
        }
        System.out.printf("(±%.0f%%)%n", BalanceConfig.TIER_ABILITY_PP_TOLERANCE * 100f);
        System.out.printf("%-22s %8s %8s%n", "ability", "PP@L1", "PP@L10");
        System.out.println("------------------------------------------------------------------------------------");
        for (ge.tbegvadze.toon3d.entity.WeaponAbility ability
                : ge.tbegvadze.toon3d.entity.WeaponAbility.values()) {
            float ppLevel1  = abilityPowerPointsAt(ability, 1);
            float ppLevel10 = abilityPowerPointsAt(ability, WeaponConstants.MAX_WEAPON_LEVEL);
            System.out.printf("%-22s %8.2f %8.2f%n", ability.name(), ppLevel1, ppLevel10);
        }
    }

    private static float abilityPowerPointsAt(ge.tbegvadze.toon3d.entity.WeaponAbility ability, int level) {
        ge.tbegvadze.toon3d.entity.AbilityInstance instance =
                ge.tbegvadze.toon3d.entity.WeaponRoller.buildAbilityInstance(ability, level);
        return GameMath.abilityPowerPoints(ability, instance.magnitude, instance.countValue);
    }

    // -----------------------------------------------------------------------------------
    // SCARCITY — the model floor's SUPPLY vs DEMAND, scarcity ratio S, per-weapon S,
    // reserve banking ceiling, and the heal economy's net HP drain (idea 3).
    // Every number is computed from BalanceConfig through GameMath, so it cannot drift.
    // -----------------------------------------------------------------------------------
    private static void printScarcityTable() {
        // The SECTION 10 model floor survives only as a PRINTED EXAMPLE (balance-overhaul order 2): its
        // supply is what the SupplyPlanner hands that roster on a depth-1 COMBAT node, not a dice model.
        ge.tbegvadze.toon3d.level.SupplyPlan plan = BalanceSchema.modelFloorPlan(1);
        float demand = plan.rosterEffectiveHitPoints;
        int enemyCount = BalanceSchema.modelFloorRoster().size();
        java.util.Map<Character, Integer> boxesBySymbol = new java.util.HashMap<>();
        for (ge.tbegvadze.toon3d.level.PlannedPickup pickup : plan.pickups()) {
            if (pickup.category == ge.tbegvadze.toon3d.level.SupplyCategory.AMMO) {
                boxesBySymbol.merge(pickup.symbol, 1, Integer::sum);
            }
        }
        System.out.println("SUPPLY PLAN — model floor example (depth 1, COMBAT): " + enemyCount
                + " enemies, DEMAND=" + String.format("%.0f", demand) + " dmg ; planned ammo damage="
                + String.format("%.0f", plan.plannedValue(ge.tbegvadze.toon3d.level.SupplyCategory.AMMO))
                + " (ammoRatio " + BalanceConfig.NODE_SUPPLY_COMBAT_AMMO_RATIO + ", "
                + String.format("%.0f%%", BalanceConfig.SUPPLY_CARRIED_SHARE * 100f) + " carried types)");
        System.out.printf("%-10s %7s %9s %8s %10s %-7s %12s%n",
                "ammoType", "boxes", "dmg/unit", "supply", "perWpnS", "", "bankFloors");
        System.out.println("------------------------------------------------------------------------------------");
        float totalSupply = 0f;
        for (BalanceSchema.ScarcityRowSpec row : BalanceSchema.scarcityRows()) {
            int boxes = boxesBySymbol.getOrDefault(row.ammoType.getPickupTileChar(), 0);
            totalSupply += printScarcityRow(row.ammoType.getDisplayName(), boxes, row.boxSize,
                    row.damagePerUnit, row.reserveCap, demand);
        }
        System.out.println("------------------------------------------------------------------------------------");
        System.out.printf("FLOOR-WIDE  SUPPLY=%.0f  DEMAND=%.0f  S=%.2f  (R-SUPPLY tracks the plan, not a band on S)%n",
                totalSupply, demand, GameMath.scarcityRatio(totalSupply, demand));
        System.out.printf("HEALS planned %.2f max-HP (heal floor %.2f) + armour %.2f ; incoming %.2f max-HP%n",
                plan.plannedValue(ge.tbegvadze.toon3d.level.SupplyCategory.HEAL), plan.healFloorValue,
                plan.plannedValue(ge.tbegvadze.toon3d.level.SupplyCategory.ARMOUR), plan.incomingFraction);
    }

    /** Prints one ammo-type row and returns that type's supply damage. */
    private static float printScarcityRow(String ammoType, int boxes, int boxSize,
                                          float damagePerUnit, int reserveCap, float demand) {
        float supply = GameMath.ammoSupplyDamage(boxes, boxSize, damagePerUnit);
        float perWeaponShare = GameMath.scarcityRatio(supply, demand);
        float bankFloors = GameMath.reserveBankingFloors(reserveCap, damagePerUnit, demand);
        System.out.printf("%-10s %7d %9.0f %8.0f %10.2f %-7s %12.2f%n",
                ammoType, boxes, damagePerUnit, supply, perWeaponShare, "", bankFloors);
        return supply;
    }

    /** Enemy eHP with no dodge or flat reduction (the contract rule for current enemies). */
    private static float enemyEffectiveHitPoints(int rawHealth) {
        return GameMath.effectiveHitPoints(rawHealth, 0f, 0f, 0f, 0f);
    }

    // -----------------------------------------------------------------------------------
    // ORDER-3 RESOURCE ECONOMY DEPTH SWEEPS — the whole-run generalisation of the model floor.
    // R-SCARCITY-DEPTH / R-HEALDRAIN-DEPTH / R-CREDITS across depths 1..15, plus the formula-derived
    // shop price list. All numbers flow through the same BalanceSchema helpers the audit uses.
    // -----------------------------------------------------------------------------------

    /**
     * SUPPLY / DENSITY (balance-overhaul order 2) — what every generator x node type actually builds at the
     * audited depths, averaged over the R-SUPPLY sweep's seeds (the same pass the audit asserts on):
     * bodies, groups, first-contact walk, footprint, density, and planned vs placed supply per category.
     */
    private static void printSupplyDensityTable() {
        System.out.println("SUPPLY / DENSITY (R-SUPPLY / R-DENSITY) — mean over "
                + BalanceConfig.SUPPLY_AUDIT_SEED_COUNT + " seeds per row; ammo in damage, heal/armour in max-HP");
        System.out.printf("%-16s %-11s %3s %6s %6s %6s %5s %6s %5s %13s %11s %11s %7s%n",
                "generator", "node", "d", "bodies", "groups", "1stCon", "walk", "dens", "TP/x",
                "ammo plan/put", "heal p/put", "armr p/put", "credits");
        System.out.println("-----------------------------------------------------------------------------------------------------------------------");
        java.util.Map<String, java.util.List<ge.tbegvadze.toon3d.level.FloorContentReport>> cells = new java.util.LinkedHashMap<>();
        for (ge.tbegvadze.toon3d.level.FloorContentReport report : BalanceSchema.supplySweepReports()) {
            cells.computeIfAbsent(report.generatorName + "|" + report.spec.type() + "|" + report.depth,
                    key -> new java.util.ArrayList<>()).add(report);
        }
        for (java.util.List<ge.tbegvadze.toon3d.level.FloorContentReport> cell : cells.values()) {
            ge.tbegvadze.toon3d.level.FloorContentReport first = cell.get(0);
            float bodies = 0f, groups = 0f, contact = 0f, walk = 0f, density = 0f, threat = 0f;
            float ammoPlan = 0f, ammoPut = 0f, healPlan = 0f, healPut = 0f, armourPlan = 0f, armourPut = 0f, credits = 0f;
            int contactCount = 0;
            for (ge.tbegvadze.toon3d.level.FloorContentReport report : cell) {
                bodies  += report.enemyCount;
                groups  += report.groupCount();
                if (report.firstContactWalkTiles >= 0) { contact += report.firstContactWalkTiles; contactCount++; }
                walk    += report.walkableTiles;
                density += report.density();
                threat  += report.threatCap > 0f ? report.threatSpent / report.threatCap : 0f;
                ammoPlan   += report.plan.plannedValue(ge.tbegvadze.toon3d.level.SupplyCategory.AMMO);
                ammoPut    += report.placement.placedValue(ge.tbegvadze.toon3d.level.SupplyCategory.AMMO);
                healPlan   += report.plan.plannedValue(ge.tbegvadze.toon3d.level.SupplyCategory.HEAL);
                healPut    += report.placement.placedValue(ge.tbegvadze.toon3d.level.SupplyCategory.HEAL);
                armourPlan += report.plan.plannedValue(ge.tbegvadze.toon3d.level.SupplyCategory.ARMOUR);
                armourPut  += report.placement.placedValue(ge.tbegvadze.toon3d.level.SupplyCategory.ARMOUR);
                credits    += report.placement.placedValue(ge.tbegvadze.toon3d.level.SupplyCategory.CREDITS);
            }
            float sampleCount = cell.size();
            System.out.printf("%-16s %-11s %3d %6.1f %6.1f %6s %5.0f %6.2f %5.2f %6.0f/%-6.0f %5.2f/%-5.2f %5.2f/%-5.2f %7.0f%n",
                    first.generatorName, first.spec.type(), first.depth,
                    bodies / sampleCount, groups / sampleCount,
                    contactCount == 0 ? "-" : String.format("%.1f", contact / contactCount),
                    walk / sampleCount, density / sampleCount, threat / sampleCount,
                    ammoPlan / sampleCount, ammoPut / sampleCount, healPlan / sampleCount, healPut / sampleCount,
                    armourPlan / sampleCount, armourPut / sampleCount, credits / sampleCount);
        }
    }

    /** A6: the ELITE promise — threat over COMBAT and the reward weapon behind the anchor group. */
    private static void printEliteRewardTable() {
        System.out.println("ELITE PROMISE (R-SUPPLY A6) — threat ratio band "
                + String.format("%.2f-%.2f", BalanceConfig.ELITE_THREAT_RATIO_MIN, BalanceConfig.ELITE_THREAT_RATIO_MAX)
                + "; reward weapon level >= d+1 in or past the anchor room on every floor");
        for (BalanceSchema.RuleResult result : BalanceSchema.eliteRewardResults()) {
            System.out.printf("%-4s %-44s %6.2f  %s%n", result.satisfied ? "OK" : "FAIL", result.subject,
                    result.value, result.detail == null ? "" : result.detail);
        }
    }

    /** R-CREDITS: expected income vs the price of the expected purchase bundle, per region. */
    private static void printCreditEconomyTable() {
        System.out.println("CREDIT ECONOMY (R-CREDITS) — income(region) / bundle price, band "
                + String.format("%.2f-%.2f", BalanceConfig.CREDIT_INCOME_RATIO_MIN, BalanceConfig.CREDIT_INCOME_RATIO_MAX)
                + "  [kill base=" + String.format("%.0f", BalanceSchema.modelFloorKillCreditReward())
                + "/floor  chips=" + String.format("%.0f", BalanceSchema.chipIncomePerFloor()) + "/floor]");
        System.out.printf("%-8s %10s %10s %8s %-6s%n", "region", "income", "bundle", "ratio", "in?");
        System.out.println("------------------------------------------------------------------------------------");
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        int region = 0;
        for (BalanceSchema.RuleResult result : BalanceSchema.creditResults()) {
            int firstDepth = region * band + 1;
            int repDepth   = firstDepth + (band - 1) / 2;
            float income = GameMath.creditIncomePerRegion(BalanceSchema.modelFloorKillCreditReward(),
                    BalanceConfig.CREDIT_DEPTH_SCALE, BalanceSchema.chipIncomePerFloor(), firstDepth, band);
            int bundle = BalanceSchema.regionPurchaseBundlePrice(repDepth);
            System.out.printf("%-8d %10.0f %10d %8.2f %-6s%n",
                    region, income, bundle, result.value,
                    bandVerdict(result.satisfied, result.value, result.bandMinimum));
            region++;
        }
        System.out.println("  (bundle = " + BalanceConfig.SHOP_EXPECTED_SIGNIFICANT_BUYS_PER_REGION
                + " significant @ " + String.format("%.0f", BalanceConfig.SHOP_SIGNIFICANT_BUY_POWER_POINTS)
                + "PP + " + String.format("%.1f", BalanceConfig.SHOP_EXPECTED_SMALL_BUYS_PER_REGION)
                + " small @ " + String.format("%.0f", BalanceConfig.SHOP_SMALL_BUY_POWER_POINTS) + "PP each)");
    }

    /** Formula-derived shop prices (GameMath.shopPrice) for representative offers at depths 1/5/10/15. */
    private static void printShopPricingTable() {
        System.out.println("SHOP PRICING — price = valuePP * " + String.format("%.0f", BalanceConfig.SHOP_CREDITS_PER_POWER_POINT)
                + " cr/PP * depthFactor (zero hand-set base prices)");
        int[] depths = {1, 5, 10, 15};
        System.out.printf("%-26s %7s", "offer", "valuePP");
        for (int depth : depths) System.out.printf("  d%-6d", depth);
        System.out.println();
        System.out.println("------------------------------------------------------------------------------------");
        printShopPriceRow("Ammo box (15 x 20 dmg)", 15 * 20 / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT, depths);
        printShopPriceRow("Stim medkit (" + BalanceSchema.modelStimHeal() + " HP)",
                BalanceSchema.modelStimHeal() / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT, depths);
        printShopPriceRow("Field medkit (" + BalanceSchema.modelFullMedkitHeal() + " HP)",
                BalanceSchema.modelFullMedkitHeal() / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT, depths);
        printShopPriceRow("Weapon level-up", BalanceConfig.LADDER_LEVEL_UP_POWER_POINTS, depths);
        printShopPriceRow("Tier up (RARE, 12 PP)", BalanceConfig.TIER_ABILITY_PP_BUDGET_RARE, depths);
        printShopPriceRow("Tier up (EPIC, 20 PP)", BalanceConfig.TIER_ABILITY_PP_BUDGET_EPIC, depths);
        printShopPriceRow("Player ability boon (~12 PP)", BalanceConfig.LEVEL_UP_BUDGET_PP, depths);
    }

    private static void printShopPriceRow(String label, float valuePowerPoints, int[] depths) {
        System.out.printf("%-26s %7.1f", label, valuePowerPoints);
        for (int depth : depths) {
            int price = GameMath.shopPrice(valuePowerPoints, BalanceConfig.SHOP_CREDITS_PER_POWER_POINT,
                    depth, BalanceConfig.SHOP_DEPTH_PRICE_SCALE);
            System.out.printf("  %-7d", price);
        }
        System.out.println();
    }

    // -----------------------------------------------------------------------------------
    // HAZARDS (idea 4, Pillar 3) — fold terrain danger into the Threat-Point contract.
    // A hazard tile has no HP, so its TP = damagePerTurn * careless-turns-stood * positional
    // (GameMath.hazardTileThreatPoints). N such tiles raise a room's effective floor TP by ~N×
    // this — that is how "a room full of fire raises its effective TP" (idea 4) is quantified.
    // -----------------------------------------------------------------------------------
    private static void printHazardTable() {
        System.out.println("HAZARDS (idea 4, Pillar 3) — turnsStood=" + BalanceConfig.HAZARD_THREAT_TURNS_STOOD
                + " (careless-player reference)");
        System.out.printf("%-8s %10s %8s %10s %12s%n",
                "hazard", "dmg/turn", "TP/tile", "lifetime", "TP per 6 tiles");
        System.out.println("------------------------------------------------------------------------------------");

        // Fire applies BURNING at a flat per-turn magnitude; toxic STACKS, so a careless stand of
        // turnsStood turns averages ~turnsStood/2 stacks — model its damage/turn at that average.
        float fireDamagePerTurn  = EffectConstants.BURN_DAMAGE_PER_TURN;
        float toxicAverageStacks = Math.max(1f, BalanceConfig.HAZARD_THREAT_TURNS_STOOD / 2f);
        float toxicDamagePerTurn = EffectConstants.POISON_DAMAGE_PER_STACK * toxicAverageStacks;

        printHazardRow("Fire 'i'",  fireDamagePerTurn,  BalanceConfig.HAZARD_FIRE_LIFETIME_TURNS);
        printHazardRow("Toxic 'q'", toxicDamagePerTurn, BalanceConfig.HAZARD_TOXIC_LIFETIME_TURNS);
        System.out.println("  Hazards damage BOTH sides (player AND enemies) via BURNING/POISONED — two-sided by design.");
    }

    private static void printHazardRow(String hazardName, float damagePerTurn, int lifetimeTurns) {
        float threatPerTile = GameMath.hazardTileThreatPoints(damagePerTurn,
                BalanceConfig.HAZARD_THREAT_TURNS_STOOD, BalanceConfig.POSITIONAL_MULT_MELEE);
        System.out.printf("%-8s %10.1f %8.1f %10d %12.1f%n",
                hazardName, damagePerTurn, threatPerTile, lifetimeTurns, threatPerTile * 6f);
    }

    // -----------------------------------------------------------------------------------
    // TELEGRAPH AUDIT (idea 4, Pillar 5) — the fairness contract.
    // RULE: every attack that can deal > TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION of reference eHP
    // in ONE hit MUST be telegraphed or avoidable. An UN-telegraphed attack whose depth-1 hit
    // already exceeds the cap FAILS. "cap@" is the depth at which the attack first crosses the cap
    // (informational): telegraphed/avoidable attacks are allowed to cross it.
    // -----------------------------------------------------------------------------------
    private static void printTelegraphAudit() {
        float cap = BalanceConfig.REFERENCE_PLAYER_EHP * BalanceConfig.TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION;
        System.out.printf("TELEGRAPH AUDIT (idea 4, Pillar 5) — cap = %.0f%% of %.0f eHP = %.0f dmg / un-telegraphed hit%n",
                BalanceConfig.TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION * 100f,
                BalanceConfig.REFERENCE_PLAYER_EHP, cap);
        System.out.printf("%-22s %9s %-13s %6s %-7s%n",
                "attack", "hit@d1", "readable?", "cap@", "verdict");
        System.out.println("------------------------------------------------------------------------------------");

        // Readable kinds: TELE = wind-up telegraph, LANE = ranged cardinal-line tell (you can see
        // when you're in its lane), FACE = positional counter (rotate to deny it), NONE = burst.
        // Iterates the schema's registered attack list (BalanceSchema.telegraphAttacks()), so this
        // table and the build-gating audit always check the same attacks.
        for (BalanceSchema.TelegraphAttackSpec attack : BalanceSchema.telegraphAttacks()) {
            printTelegraphRow(attack.attackName, attack.baseHit, attack.readableKind, cap);
        }
    }

    private static void printTelegraphRow(String attackName, int baseHit, String readable, float cap) {
        boolean avoidable = !"NONE".equals(readable);
        // Depth at which this hit first crosses the cap, scanning a generous depth range.
        int crossingDepth = -1;
        for (int depth = 1; depth <= 30; depth++) {
            float hit = baseHit * GameBalance.enemyDamageScaleForDepth(depth);
            if (hit > cap) { crossingDepth = depth; break; }
        }
        String crossText = (crossingDepth == -1) ? ">30" : Integer.toString(crossingDepth);
        // FAIL only when an UN-telegraphed attack already exceeds the cap at depth 1.
        boolean failsContract = !avoidable && baseHit > cap;
        String verdict = failsContract ? "FAIL" : "OK";
        System.out.printf("%-22s %9d %-13s %6s %-7s%n", attackName, baseHit, readable, crossText, verdict);
    }

    // -----------------------------------------------------------------------------------
    // BOSS RULESET (idea 6) — bosses are tuned by FORMULA, not by flat HP.
    // Bosses break the trash-mob TP/golden-ratio bands, so they get their own contract:
    // HP is DERIVED from a fight-length target against the EXPECTED player DPT at depth
    // (RULE 1), the fight is capped from above (RULE 2), boss DPT is a survival check
    // (RULE 3), and no single hit may break the fairness caps (RULE 3). Boss FIGHTS are
    // deferred, so this section re-derives what the CURRENT placeholder bosses SHOULD be
    // and flags how far the placeholders sit from the formula — exactly the "re-derive me"
    // signal the boss work will act on. Every number is BalanceConfig through GameMath.
    // -----------------------------------------------------------------------------------
    private static void printBossRulesetTable() {
        System.out.println("BOSS RULESET (order 6) — HP/DPT/verbs/reward all DERIVED (BossBalance), no flat constants."
                + " survivalRatio band " + String.format("%.2f-%.2f", BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MIN,
                        BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MAX)
                + " ; single-hit caps: telegraph >" + String.format("%.0f%%", BalanceConfig.TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION * 100f)
                + " eHP, hard " + String.format("%.0f%%", BalanceConfig.BOSS_HARD_SINGLE_HIT_FRACTION * 100f) + " eHP");
        System.out.printf("%-12s %5s %7s %8s %8s %8s %6s %8s %8s %8s%n",
                "boss", "depth", "expDPT", "derHP", "bossDPT", "target", "gate", "fairRatio", "reward", "ammoCov");
        System.out.println("------------------------------------------------------------------------------------------");

        for (BossBalance.Archetype archetype : BossBalance.Archetype.values()) {
            int depth = archetype.canonicalDepth;
            BossStats stats = BossBalance.statsForDepth(archetype, depth);
            float expectedDpt = BossBalance.expectedPlayerDamagePerTurn(depth);

            // R-BOSS-GATE margin: start-weapon (L1 COMMON on the ladder) TTK vs survivable turns with max heals.
            ExpectedPlayer startWeapon = BalanceSchema.ladderStartWeaponPlayer(depth);
            float maxHeals = startWeapon.effectiveHitPoints * BalanceConfig.BOSS_GATE_MODELED_HEAL_SUPPLY_EHP_FRACTION;
            float startTtk = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    stats.effectiveHitPoints, startWeapon.damagePerTurn);
            float survivable = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    startWeapon.effectiveHitPoints + maxHeals, stats.damagePerTurn);
            float gateMargin = survivable > 0f ? startTtk / survivable : Float.POSITIVE_INFINITY;

            // R-BOSS-FAIR survival ratio for the expected loadout.
            float fightTurns = GameMath.bossFightTurnsForPlayerDamagePerTurn(stats.effectiveHitPoints, expectedDpt);
            float fairRatio  = GameMath.bossSurvivalCheckRatio(
                    BossBalance.expectedPlayerEffectiveHitPoints(depth), stats.damagePerTurn, fightTurns);

            // R-BOSS-AMMO coverage.
            float demand = BossBalance.modelledAmmoDemandDamage(stats.effectiveHitPoints);
            float reserveDamage = BalanceConfig.RESERVE_BANKING_FLOORS_TARGET
                    * GameMath.floorDemandAtDepth(BalanceSchema.modelFloorDemand(),
                            BalanceConfig.ENEMY_HEALTH_GROWTH, depth);
            float ammoCoverage = demand > 0f
                    ? (reserveDamage + BossBalance.arenaAmmoBudgetDamage(stats.effectiveHitPoints)) / demand
                    : Float.POSITIVE_INFINITY;

            System.out.printf("%-12s %5d %7.1f %8d %8.1f %8.0f %5.2fx %8.2f %8d %7.2fx%n",
                    archetype.displayName, depth, expectedDpt, stats.effectiveHitPoints, stats.damagePerTurn,
                    stats.targetFightTurns, gateMargin, fairRatio, stats.creditReward, ammoCoverage);
        }

        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("  GATE holds when start-weapon TTK >= %.1fx survivable turns (WITH max heals = %.0f%% eHP). "
                        + "phase seams (2-phase): %.0f%% (death).%n",
                BalanceConfig.BOSS_GATE_MIN_DAMAGE_MARGIN,
                BalanceConfig.BOSS_GATE_MODELED_HEAL_SUPPLY_EHP_FRACTION * 100f,
                GameMath.bossPhaseHealthThreshold(1, BalanceConfig.BOSS_PHASE_COUNT) * 100f);

        // Derived verb damages vs the single-hit fairness caps (RULE 3).
        System.out.printf("%-22s %6s %10s %-12s%n", "boss verb", "dmg", "%eHP", "capVerdict");
        for (BalanceSchema.BossVerbSpec verb : BalanceSchema.bossVerbs()) {
            BossStats stats = BossBalance.statsForDepth(verb.archetype, verb.archetype.canonicalDepth);
            int damage = stats.verbDamage(verb.dptFraction);
            float fraction = GameMath.bossSingleHitFractionOfEffectiveHitPoints(
                    damage, BalanceConfig.REFERENCE_PLAYER_EHP);
            String verdict;
            if (fraction > BalanceConfig.BOSS_HARD_SINGLE_HIT_FRACTION) {
                verdict = "BANNED";
            } else if (fraction > BalanceConfig.TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION) {
                verdict = verb.telegraphed ? "TELE-OK" : "NEEDS-TELE";
            } else {
                verdict = "OK";
            }
            System.out.printf("%-22s %6d %9.1f%% %-12s%n", verb.verbName, damage, fraction * 100f, verdict);
        }
    }

    private static void printLegend() {
        System.out.println("LEGEND: in?/gr?/<0.6?/band = OK inside band, UNDER below, OVER above.");
        System.out.println("  sustDPT = (clip*dmg)/(clip+reload).  SCENARIO: turns to clear (R-ROLE), * best, ! ammo-infeasible.");
        System.out.println("  TP = (atkDmg/cadence) * (eHP/refDPT) * posMult.  TTD/TTK = golden ratio.");
        System.out.println("  S = SUPPLY/DEMAND (ranged ammo vs sum of enemy eHP).  perWpnS = that weapon's share.");
        System.out.println("  bankFloors = full reserve / floor demand (anti-hoard target ~"
                + BalanceConfig.RESERVE_BANKING_FLOORS_TARGET + ").  netHpDrain = INCOMING - HEAL_SUPPLY.");
        System.out.println("Out-of-band rows FAIL BalanceAuditTest unless waived — see docs/game-balance-authority.txt.");
    }

    // -----------------------------------------------------------------------------------
    // LADDER (balance-overhaul order 1, R-LADDER) — hits-to-kill / hits-to-die per role at the
    // sampled depths for the on-curve player and every lagging / ahead variant the rule checks.
    // Continuous hits (eHP / per-hit damage); the integer a player counts is the ceiling.
    // -----------------------------------------------------------------------------------
    private static final int[] LADDER_SAMPLE_DEPTHS = {1, 5, 10, 15, 20, 25};

    private static void printLadderTable() {
        System.out.println("LADDER (balance-overhaul order 1, R-LADDER) — continuous hits to KILL (TTK) / to DIE (TTD)");
        System.out.println("  growth: weapon " + BalanceConfig.LADDER_GROWTH + "/level, enemy HP "
                + BalanceConfig.ENEMY_HEALTH_GROWTH + " + dmg " + BalanceConfig.ENEMY_DAMAGE_GROWTH
                + " /floor, vitality " + BalanceConfig.PLAYER_VITALITY_GROWTH + "/char level; gap penalty "
                + BalanceConfig.LEVEL_GAP_PENALTY_BASE + "^levels (floor " + BalanceConfig.LEVEL_GAP_FLOOR
                + ", steepness " + BalanceConfig.LEVEL_GAP_STEEPNESS + ")");
        System.out.printf("  %-24s %5s %7s %7s %7s %7s %7s %7s %7s%n",
                "role (reference)", "depth", "onTTK", "d-2", "d-4", "start", "d+2", "onTTD", "cL-3TTD");
        System.out.println("  ------------------------------------------------------------------------------------");
        for (ge.tbegvadze.toon3d.enemy.EnemyRole role : ge.tbegvadze.toon3d.enemy.EnemyRole.values()) {
            ge.tbegvadze.toon3d.enemy.EnemyType reference = BalanceSchema.ladderReferenceArchetype(role);
            if (reference == null) continue;
            for (int depth : LADDER_SAMPLE_DEPTHS) {
                ExpectedPlayer onCurve = GameMath.expectedPlayerAtDepth(depth);
                float onKill = BalanceSchema.ladderTurnsToKill(reference, depth, onCurve);
                float onDie  = BalanceSchema.ladderTurnsToDie(reference, depth, onCurve);
                String lagTwo  = depth - 2 >= 1 ? String.format("%7.2f", BalanceSchema.ladderTurnsToKill(reference,
                        depth, BalanceSchema.ladderWeaponOffsetPlayer(depth, -2))) : "      -";
                String lagFour = depth - 4 >= 1 ? String.format("%7.2f", BalanceSchema.ladderTurnsToKill(reference,
                        depth, BalanceSchema.ladderWeaponOffsetPlayer(depth, -4))) : "      -";
                float startKill = BalanceSchema.ladderTurnsToKill(reference, depth,
                        BalanceSchema.ladderStartWeaponPlayer(depth));
                float aheadKill = BalanceSchema.ladderTurnsToKill(reference, depth,
                        BalanceSchema.ladderWeaponOffsetPlayer(depth, 2));
                int laggingLevel = GameMath.expectedCharacterLevelAtDepth(depth) - BalanceConfig.LADDER_VITALITY_LAG_LEVELS;
                String vitality = laggingLevel >= 1 ? String.format("%7.2f", BalanceSchema.ladderTurnsToDie(reference,
                        depth, GameMath.expectedPlayer(depth, depth, GameMath.expectedRarityMultiplierAtDepth(depth),
                                laggingLevel))) : "      -";
                System.out.printf("  %-24s %5d %7.2f %s %s %7.2f %7.2f %7.2f %s%n",
                        role + " (" + reference.displayName() + ")", depth, onKill, lagTwo, lagFour,
                        startKill, aheadKill, onDie, vitality);
            }
        }
        System.out.println("  LEVEL-UP RUNG (R-LADDER-AFFORD, <= " + BalanceConfig.LADDER_AFFORD_FRACTION
                + " of one combat floor's credits):");
        for (int depth : LADDER_SAMPLE_DEPTHS) {
            System.out.printf("    depth %2d: price %5d vs combat-floor income %6.0f%n", depth,
                    BalanceSchema.ladderLevelUpPrice(depth), BalanceSchema.combatFloorCreditIncome(depth));
        }
    }
}
