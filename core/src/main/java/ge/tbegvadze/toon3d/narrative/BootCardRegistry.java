package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The boot-card catalog (Story UI order-3): {@link BootCardVariant} -&gt; the card it prints, plus
 * the pool of ORA wake-up lines the default reprint draws from.  Registration is the ONLY edit
 * point — adding an ORA reprint line is one {@link #registerWakeLine} call in
 * {@link BootCardCatalog}, and a new ending card is one {@link #registerCard} call.  Nothing in
 * {@code World}, the renderer or {@link BootCardSystem} switches on a hard-coded card list (the
 * same discipline as {@code route/RouteRegistries} and {@link BarkRegistry}).
 *
 * <p>Wake lines are returned READ-ONLY in registration order; callers must never mutate them.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class BootCardRegistry {

    private final Map<BootCardVariant, BootCardDefinition> cardsByVariant =
            new EnumMap<>(BootCardVariant.class);
    private final List<BootWakeLine>        wakeLines     = new ArrayList<>();
    private final Map<String, BootWakeLine> wakeLinesById = new HashMap<>();

    /** Registers one variant's card.  A duplicate variant is a catalog bug, so it throws. */
    public BootCardRegistry registerCard(BootCardDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        if (cardsByVariant.containsKey(definition.getVariant())) {
            throw new IllegalStateException("duplicate boot card variant: " + definition.getVariant());
        }
        cardsByVariant.put(definition.getVariant(), definition);
        return this;
    }

    /** Registers one ORA wake-up line.  Ids are unique — a duplicate id is a catalog bug. */
    public BootCardRegistry registerWakeLine(BootWakeLine line) {
        if (line == null) throw new IllegalArgumentException("wake line must not be null");
        if (wakeLinesById.containsKey(line.getId())) {
            throw new IllegalStateException("duplicate wake line id: " + line.getId());
        }
        wakeLinesById.put(line.getId(), line);
        wakeLines.add(line);
        return this;
    }

    /** The card registered for this variant, or null when the variant prints nothing (MERGE). */
    public BootCardDefinition getCard(BootCardVariant variant) {
        return cardsByVariant.get(variant);
    }

    /** Every registered wake line, in registration order.  Never null; read-only. */
    public List<BootWakeLine> getWakeLines() {
        return Collections.unmodifiableList(wakeLines);
    }

    /** Every registered card (tests / audits). */
    public List<BootCardDefinition> getCards() {
        return new ArrayList<>(cardsByVariant.values());
    }

    public int cardCount() { return cardsByVariant.size(); }

    public int wakeLineCount() { return wakeLines.size(); }

    /** True until something is registered — lets {@link BootCardCatalog#bootstrap} be idempotent. */
    public boolean isEmpty() {
        return cardsByVariant.isEmpty() && wakeLines.isEmpty();
    }
}
