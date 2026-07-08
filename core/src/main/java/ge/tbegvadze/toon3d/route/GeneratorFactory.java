package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.ILevelGenerator;
import ge.tbegvadze.toon3d.level.LevelGenConfig;

/**
 * Builds an {@link ILevelGenerator} for a given seed and (optional) config. Registered against a
 * {@link GeneratorId} in {@link GeneratorRegistry}, this closure is how a data-driven registry
 * replaces the old hard-coded {@code switch} in {@code World.pickGenerator()}.
 *
 * <p>Existing generators take their seed in the constructor; a factory simply closes over that.
 * The {@code config} may be {@code null} for generators that ignore it (they default internally).
 */
@FunctionalInterface
public interface GeneratorFactory {
    /**
     * @param seed   the deterministic seed for this floor
     * @param config optional generation config; may be {@code null}
     * @return a ready-to-use generator instance
     */
    ILevelGenerator create(long seed, LevelGenConfig config);
}
