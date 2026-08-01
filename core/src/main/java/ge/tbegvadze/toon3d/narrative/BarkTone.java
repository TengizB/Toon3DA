package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * What a bark is FOR — the balance knob between "this moves the story" and "this is ORA being dry"
 * (Story UI order-2).
 *
 * <p>ORA is written "useful before funny before sad" (story/dialog/ai-assistant.md), and a narrator
 * who jokes at every moment stops being funny and starts being noise.  So each catalogued line
 * declares its tone, and the tone sets its default selection weight: {@link #LORE} lines are
 * roughly two-and-a-half times more likely to be picked than {@link #LEVITY} ones
 * ({@code StoryUiConstants.STORY_BARK_WEIGHT_*}).  She still cracks jokes — just not constantly,
 * and the balance is tuned in the constants file rather than in selection code.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum BarkTone {

    /**
     * Carries the story: what this place is, what the Organization did here, what ORA is working
     * out, what the planet is.  The default for a new line — the channel exists to tell the story.
     */
    LORE(StoryUiConstants.STORY_BARK_WEIGHT_LORE),

    /**
     * ORA being ORA: a dry aside, a joke to cut the tension.  Deliberately the rarer half; the
     * humour lands because it is occasional and because the player likes her by then.
     */
    LEVITY(StoryUiConstants.STORY_BARK_WEIGHT_LEVITY);

    private final float selectionWeight;

    BarkTone(float selectionWeight) {
        this.selectionWeight = selectionWeight;
    }

    /** Default relative weight of a line with this tone inside its pool. */
    public float getSelectionWeight() {
        return selectionWeight;
    }
}
