package ge.tbegvadze.toon3d.level;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of {@link EncounterGroupTemplate}s (balance-overhaul order 2, E2). The shared instance is
 * populated on first access by {@link EncounterGroupTemplates#registerAll}, so the generators and the
 * headless balance audit read the same rows. Insertion-ordered (deterministic selection).
 */
public final class EncounterGroupTemplateRegistry {

    private static EncounterGroupTemplateRegistry shared;

    private final Map<String, EncounterGroupTemplate> byId = new LinkedHashMap<>();

    /** The shared registry, populated exactly once. */
    public static synchronized EncounterGroupTemplateRegistry shared() {
        if (shared == null) {
            EncounterGroupTemplateRegistry registry = new EncounterGroupTemplateRegistry();
            EncounterGroupTemplates.registerAll(registry);
            shared = registry;
        }
        return shared;
    }

    /**
     * Registers one group shape.
     *
     * @throws IllegalStateException if that id is already registered
     */
    public void register(EncounterGroupTemplate template) {
        if (byId.containsKey(template.id())) {
            throw new IllegalStateException("Encounter group template already registered: " + template.id());
        }
        byId.put(template.id(), template);
    }

    /** The template with this id, or {@code null}. */
    public EncounterGroupTemplate get(String id) {
        return byId.get(id);
    }

    /** Every registered template, in registration order. */
    public List<EncounterGroupTemplate> all() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(byId.values()));
    }
}
