package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The exchange catalog: {@link ExchangeTrigger} -&gt; the exchanges that may open on it, plus an
 * id index for {@link ExchangeSystem#present(String)} and for option chaining (Story UI order-4).
 *
 * <p>Registration is the ONLY edit point — adding an exchange is one {@link #register} call in
 * {@link ExchangeCatalog}, and neither the world nor the renderer knows the list exists (the same
 * discipline as {@link BarkRegistry} and {@code route/RouteRegistries}).
 *
 * <p>Rows are grouped by trigger at registration time, so runtime selection is an indexed walk over
 * one small list — no lookup allocation on the open path.  The per-trigger lists are returned
 * READ-ONLY; callers must never mutate them.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class ExchangeRegistry {

    private final Map<ExchangeTrigger, List<ExchangeDefinition>> byTrigger =
            new EnumMap<>(ExchangeTrigger.class);
    private final Map<String, ExchangeDefinition> byId = new HashMap<>();

    /** Registers one exchange.  Ids are unique — a duplicate id is a catalog bug, so it throws. */
    public ExchangeRegistry register(ExchangeDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        if (byId.containsKey(definition.getId())) {
            throw new IllegalStateException("duplicate exchange id: " + definition.getId());
        }
        byId.put(definition.getId(), definition);
        List<ExchangeDefinition> triggerRows = byTrigger.get(definition.getTrigger());
        if (triggerRows == null) {
            triggerRows = new ArrayList<>();
            byTrigger.put(definition.getTrigger(), triggerRows);
        }
        triggerRows.add(definition);
        return this;
    }

    /** All exchanges registered for a trigger, in registration order.  Never null; read-only. */
    public List<ExchangeDefinition> getForTrigger(ExchangeTrigger trigger) {
        List<ExchangeDefinition> triggerRows = byTrigger.get(trigger);
        return triggerRows != null ? triggerRows : Collections.<ExchangeDefinition>emptyList();
    }

    /** The exchange with this id, or null. */
    public ExchangeDefinition getById(String exchangeId) {
        return exchangeId != null ? byId.get(exchangeId) : null;
    }

    /** Every registered exchange (tests / audits). */
    public List<ExchangeDefinition> getAll() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /** True once at least one row is registered — lets {@link ExchangeCatalog#bootstrap} be idempotent. */
    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
