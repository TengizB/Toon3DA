package ge.tbegvadze.toon3d.narrative;

/**
 * One tappable answer inside an exchange (Story UI order-4) — data only, no behaviour, no LibGDX.
 *
 * <p>An option carries the player's line, what kind of choice it is, and every consequence of
 * picking it: the stance nudge, the reply that comes back, an optional codex unlock, an optional
 * small reward, and an optional follow-up exchange.  {@link ExchangeSystem} applies all of it; the
 * renderer only draws {@link #getPlayerLineStringId()} on a button.
 *
 * <h3>Writing rules for the player line</h3>
 * ONE short line — it has to fit a button and be readable at a glance, or the player stops reading
 * the options and starts mashing.  A test enforces
 * {@code StoryUiConstants.STORY_EXCHANGE_OPTION_MAX_CHARS}.  Options must be DISTINCT from each
 * other at a glance too: three shades of "yes" is the same as no choice at all.
 *
 * <p>Localisation rule: a row carries stable string ids only, never the text itself.
 */
public final class ExchangeOption {

    private final String             id;
    private final String             playerLineStringId;
    private final ExchangeOptionKind kind;
    private final int[]              stanceNudges;
    private final String             replyStringId;
    private final Speaker            replySpeaker;
    private final String             unlocksCodexId;
    private final String             rewardItemTypeName;
    private final int                rewardQuantity;
    private final String             nextExchangeId;

    private ExchangeOption(Builder builder) {
        this.id                 = builder.id;
        this.playerLineStringId = builder.playerLineStringId;
        this.kind               = builder.kind;
        this.stanceNudges       = builder.stanceNudges;
        this.replyStringId      = builder.replyStringId;
        this.replySpeaker       = builder.replySpeaker;
        this.unlocksCodexId     = builder.unlocksCodexId;
        this.rewardItemTypeName = builder.rewardItemTypeName;
        this.rewardQuantity     = builder.rewardQuantity;
        this.nextExchangeId     = builder.nextExchangeId;
    }

    /** Stable id, unique within its exchange.  Doubles as the recorded value of a CONSEQUENTIAL
     *  outcome, so never change a shipped one. */
    public String getId() { return id; }

    /** Localisation id of the player's line — the text drawn on the button. */
    public String getPlayerLineStringId() { return playerLineStringId; }

    public ExchangeOptionKind getKind() { return kind; }

    /** Signed nudge this option applies to one leaning.  Zero for most axes of most options. */
    public int getStanceNudge(Stance stance) {
        return stanceNudges[stance.ordinal()];
    }

    /** True when picking this option moves the hidden stance model at all. */
    public boolean hasStanceEffect() {
        for (int stanceIndex = 0; stanceIndex < stanceNudges.length; stanceIndex++) {
            if (stanceNudges[stanceIndex] != 0) return true;
        }
        return false;
    }

    /** Localisation id of the answer that comes back, or null when the exchange just ends. */
    public String getReplyStringId() { return replyStringId; }

    /** Who answers.  Defaults to the exchange's own speaker; a row may hand the reply to another
     *  voice (the Organization orders, ORA mutters the truth back). */
    public Speaker getReplySpeaker() { return replySpeaker; }

    /** Codex entry unlocked by picking this option (order-6 reads it back), or null. */
    public String getUnlocksCodexId() { return unlocksCodexId; }

    /** {@code ItemType} name of a small reward this option reveals, or null.  Resolved by the
     *  engine so the narrative layer never imports the item package. */
    public String getRewardItemTypeName() { return rewardItemTypeName; }

    public int getRewardQuantity() { return rewardQuantity; }

    /** Id of a follow-up exchange opened immediately after this one, or null. */
    public String getNextExchangeId() { return nextExchangeId; }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder — every option is built once at bootstrap, never per frame. */
    public static final class Builder {

        private final String id;
        private String             playerLineStringId;
        private ExchangeOptionKind kind         = ExchangeOptionKind.STANCE;
        private final int[]        stanceNudges = new int[Stance.values().length];
        private String             replyStringId;
        private Speaker            replySpeaker;
        private String             unlocksCodexId;
        private String             rewardItemTypeName;
        private int                rewardQuantity;
        private String             nextExchangeId;

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("exchange option id must not be empty");
            }
            this.id = id;
        }

        public Builder playerLineStringId(String value) { this.playerLineStringId = value; return this; }
        public Builder kind(ExchangeOptionKind value)   { this.kind               = value; return this; }
        public Builder replyStringId(String value)      { this.replyStringId      = value; return this; }
        public Builder replySpeaker(Speaker value)      { this.replySpeaker       = value; return this; }
        public Builder unlocksCodexId(String value)     { this.unlocksCodexId     = value; return this; }
        public Builder nextExchangeId(String value)     { this.nextExchangeId     = value; return this; }

        /** Nudges one leaning.  May be called once per {@link Stance}; amounts accumulate. */
        public Builder stanceNudge(Stance stance, int amount) {
            if (stance == null) throw new IllegalArgumentException("stance must not be null");
            this.stanceNudges[stance.ordinal()] += amount;
            return this;
        }

        /**
         * A small reward the option reveals — the "pay them for reading" lever.  {@code itemTypeName}
         * is an {@code item/ItemType} enum name; a test asserts every catalogued name resolves.
         */
        public Builder reward(String itemTypeName, int quantity) {
            this.rewardItemTypeName = itemTypeName;
            this.rewardQuantity     = quantity;
            return this;
        }

        public ExchangeOption build() {
            if (playerLineStringId == null || playerLineStringId.isEmpty()) {
                throw new IllegalArgumentException("exchange option " + id + " has no player line");
            }
            if (kind == null) {
                throw new IllegalArgumentException("exchange option " + id + " has no kind");
            }
            if (rewardItemTypeName != null && rewardQuantity <= 0) {
                throw new IllegalArgumentException("exchange option " + id + " rewards nothing");
            }
            return new ExchangeOption(this);
        }
    }
}
