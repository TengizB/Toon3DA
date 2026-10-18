package ge.tbegvadze.toon3d.relic;

/**
 * THE CONTENT FILE: one {@code registry.register(...)} call per relic. Adding a relic = one call plus its
 * BalanceConfig SECTION 26 magnitudes; never a switch.
 */
public final class RelicCatalog {

    private RelicCatalog() { }

    public static void bootstrap(RelicRegistry registry) {
        // CP3 fills this with the 36 relics of the idea file's catalog.
    }
}
