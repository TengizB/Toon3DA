package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bark catalog: {@link BarkTrigger} -&gt; the rows that may fire on it (Story UI order-2).
 * Registration is the ONLY edit point — adding a line is one {@link #register} call in
 * {@link BarkCatalog}, and nothing in the world, the renderer or {@link BarkSystem} switches on a
 * hard-coded bark list (the same discipline as {@code route/RouteRegistries}).
 *
 * <p>Rows are grouped by trigger at registration time, so runtime selection is an indexed walk over
 * one small list — no lookup allocation on the delivery path.  The per-trigger lists are returned
 * READ-ONLY; callers must never mutate them.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class BarkRegistry {

    private final Map<BarkTrigger, List<BarkDefinition>> byTrigger = new EnumMap<>(BarkTrigger.class);
    private final Map<String, BarkDefinition>            byId      = new HashMap<>();

    /** Registers one row.  Ids are unique — a duplicate id is a catalog bug, so it throws. */
    public BarkRegistry register(BarkDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        if (byId.containsKey(definition.getId())) {
            throw new IllegalStateException("duplicate bark id: " + definition.getId());
        }
        byId.put(definition.getId(), definition);
        List<BarkDefinition> triggerRows = byTrigger.get(definition.getTrigger());
        if (triggerRows == null) {
            triggerRows = new ArrayList<>();
            byTrigger.put(definition.getTrigger(), triggerRows);
        }
        triggerRows.add(definition);
        return this;
    }

    /** All rows registered for a trigger, in registration order.  Never null; read-only. */
    public List<BarkDefinition> getForTrigger(BarkTrigger trigger) {
        List<BarkDefinition> triggerRows = byTrigger.get(trigger);
        return triggerRows != null ? triggerRows : Collections.<BarkDefinition>emptyList();
    }

    /** The row with this id, or null. */
    public BarkDefinition getById(String barkId) {
        return byId.get(barkId);
    }

    /** Every registered row (tests / audits). */
    public List<BarkDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /** True once at least one row is registered — lets {@link BarkCatalog#bootstrap} be idempotent. */
    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
