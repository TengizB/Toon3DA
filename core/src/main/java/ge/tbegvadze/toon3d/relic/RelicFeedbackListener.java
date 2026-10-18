package ge.tbegvadze.toon3d.relic;

/** Receives the text of a discrete relic trigger ("+9 HP - FIELD DRESSING"). World routes it to event text; the sim passes null. */
public interface RelicFeedbackListener {
    void onRelicTriggered(String text, boolean positive);
}
