package ge.tbegvadze.toon3d.route;

/**
 * Presentation / telemetry flags a {@link NodeLevelProfile} attaches to a {@link LevelPlan} for the
 * floor it builds — the seam that lets a HEADLESS profile ask {@code World} for effects it cannot
 * touch itself (the profile has no LibGDX / GameState / EventText access, by design). {@code World}
 * reads these AFTER the floor is rebuilt and applies them: light the emergency red-alert pulse, show
 * a first-frame reveal sting, and append the resolved outcome to run telemetry.
 *
 * <p>Introduced by order-9 for the DANGER / GAMBLE nodes: ELITE lights red-alert on arrival, and
 * MYSTERY stamps a "SURPRISE" sting plus logs its hidden-but-resolved outcome so the post-run route
 * story is complete. Immutable value object; {@link #NONE} is the do-nothing default carried by every
 * plan that asks for no effects. Pure / headless — no LibGDX imports.
 */
public final class FloorEffects {

    /** The do-nothing default: no red alert, no sting, no telemetry note. */
    public static final FloorEffects NONE = new FloorEffects(false, null, null, null);

    private final boolean redAlert;
    private final String  stingText;
    private final String  statLogToken;
    private final String  twistId;

    private FloorEffects(boolean redAlert, String stingText, String statLogToken, String twistId) {
        this.redAlert     = redAlert;
        this.stingText    = stingText;
        this.statLogToken = statLogToken;
        this.twistId      = twistId;
    }

    /** A plain red-alert floor (ELITE): the emergency lighting pulses from the first frame. */
    public static FloorEffects redAlert() {
        return new FloorEffects(true, null, null, null);
    }

    /** Returns a copy with the red-alert pulse forced on. */
    public FloorEffects withRedAlert() {
        return new FloorEffects(true, stingText, statLogToken, twistId);
    }

    /** Returns a copy carrying a first-frame reveal sting (an EventText line shown on entry). */
    public FloorEffects withSting(String text) {
        return new FloorEffects(redAlert, text, statLogToken, twistId);
    }

    /** Returns a copy carrying a telemetry token appended to the run's route story (e.g. "VAULT"). */
    public FloorEffects withStatLog(String token) {
        return new FloorEffects(redAlert, stingText, token, twistId);
    }

    /**
     * Returns a copy carrying the FLOOR TWIST this floor was built with (floor-twists R5): World / SimWorld
     * look its {@link NodeAffixDefinition} up and apply the runtime modifiers after the rebuild.
     */
    public FloorEffects withTwist(String id) {
        return new FloorEffects(redAlert, stingText, statLogToken, id);
    }

    /** The floor twist id this floor carries, or {@code null} for none. */
    public String twistId() {
        return twistId;
    }

    /** Whether the emergency red-alert pulse should be lit on this floor. */
    public boolean redAlertActive() {
        return redAlert;
    }

    /** The first-frame reveal sting, or {@code null} for none. */
    public String stingText() {
        return stingText;
    }

    /** The run-telemetry token to annotate the committed node with, or {@code null} for none. */
    public String statLogToken() {
        return statLogToken;
    }

    /** Whether this bundle asks {@code World} to do anything at all. */
    public boolean hasAny() {
        return redAlert || stingText != null || statLogToken != null || twistId != null;
    }
}
