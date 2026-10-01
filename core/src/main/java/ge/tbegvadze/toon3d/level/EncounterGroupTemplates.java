package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.route.NodeSupplySpec.EncounterKind;
import ge.tbegvadze.toon3d.util.BalanceConfig;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * The shipped encounter GROUP shapes (balance-overhaul order 2, E2) — one {@code register()} row each.
 * Chaff never spawns alone: every slot that admits chaff fields at least
 * {@link BalanceConfig#GROUP_CHAFF_SLOT_MIN} of ONE archetype.
 */
public final class EncounterGroupTemplates {

    public static final String PACK     = "PACK";
    public static final String FIRETEAM = "FIRETEAM";
    public static final String ESCORT   = "ESCORT";
    public static final String BATTERY  = "BATTERY";
    public static final String WARBAND  = "WARBAND";
    public static final String HUNTER   = "HUNTER";

    private EncounterGroupTemplates() {}

    /** Registers the six default shapes. */
    public static void registerAll(EncounterGroupTemplateRegistry registry) {
        int chaff = BalanceConfig.GROUP_CHAFF_SLOT_MIN;
        EnumSet<EncounterKind> everyFight = EnumSet.of(EncounterKind.COMBAT, EncounterKind.ELITE, EncounterKind.CALM);
        EnumSet<EncounterKind> realFights = EnumSet.of(EncounterKind.COMBAT, EncounterKind.ELITE);

        // PACK — 3-5 chaff of one type: the bread-and-butter group.
        registry.register(new EncounterGroupTemplate(PACK, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, 3, 5, true)),
                1, everyFight, false, false, 3f));
        // FIRETEAM — 2 soldiers + 2 chaff.
        registry.register(new EncounterGroupTemplate(FIRETEAM, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.SOLDIER, 2, 2, false),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, chaff, chaff, true)),
                1, everyFight, false, false, 2f));
        // ESCORT — 1 bruiser + 2-3 chaff: a COMBAT floor's anchor.
        registry.register(new EncounterGroupTemplate(ESCORT, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.BRUISER, 1, 1, false),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, chaff, chaff + 1, true)),
                1, realFights, true, false, 1f));
        // BATTERY — 1-2 ranged soldiers behind 2 melee chaff: break the lane or close the gap.
        registry.register(new EncounterGroupTemplate(BATTERY, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.RANGED_SOLDIER, 1, 2, false),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.MELEE_CHAFF, chaff, chaff, true)),
                1, realFights, false, false, 2f));
        // WARBAND — 1 mini-elite + 2 soldiers: an ELITE floor's anchor, from depth 3.
        registry.register(new EncounterGroupTemplate(WARBAND, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.MINI_ELITE, 1, 1, false),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.SOLDIER, 2, 2, false)),
                BalanceConfig.GROUP_WARBAND_MIN_DEPTH, EnumSet.of(EncounterKind.ELITE), true, false, 1f));
        // HUNTER — one bruiser or flanker alone, at most GROUP_HUNTER_MAX_FRACTION of a floor's groups.
        registry.register(new EncounterGroupTemplate(HUNTER, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.HUNTER, 1, 1, false)),
                1, realFights, false, true, 1f));
    }
}
