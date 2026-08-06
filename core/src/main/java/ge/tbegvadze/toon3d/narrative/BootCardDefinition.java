package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;

/**
 * One immutable row of the boot-card catalog: what a {@link BootCardVariant} PRINTS (Story UI
 * order-3).  Data only — no behaviour, no LibGDX.  Rows are registered once into a
 * {@link BootCardRegistry} ({@link BootCardCatalog#bootstrap}) and read at runtime by
 * {@link BootCardSystem}; nothing switches on a hard-coded card list.
 *
 * <p>Localisation rule (honoured from order-1): a row carries stable {@code textStringId}s only —
 * never the card text itself.  The lines are resolved at present time through {@link StoryStrings}.
 *
 * <p>THE SYSTEM VOICE NEVER EDITORIALISES.  These lines report machine state and nothing else;
 * their horror is entirely in what the player has learned to read into them, which is why
 * "SOUL RESERVE .......... SUFFICIENT" means nothing on run 1 and everything on run 40
 * (story/dialog/system-cards.md).  Keep any new line flat, technical and unaware.
 *
 * <h3>REGION-BANDED STATUS BLOCKS (narrative-rework order-2 C)</h3>
 * A card may register more than one status block, each banded to a deepest-story-region, and
 * {@link #getSystemLineStringIds(StoryRegion)} returns the DEEPEST block the save has earned.  That
 * is how a proper noun waits for the beat that explains it: the reprint card says nothing about a
 * soul reserve until the player has walked the Harvesting Galleries, where ORA starts asking what
 * the printers run on — and from then on it says it on every card, to a player who now knows what it
 * means.  The gate is data, exactly like a wake line's region band; there is no {@code if} for it in
 * game code.
 */
public final class BootCardDefinition {

    /**
     * One banded set of status lines: what the machine prints once the story has reached
     * {@code firstRegion}.  Blocks are held in registration order and resolved deepest-first.
     */
    private static final class StatusBlock {

        private final StoryRegion firstRegion;
        private final String[]    lineStringIds;

        private StatusBlock(StoryRegion firstRegion, String[] lineStringIds) {
            this.firstRegion   = firstRegion;
            this.lineStringIds = lineStringIds;
        }
    }

    private final BootCardVariant   variant;
    private final List<StatusBlock> statusBlocks;
    private final boolean           showsInstanceCounter;
    private final boolean           showsWakeLine;
    private final int               launchStatusLineCount;

    private BootCardDefinition(Builder builder, int resolvedLaunchStatusLineCount) {
        this.variant               = builder.variant;
        // Copied, so a builder reused after build() cannot mutate a registered card.
        this.statusBlocks          = new ArrayList<>(builder.statusBlocks);
        this.showsInstanceCounter  = builder.showsInstanceCounter;
        this.showsWakeLine         = builder.showsWakeLine;
        this.launchStatusLineCount = resolvedLaunchStatusLineCount;
    }

    public BootCardVariant getVariant() { return variant; }

    /**
     * The machine-voice status lines for the shallowest band — the block every save starts on.
     * Localisation ids; resolve via {@link StoryStrings#get(String)}.  The returned array is the
     * row's own; callers must not mutate it (the system only ever reads it).
     */
    public String[] getSystemLineStringIds() {
        return statusBlocks.get(0).lineStringIds;
    }

    /**
     * The status lines this card prints for a save whose deepest region ever reached is
     * {@code region}: the DEEPEST registered block the save has earned, falling back to the
     * shallowest one.  A null region reads as "the story has not started", i.e. the first block.
     */
    public String[] getSystemLineStringIds(StoryRegion region) {
        StatusBlock chosen = statusBlocks.get(0);
        if (region == null) return chosen.lineStringIds;
        for (int blockIndex = 1; blockIndex < statusBlocks.size(); blockIndex++) {
            StatusBlock block = statusBlocks.get(blockIndex);
            // Deepest wins: a later block only takes over once the save has actually reached it.
            if (region.ordinal() >= block.firstRegion.ordinal()
                    && block.firstRegion.ordinal() >= chosen.firstRegion.ordinal()) {
                chosen = block;
            }
        }
        return chosen.lineStringIds;
    }

    /** How many status blocks this card carries (the catalog sweeps and tests read them all). */
    public int getStatusBlockCount() { return statusBlocks.size(); }

    /** The line ids of one status block, by registration index.  Read-only. */
    public String[] getStatusBlockLineStringIds(int blockIndex) {
        return statusBlocks.get(blockIndex).lineStringIds;
    }

    /** The shallowest story region the block at {@code blockIndex} is printed in. */
    public StoryRegion getStatusBlockFirstRegion(int blockIndex) {
        return statusBlocks.get(blockIndex).firstRegion;
    }

    public int getSystemLineCount() { return statusBlocks.get(0).lineStringIds.length; }

    /**
     * How many of the status lines the LAUNCH SCREEN borrows from this card (narrative-rework
     * order-2 A).  Defaults to every line except the last, because a card's closing line is a
     * promise about the run it is about to start ("PRINTING NEW BODY .... STAND BY") and the title
     * screen makes no promises.  The FIRST-PRINT card overrides it to print the whole block: on a
     * save with nothing in it, "there is a copy and it is being made" is not a promise, it is the
     * only explanation the player has of what they are about to be.
     */
    public int getLaunchStatusLineCount() { return launchStatusLineCount; }

    /** True when the quiet "INSTANCE #0048" counter is printed under the status lines. */
    public boolean showsInstanceCounter() { return showsInstanceCounter; }

    /**
     * True when ORA speaks over this card.  Only the default reprint does: the endgame cards are
     * the machine talking alone, which is exactly what makes them land.
     */
    public boolean showsWakeLine() { return showsWakeLine; }

    public static Builder builder(BootCardVariant variant) {
        return new Builder(variant);
    }

    /** Fluent builder — every row is built once at bootstrap, never per frame. */
    public static final class Builder {

        private final BootCardVariant   variant;
        private final List<StatusBlock> statusBlocks         = new ArrayList<>();
        private boolean                 showsInstanceCounter = true;
        private boolean                 showsWakeLine        = false;
        private int                     launchStatusLineCount = -1;   // -1 = "every line but the last"

        private Builder(BootCardVariant variant) {
            if (variant == null) throw new IllegalArgumentException("boot card variant must not be null");
            this.variant = variant;
        }

        /** The machine-voice status lines, in print order.  The block every save starts on. */
        public Builder systemLines(String... stringIds) {
            return systemLinesFrom(StoryRegion.HABITATION_RINGS, stringIds);
        }

        /**
         * A deeper status block: what this card prints once the save has reached {@code firstRegion}.
         * Registering one is how a word waits for the beat that earns it (see the class comment).
         */
        public Builder systemLinesFrom(StoryRegion firstRegion, String... stringIds) {
            if (firstRegion == null) {
                throw new IllegalArgumentException("boot card " + variant + " has a null region band");
            }
            statusBlocks.add(new StatusBlock(firstRegion,
                                             stringIds != null ? stringIds : new String[0]));
            return this;
        }

        public Builder showsInstanceCounter(boolean value) {
            this.showsInstanceCounter = value;
            return this;
        }

        public Builder showsWakeLine(boolean value) {
            this.showsWakeLine = value;
            return this;
        }

        /** Overrides how many lines the launch screen borrows (see {@link #getLaunchStatusLineCount}). */
        public Builder launchStatusLines(int value) {
            this.launchStatusLineCount = value;
            return this;
        }

        public BootCardDefinition build() {
            if (statusBlocks.isEmpty()) {
                throw new IllegalArgumentException("boot card " + variant + " has no system lines");
            }
            int baseLineCount = statusBlocks.get(0).lineStringIds.length;
            for (int blockIndex = 0; blockIndex < statusBlocks.size(); blockIndex++) {
                StatusBlock block = statusBlocks.get(blockIndex);
                if (block.lineStringIds.length == 0) {
                    throw new IllegalArgumentException("boot card " + variant + " has an empty block");
                }
                // Every band prints the same number of rows: the panel is sized once, and a block
                // that is one line shorter than another would make the card jump between saves.
                if (block.lineStringIds.length != baseLineCount) {
                    throw new IllegalArgumentException("boot card " + variant
                            + " has status blocks of differing line counts");
                }
                for (String stringId : block.lineStringIds) {
                    if (stringId == null || stringId.isEmpty()) {
                        throw new IllegalArgumentException(
                                "boot card " + variant + " has an empty system line string id");
                    }
                }
            }
            // Resolved into a local rather than back into the field, so build() leaves the builder
            // exactly as the caller configured it and building twice cannot drift.
            int resolvedLaunchStatusLineCount = launchStatusLineCount < 0
                    ? Math.max(1, baseLineCount - 1)
                    : launchStatusLineCount;
            if (resolvedLaunchStatusLineCount > baseLineCount) {
                throw new IllegalArgumentException("boot card " + variant
                        + " lends the launch screen more lines than it prints");
            }
            return new BootCardDefinition(this, resolvedLaunchStatusLineCount);
        }
    }
}
