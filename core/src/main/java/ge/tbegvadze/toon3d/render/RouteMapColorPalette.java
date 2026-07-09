package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import ge.tbegvadze.toon3d.route.DangerTier;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * The route-map overlay's colour system (order-4). Resolves the headless string ids the route
 * package speaks — {@code NodeTypeDefinition.accentColorId()}, {@code RouteRegion.themeId()} — into
 * concrete LibGDX {@link Color}s, and maps a node's {@link DangerTier} to its risk-pip colour and
 * dot count. This is the ONLY place accent ids become colours, so the {@code …toon3d.route} package
 * stays free of any LibGDX import.
 *
 * <p>Every colour is pre-allocated in the constructor (project no-allocation-in-render rule). The
 * accessors return SHARED references — callers must read {@code r/g/b} and apply their own alpha
 * rather than mutate them.
 */
final class RouteMapColorPalette {

    // ---- Base palette (see the idea's COLOUR SYSTEM section) ----------------
    final Color holoCyan     = new Color(0.42f, 0.86f, 0.98f, 1f);
    final Color holoDim      = new Color(0.24f, 0.44f, 0.52f, 1f);
    final Color scrimNavy    = new Color(0.03f, 0.05f, 0.09f, 1f);
    final Color steel        = new Color(0.14f, 0.17f, 0.21f, 1f);
    final Color rivet        = new Color(0.34f, 0.40f, 0.46f, 1f);
    final Color hazardAmber  = new Color(0.98f, 0.72f, 0.16f, 1f);
    final Color dangerRed    = new Color(0.95f, 0.28f, 0.24f, 1f);
    final Color medGreen     = new Color(0.36f, 0.90f, 0.46f, 1f);
    final Color creditGold   = new Color(0.98f, 0.82f, 0.34f, 1f);
    final Color mysteryViolet = new Color(0.74f, 0.46f, 0.98f, 1f);
    final Color textBright   = new Color(0.92f, 0.97f, 1f, 1f);
    final Color textDim      = new Color(0.60f, 0.68f, 0.74f, 1f);

    // ---- Per-node accent colours (keyed by NodeTypeDefinition.accentColorId) -
    private final Color accentCombat  = new Color(0.42f, 0.78f, 0.98f, 1f);
    private final Color accentElite   = new Color(0.98f, 0.46f, 0.30f, 1f);
    private final Color accentCache   = new Color(0.40f, 0.92f, 0.60f, 1f);
    private final Color accentShop    = creditGold;
    private final Color accentRest    = new Color(0.44f, 0.94f, 0.72f, 1f);
    private final Color accentMystery = mysteryViolet;
    private final Color accentEvent   = new Color(0.56f, 0.90f, 0.94f, 1f);
    private final Color accentBoss    = new Color(0.98f, 0.24f, 0.22f, 1f);
    private final Color accentGate    = hazardAmber;
    private final Color accentLocked  = new Color(0.42f, 0.47f, 0.52f, 1f);

    // ---- Region theme tints (keyed by RouteRegion.themeId) ------------------
    private final Color tintOuterFacility = new Color(0.42f, 0.72f, 0.86f, 1f); // cool steel-cyan
    private final Color tintResearchWing  = new Color(0.72f, 0.92f, 0.80f, 1f); // clinical white-green
    private final Color tintReactorDepths = new Color(0.96f, 0.66f, 0.30f, 1f); // amber-hot
    private final Color tintTheBreach     = new Color(0.82f, 0.36f, 0.62f, 1f); // corrupted violet-red

    // ---- Risk-pip colours by danger tier (green -> amber -> red ramp) -------
    private final Color pipCalm     = medGreen;
    private final Color pipStandard = new Color(0.60f, 0.90f, 0.42f, 1f);
    private final Color pipDanger   = hazardAmber;
    private final Color pipGamble   = mysteryViolet;
    private final Color pipSetPiece = dangerRed;

    /** Resolves a node accent id to its colour; unknown ids fall back to the primary hologram cyan. */
    Color accentFor(String accentColorId) {
        if (accentColorId == null) return holoCyan;
        switch (accentColorId) {
            case "combat":  return accentCombat;
            case "elite":   return accentElite;
            case "cache":   return accentCache;
            case "shop":    return accentShop;
            case "rest":    return accentRest;
            case "mystery": return accentMystery;
            case "event":   return accentEvent;
            case "boss":    return accentBoss;
            case "gate":    return accentGate;
            default:        return holoCyan;
        }
    }

    /** The steel accent for LOCKED cards (desaturated, no glow). */
    Color lockedAccent() {
        return accentLocked;
    }

    /** Resolves a region theme id to the frame/scrim tint that makes each act FEEL different. */
    Color regionTint(String themeId) {
        if (themeId == null) return holoCyan;
        switch (themeId) {
            case RouteMapConstants.REGION_A_THEME: return tintOuterFacility;
            case RouteMapConstants.REGION_B_THEME: return tintResearchWing;
            case RouteMapConstants.REGION_C_THEME: return tintReactorDepths;
            case RouteMapConstants.REGION_D_THEME: return tintTheBreach;
            default:                               return holoCyan;
        }
    }

    /** Risk-pip colour for a danger tier. */
    Color pipColor(DangerTier tier) {
        switch (tier) {
            case CALM:      return pipCalm;
            case STANDARD:  return pipStandard;
            case DANGER:    return pipDanger;
            case GAMBLE:    return pipGamble;
            case SET_PIECE: return pipSetPiece;
            default:        return pipStandard;
        }
    }

    /** How many risk pips (1-4) a danger tier lights up — readable at a glance without text. */
    int pipCount(DangerTier tier) {
        switch (tier) {
            case CALM:      return 1;
            case STANDARD:  return 2;
            case DANGER:    return 3;
            case GAMBLE:    return 3;
            case SET_PIECE: return RouteMapConstants.RISK_PIP_MAX;
            default:        return 1;
        }
    }
}
