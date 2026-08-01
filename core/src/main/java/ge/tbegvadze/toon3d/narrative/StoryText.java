package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, headless text helpers for the story UI.  No LibGDX, no side effects — so the
 * wrapping rule ("max ~40 characters per line; wrap, never shrink") is unit-testable
 * without a render context.
 *
 * The render side pre-wraps a resolved line ONCE (not inside render()) with
 * {@link #wrapToMaxChars} and then draws the resulting lines; render() never re-wraps.
 */
public final class StoryText {

    private StoryText() {}

    /**
     * Word-wraps {@code text} so that each returned line is at most {@code maxChars}
     * characters wide.  Splits on spaces; a single word longer than {@code maxChars} is
     * hard-broken so a line never exceeds the cap.  Collapses runs of spaces between
     * words.  Never shrinks or truncates content — every character of the input appears
     * in the output.
     *
     * @param text     the resolved (already localised) line to wrap; null/blank ⇒ empty list
     * @param maxChars the per-line character cap; values &lt; 1 are treated as 1
     * @return one entry per wrapped line, in order
     */
    public static List<String> wrapToMaxChars(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        int cap = Math.max(1, maxChars);

        StringBuilder currentLine = new StringBuilder();
        int wordStart = 0;
        int length = text.length();
        for (int scanIndex = 0; scanIndex <= length; scanIndex++) {
            boolean atBoundary = scanIndex == length || text.charAt(scanIndex) == ' ';
            if (!atBoundary) continue;

            if (scanIndex > wordStart) {
                String word = text.substring(wordStart, scanIndex);
                appendWord(lines, currentLine, word, cap);
            }
            wordStart = scanIndex + 1;
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    /** Appends one word to the running line, wrapping (and hard-breaking) as needed. */
    private static void appendWord(List<String> lines, StringBuilder currentLine, String word, int cap) {
        // Hard-break a word that cannot fit on any line by itself.
        while (word.length() > cap) {
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            lines.add(word.substring(0, cap));
            word = word.substring(cap);
        }
        if (word.isEmpty()) return;

        int projected = currentLine.length() == 0
                ? word.length()
                : currentLine.length() + 1 + word.length();
        if (projected > cap && currentLine.length() > 0) {
            lines.add(currentLine.toString());
            currentLine.setLength(0);
        }
        if (currentLine.length() > 0) currentLine.append(' ');
        currentLine.append(word);
    }
}
