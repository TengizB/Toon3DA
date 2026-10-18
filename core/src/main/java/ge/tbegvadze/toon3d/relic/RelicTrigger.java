package ge.tbegvadze.toon3d.relic;

/** When a relic's rule fires — a UI badge only (the runtime seams are wired per PerkModifier kind). */
public enum RelicTrigger {
    ALWAYS("ALWAYS"),
    ON_KILL("ON KILL"),
    ON_DOOR_OPEN("ON DOOR"),
    ON_FLOOR_ARRIVAL("ON ARRIVAL"),
    ON_HIT_TAKEN("ON HIT"),
    ON_LOW_HEALTH("LOW HP"),
    ON_LETHAL_HIT("LETHAL HIT"),
    ON_ENEMY_SPAWN("ON SPAWN");

    private final String badgeLabel;

    RelicTrigger(String badgeLabel) { this.badgeLabel = badgeLabel; }

    public String badgeLabel() { return badgeLabel; }
}
