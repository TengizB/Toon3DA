package ge.tbegvadze.toon3d.sim;

import java.util.EnumSet;

import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.Keystone;

/**
 * The BUILD BIAS a scripted TACTICAL player plays with (balance-overhaul order 5, A6 / S-BUILD): the set of
 * {@link BuildTag}s it leans into at every level-up and keystone screen. {@link #NONE} is plain TACTICAL —
 * no preference beyond synergy and the most-ranked tag, exactly the CP2 behaviour.
 *
 * <p>A bias steers PERK and KEYSTONE picks only. It deliberately does not steer which weapon the policy keeps
 * or equips: TACTICAL's ground-weapon logic is a damage-class DIVERSITY rule (keep one answer per class),
 * and bolting a tag preference onto it would change S-SWITCH / S-HEAVY behaviour for every variant. The
 * bias therefore measures what the perks and keystones themselves are worth, on the same kits.
 *
 * <p>Each constant is data (its preferred tags, in priority order, and its policy-id suffix) — nothing
 * switches on a bias.
 */
public enum BuildBias {

    /** Plain TACTICAL: no tag preference. */
    NONE("", EnumSet.noneOf(BuildTag.class)),
    /** Shotgun play: CLOSE_QUARTERS perks and SCATTERGUN DOCTRINE. */
    CLOSE("-CLOSE", EnumSet.of(BuildTag.CLOSE_QUARTERS)),
    /** Rifle play: MARKSMAN perks and DEADEYE. */
    MARKSMAN("-MARKSMAN", EnumSet.of(BuildTag.MARKSMAN)),
    /** Fire and blast play: PYRO then DEMOLITION perks, PYROMANIAC or DEMOLITIONIST. */
    PYRO_DEMOLITION("-PYRO-DEMO", EnumSet.of(BuildTag.PYRO, BuildTag.DEMOLITION)),
    /** Guard play: BULWARK perks and IRON BULWARK. */
    BULWARK("-BULWARK", EnumSet.of(BuildTag.BULWARK));

    /** Appended to "TACTICAL" to form the variant's policy id (e.g. "TACTICAL-CLOSE"). */
    public final String policyIdSuffix;
    private final EnumSet<BuildTag> preferredTags;

    BuildBias(String policyIdSuffix, EnumSet<BuildTag> preferredTags) {
        this.policyIdSuffix = policyIdSuffix;
        this.preferredTags  = preferredTags;
    }

    /** True when this bias leans into the tag. Always false for {@link #NONE}. */
    public boolean prefers(BuildTag tag) {
        return tag != null && preferredTags.contains(tag);
    }

    /** True for every bias except {@link #NONE}. */
    public boolean isBiased() {
        return !preferredTags.isEmpty();
    }

    /**
     * The offered keystone whose tag this bias prefers (the earliest preferred tag in {@link BuildTag}
     * order wins when two are offered), or null when none is on the screen.
     */
    public Keystone preferredKeystone(Keystone[] offeredKeystones, int count) {
        for (BuildTag tag : preferredTags) {
            for (int index = 0; index < count; index++) {
                Keystone keystone = offeredKeystones[index];
                if (keystone != null && keystone.tag == tag) return keystone;
            }
        }
        return null;
    }
}
