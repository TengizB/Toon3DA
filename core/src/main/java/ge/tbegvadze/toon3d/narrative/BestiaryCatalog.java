package ge.tbegvadze.toon3d.narrative;

import java.util.Locale;

import ge.tbegvadze.toon3d.enemy.EnemyFamily;
import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.enemy.SpecialAbility;

/**
 * THE BESTIARY VOICE (narrative-rework order-5) — ORA introduces the things trying to kill the
 * player, and every introduction does two jobs at once: it says WHAT IT IS (which is the story —
 * the moral geometry of the bestiary is the whole premise, {@code story/00-premise.md}) and HOW TO
 * SURVIVE IT (which is the reason the player reads it).
 *
 * <h3>Four kinds of row, all on the same idiom</h3>
 * <ul>
 *   <li><b>The FAMILY line</b> — one per {@link EnemyFamily}, on the first sight of that family.
 *       TWO rows each: an EARLY one that names the thing and says how it kills you, and a
 *       REFRAME one from the Wound down that drops the tactical half (by Region 4 the player knows
 *       how to fight) and says what they have actually been shooting.  Which one a player gets is
 *       the region gate, not a conditional: meeting the undead on floor two gets the crew serials,
 *       meeting them for the first time in the Wound gets "that was crew".</li>
 *   <li><b>The ARCHETYPE line</b> — one per {@link EnemyType}, on the first sight of that TYPE, on
 *       the same trigger with the type's own subject key.  Each is ORA's translation of that
 *       archetype's {@link EnemyType#tacticalVerb()} — the designer sentence is the SOURCE OF TRUTH
 *       for what the line must convey, and it is read here at bootstrap so the two cannot drift into
 *       a type that ships with no voice.  A row names ONE behaviour: where an archetype does two
 *       things, she says the one that kills players.</li>
 *   <li><b>The ABILITY line</b> — one per {@link SpecialAbility}, the first time the player watches
 *       one RESOLVE (not the first time it is telegraphed).  These are the rules that kill a player
 *       who does not know them, so every one is {@code STORY_CRITICAL}.</li>
 *   <li><b>The BOSS pair</b> — the one enemy that deserves a stop.  A bark, never an exchange: the
 *       player is about to fight, and a modal there is cruel.  Its death carries a REVEAL, on a
 *       moment every player who gets that far is guaranteed to reach.</li>
 * </ul>
 *
 * <p><b>Coverage is structural, not a checklist.</b> Every row is registered by looping the game's
 * own enums, so a new family, archetype or special ability cannot ship without a line — it arrives
 * with an unresolved string id, which the localisation sweep fails on.  Adding art to the bestiary
 * and adding a voice for it are the same commit by construction.
 *
 * <p>NO LINE MAY STATE A NUMBER.  She says "slow", "thick", "fast", "armoured" — the HUD owns
 * numbers, and a line that quotes one is a line that goes stale the next time the balance moves.
 *
 * <p>Headless: no LibGDX imports.  It reads the enemy layer's DATA enums only (family, role,
 * archetype, ability), never a manager or a renderer.
 */
public final class BestiaryCatalog {

    /** Row id of the line ORA says on entering a boss arena for the first time. */
    public static final String BOSS_ARENA_BARK_ID = "bark.boss.first";
    /** Row id of the REVEAL that rides the first boss's death. */
    public static final String BOSS_DEFEATED_BARK_ID = "bark.boss.first.dead";

    private BestiaryCatalog() {}

    /**
     * Registers every bestiary row into {@code registry}.  Called by {@link BarkCatalog#bootstrap};
     * there is no separate entry point, because the bestiary is part of the one bark catalog.
     */
    static void bootstrap(BarkRegistry registry) {
        registerFamilies(registry);
        registerArchetypes(registry);
        registerAbilities(registry);
        registerBossBeats(registry);
    }

    // -------------------------------------------------------------------------
    // A. The FAMILY line — what it was, and what it does to you
    // -------------------------------------------------------------------------

    /**
     * Two rows per family: the early one (down to the Reliquary) carries the name AND the tactic in
     * one line; the deep one (from the Wound) is the reframe, and carries that family's column of
     * the premise's system-as-story table — undead and insect are the human cost, machine is the
     * Organization's own immune system, demon and golem are the planet defending itself.
     *
     * <p>Both are one-shot and {@code STORY_CRITICAL}: "what am I fighting" is a comprehension-bar
     * question ({@code docs/narrative-authority.txt} SECTION 5), so it may not ride a channel a
     * skimmer can lose.  The two rows carry different ids, so a player who met a family in Region 1
     * still gets its reframe the first time they meet it again at the bottom.
     */
    private static void registerFamilies(BarkRegistry registry) {
        for (EnemyFamily family : EnemyFamily.values()) {
            String subjectKey = family.name();
            String earlyId    = familyBarkId(family);
            registry.register(bestiaryRow(earlyId)
                    .subjectKey(subjectKey)
                    .regionBand(StoryRegion.HABITATION_RINGS, StoryRegion.RELIQUARY)
                    .build());
            registry.register(bestiaryRow(deepFamilyBarkId(family))
                    .subjectKey(subjectKey)
                    .regionFrom(StoryRegion.WOUND)
                    .build());
        }
    }

    /** Catalog id of a family's EARLY (name-it-and-fight-it) row. */
    public static String familyBarkId(EnemyFamily family) {
        return "bark.family." + catalogKey(family);
    }

    /** Catalog id of a family's REFRAME row — the one that says what it actually was. */
    public static String deepFamilyBarkId(EnemyFamily family) {
        return "bark.family." + catalogKey(family) + ".deep";
    }

    // -------------------------------------------------------------------------
    // B. The ARCHETYPE line — one behaviour, the one that kills players
    // -------------------------------------------------------------------------

    /**
     * One row per archetype, keyed by {@code EnemyType.name()} on the same first-sight trigger the
     * families use.  Non-boss rows are {@link BarkPriority#REACTIVE}, so a spike HOLDS them until the
     * lull — a tactic delivered mid-swing is a tactic nobody reads.  A BOSS is the exception: its
     * fight is the whole floor, there is no lull coming, and the advice is the fight.
     *
     * <p>{@link EnemyType#tacticalVerb()} is read here rather than printed: it is written in
     * designer voice (caps-prefixed, em-dashed, far past the two-line panel), so the shipped line is
     * ORA's translation of it.  Reading it at bootstrap is what keeps the pair honest — an archetype
     * that lost its verb fails the build here instead of quietly shipping a line about nothing.
     */
    private static void registerArchetypes(BarkRegistry registry) {
        for (EnemyType type : EnemyType.values()) {
            String tacticalVerb = type.tacticalVerb();
            if (tacticalVerb == null || tacticalVerb.trim().isEmpty()) {
                throw new IllegalStateException(
                        "enemy archetype " + type.name() + " has no tacticalVerb() for its bestiary line");
            }
            boolean isBoss = type.role() == EnemyRole.BOSS;
            registry.register(bestiaryRow(archetypeBarkId(type))
                    .subjectKey(type.name())
                    .priority(isBoss ? BarkPriority.STORY_CRITICAL : BarkPriority.REACTIVE)
                    .build());
        }
    }

    /** Catalog id of an archetype's first-sight line. */
    public static String archetypeBarkId(EnemyType type) {
        return "bark.enemy." + catalogKey(type);
    }

    // -------------------------------------------------------------------------
    // C. The ABILITY line — taught by the enemy that first uses one
    // -------------------------------------------------------------------------

    /**
     * One row per special ability, fired the first time the player SEES it resolve.  Five abilities
     * ship and the game has never explained any of them; each of these is the rule a player
     * otherwise learns by dying, so all five are one-shot {@code STORY_CRITICAL} — the one class of
     * line that must land even mid-fight, because mid-fight is the only place it is any use.
     */
    private static void registerAbilities(BarkRegistry registry) {
        for (SpecialAbility ability : SpecialAbility.values()) {
            registry.register(bestiaryRow(abilityBarkId(ability))
                    .trigger(BarkTrigger.ENEMY_ABILITY_RESOLVED)
                    .subjectKey(ability.name())
                    .build());
        }
    }

    /** Catalog id of a special ability's first-resolution line. */
    public static String abilityBarkId(SpecialAbility ability) {
        return "bark.ability." + catalogKey(ability);
    }

    // -------------------------------------------------------------------------
    // D. The FIRST BOSS — the one enemy that deserves a stop
    // -------------------------------------------------------------------------

    /**
     * Two one-shot beats around the first boss arena the player ever walks into.  The first says the
     * room is deliberate; the second, on the kill, is a REVEAL riding a moment the player is
     * guaranteed to reach — the facility knew what was down here, built a door for it, and kept
     * sending shifts past it anyway.
     */
    private static void registerBossBeats(BarkRegistry registry) {
        registry.register(bestiaryRow(BOSS_ARENA_BARK_ID)
                .trigger(BarkTrigger.BOSS_ARENA_ENTERED)
                .build());
        registry.register(bestiaryRow(BOSS_DEFEATED_BARK_ID)
                .trigger(BarkTrigger.BOSS_DEFEATED)
                .build());
    }

    // -------------------------------------------------------------------------
    // Shared row shape
    // -------------------------------------------------------------------------

    /**
     * The prologue every bestiary row shares: ORA, first-sight moment, lore tone, one-shot for the
     * life of the save, and mandatory unless a caller lowers it.  Region-unrestricted by default —
     * an archetype or an ability may debut at any depth, and a line the player needs must not be
     * gated on where they happened to meet the thing.
     */
    private static BarkDefinition.Builder bestiaryRow(String id) {
        return BarkDefinition.builder(id)
                .speaker(Speaker.AI)
                .textStringId("story." + id)
                .trigger(BarkTrigger.ENEMY_FAMILY_FIRST_SEEN)
                .priority(BarkPriority.STORY_CRITICAL)
                .tone(BarkTone.LORE)
                .oneShot(true);
    }

    /** Lower-case catalog key for an enum constant — the id fragment and the properties key. */
    private static String catalogKey(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
