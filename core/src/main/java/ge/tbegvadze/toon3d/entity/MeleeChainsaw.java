package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Chainsaw — high sustained melee damage, no knockback (grinds the enemy in place).
 * The highest damage melee weapon; risk is staying adjacent to enemies that retaliate
 * each turn while the blade is running.
 */
public final class MeleeChainsaw extends MeleeWeapon {

    public MeleeChainsaw() {
        super("CHAINSAW", GameBalance.MELEE_CHAINSAW_DAMAGE);
    }
}
