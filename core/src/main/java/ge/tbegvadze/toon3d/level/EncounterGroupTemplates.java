package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.route.NodeSupplySpec.EncounterKind;
import ge.tbegvadze.toon3d.util.BalanceConfig;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * The shipped encounter GROUP shapes (balance-overhaul order 2, E2; reshaped by mixed-encounter-groups)
 * — one {@code register()} row each. Chaff come in PAIRS of DIFFERENT archetypes (so chaff still never
 * spawns alone: every chaff archetype fields at least {@link BalanceConfig#GROUP_CHAFF_SLOT_MIN}), soldiers
 * and heavies as SINGLES of different archetypes, and every group mixes behaviours, so a room asks which
 * one to kill first. The only same-type cluster above a pair is the rare BROOD of
 * {@link BalanceConfig#GROUP_BROOD_SIZE}.
 */
public final class EncounterGroupTemplates {

    public static final String RABBLE   = "RABBLE";
    public static final String SKIRMISH = "SKIRMISH";
    public static final String FIRETEAM = "FIRETEAM";
    public static final String ESCORT   = "ESCORT";
    public static final String BATTERY  = "BATTERY";
    public static final String WARBAND  = "WARBAND";
    public static final String HUNTER   = "HUNTER";
    public static final String BROOD    = "BROOD";
    public static final String PAIR     = "PAIR";
    public static final String LOOKOUT  = "LOOKOUT";

    private EncounterGroupTemplates() {}

    /** Registers the ten default shapes. */
    public static void registerAll(EncounterGroupTemplateRegistry registry) {
        int pair  = BalanceConfig.GROUP_CHAFF_SLOT_MIN;
        int brood = BalanceConfig.GROUP_BROOD_SIZE;
        EnumSet<EncounterKind> everyFight = EnumSet.of(EncounterKind.COMBAT, EncounterKind.ELITE, EncounterKind.CALM);
        EnumSet<EncounterKind> realFights = EnumSet.of(EncounterKind.COMBAT, EncounterKind.ELITE);

        // RABBLE — 2-3 pairs of DIFFERENT chaff: the bread-and-butter group (was PACK, 3-5 of one type).
        registry.register(new EncounterGroupTemplate(RABBLE, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, 2 * pair, 3 * pair, SlotFill.PAIRS)),
                1, everyFight, false, false, 3.5f));
        // SKIRMISH — 1-2 soldiers + a chaff pair: the small mixed shape every fight kind can field.
        registry.register(new EncounterGroupTemplate(SKIRMISH, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.SOLDIER, 1, 2, SlotFill.SINGLES),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, pair, pair, SlotFill.PAIRS)),
                1, everyFight, false, false, 1.5f));
        // FIRETEAM — 2 different soldiers + a chaff pair.
        registry.register(new EncounterGroupTemplate(FIRETEAM, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.SOLDIER, 2, 2, SlotFill.SINGLES),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, pair, pair, SlotFill.PAIRS)),
                1, everyFight, false, false, 2f));
        // ESCORT — 1 bruiser + 1-2 chaff pairs: a COMBAT floor's anchor.
        registry.register(new EncounterGroupTemplate(ESCORT, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.BRUISER, 1, 1, SlotFill.SINGLES),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, pair, 2 * pair, SlotFill.PAIRS)),
                1, realFights, true, false, 1f));
        // BATTERY — 1-2 ranged soldiers behind a melee chaff pair: break the lane or close the gap.
        registry.register(new EncounterGroupTemplate(BATTERY, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.RANGED_SOLDIER, 1, 2, SlotFill.SINGLES),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.MELEE_CHAFF, pair, pair, SlotFill.PAIRS)),
                1, realFights, false, false, 1.5f));
        // WARBAND — 1 mini-elite + 2 different soldiers: an ELITE floor's anchor, from depth 3.
        registry.register(new EncounterGroupTemplate(WARBAND, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.MINI_ELITE, 1, 1, SlotFill.SINGLES),
                new EncounterGroupTemplate.Slot(EncounterGroupRole.SOLDIER, 2, 2, SlotFill.SINGLES)),
                BalanceConfig.GROUP_WARBAND_MIN_DEPTH, EnumSet.of(EncounterKind.ELITE), true, false, 1f));
        // HUNTER — one bruiser or flanker alone, at most GROUP_HUNTER_MAX_FRACTION of a floor's groups.
        registry.register(new EncounterGroupTemplate(HUNTER, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.HUNTER, 1, 1, SlotFill.SINGLES)),
                1, realFights, false, true, 1f));
        // BROOD — 3 of ONE chaff archetype: the rare nest, eligible on GROUP_BROOD_FLOOR_CHANCE of floors.
        registry.register(new EncounterGroupTemplate(BROOD, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, brood, brood, SlotFill.BROOD)),
                1, everyFight, false, false, 1f));
        // PAIR — one chaff pair, CALM floors only: their per-group threat cap is too small for a RABBLE at
        // shallow depth, and a pair is the basic chaff unit, never a "large number" of one type.
        registry.register(new EncounterGroupTemplate(PAIR, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.CHAFF, pair, pair, SlotFill.PAIRS)),
                1, EnumSet.of(EncounterKind.CALM), false, false, 1f));
        // LOOKOUT — one soldier alone, CALM floors only, at most GROUP_LOOKOUT_MAX_PER_FLOOR a floor: the odd body a pairs-only floor cannot field, and a
        // second behaviour beside the pairs (a CALM floor has no E4 lone-enemy rule).
        registry.register(new EncounterGroupTemplate(LOOKOUT, Arrays.asList(
                new EncounterGroupTemplate.Slot(EncounterGroupRole.SOLDIER, 1, 1, SlotFill.SINGLES)),
                1, EnumSet.of(EncounterKind.CALM), false, false, 1f, BalanceConfig.GROUP_LOOKOUT_MAX_PER_FLOOR));
    }
}
