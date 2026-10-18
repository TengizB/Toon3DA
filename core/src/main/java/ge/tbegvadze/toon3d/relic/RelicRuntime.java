package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.door.DoorOpenedListener;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.hazard.RelicBlastTarget;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.progression.BuildStats;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;

import java.util.List;

/**
 * The per-run trigger runner (one class, owned by World and SimWorld, never mirrored code). It reads the run's
 * {@link BuildStats} LIVE through the player, so it holds no relic ids: every behaviour is a build kind
 * (FLOOR_ARRIVAL_*, DOOR_BLAST, DOOR_OPEN_HEAL). With no relic held every term is zero and nothing happens.
 */
public final class RelicRuntime implements DoorOpenedListener {

    private static final AmmoType[] AMMO_TYPES = AmmoType.values();

    private RelicFeedbackListener feedbackListener;

    private Level             level;
    private Player            player;
    private RelicBlastTarget  blastTarget;
    /** Door tiles that already fired their trigger this floor, indexed tileRow * levelWidth + tileColumn (R10). */
    private boolean[]         doorTriggered = new boolean[0];
    private int               levelWidth;

    /**
     * Per-floor reset, then the arrival heal / armour / ammo (R12: ammo once per AMMO TYPE carried, as a fraction
     * of that type's reserve cap). Call right after {@code player.resetFloorBuildState()}.
     */
    public void beginFloor(Level level, Player player, PlayerInventory inventory, RelicBlastTarget blastTarget) {
        this.level       = level;
        this.player      = player;
        this.blastTarget = blastTarget;
        this.levelWidth  = level.getWidth();
        this.doorTriggered = new boolean[levelWidth * level.getHeight()];

        BuildStats stats = player.getBuildStats();
        float healFraction = stats.floorArrivalHealFraction();
        if (healFraction > 0f) healPlayer(healFraction);
        float armorFraction = stats.floorArrivalArmorFraction();
        if (armorFraction > 0f) {
            int armorBefore = player.getArmor();
            player.applyArmor(GameMath.fractionOfMaximum(player.getMaxArmor(), armorFraction));
            report(player.getArmor() - armorBefore, HudConstants.RELIC_FEEDBACK_ARMOR_UNIT);
        }
        float ammoFraction = stats.floorArrivalAmmoFraction();
        if (ammoFraction > 0f && inventory != null) refundArrivalAmmo(inventory, ammoFraction);
    }

    /** R9/R10: the door blast and the door heal, once per door tile per floor. */
    @Override
    public void onDoorOpenedByPlayer(int tileColumn, int tileRow) {
        if (level == null || player == null) return;
        int triggerIndex = tileRow * levelWidth + tileColumn;
        if (triggerIndex < 0 || triggerIndex >= doorTriggered.length || doorTriggered[triggerIndex]) return;
        doorTriggered[triggerIndex] = true;

        BuildStats stats = player.getBuildStats();
        float blastFraction = stats.doorBlastFraction();
        if (blastFraction > 0f && blastTarget != null) {
            int playerColumn = (int) Math.floor(player.positionX / Constants.CELL_SIZE);
            int playerRow    = (int) Math.floor(player.positionY / Constants.CELL_SIZE);
            // The tile beyond the door, opposite the player; a wall there moves the centre onto the door tile.
            int centreColumn = tileColumn + Integer.signum(tileColumn - playerColumn);
            int centreRow    = tileRow    + Integer.signum(tileRow    - playerRow);
            if (Level.isWall(level.getCell(centreColumn, centreRow))) {
                centreColumn = tileColumn;
                centreRow    = tileRow;
            }
            blastTarget.detonateRelicBlast(centreColumn, centreRow, blastFraction,
                    blastFraction * BalanceConfig.RELIC_BLAST_RING_SHARE,
                    EffectConstants.RELIC_BLAST_VISUAL_SCALE_DOOR);
        }
        float healFraction = stats.doorOpenHealFraction();
        if (healFraction > 0f) healPlayer(healFraction);
    }

    /** World routes it to event text; null in the sim. */
    public void setFeedbackListener(RelicFeedbackListener feedbackListener) {
        this.feedbackListener = feedbackListener;
    }

    private void healPlayer(float fractionOfMaxHealth) {
        int healthBefore = player.getHealth();
        player.applyHealing(GameMath.fractionOfMaximum(player.getMaxHealth(), fractionOfMaxHealth));
        report(player.getHealth() - healthBefore, HudConstants.RELIC_FEEDBACK_HEALTH_UNIT);
    }

    private void refundArrivalAmmo(PlayerInventory inventory, float fractionOfCap) {
        List<Weapon> arsenal = inventory.getArsenal();
        boolean[] refunded = new boolean[AMMO_TYPES.length];
        for (int weaponIndex = 0; weaponIndex < arsenal.size(); weaponIndex++) {
            Weapon weapon = arsenal.get(weaponIndex);
            AmmoType ammoType = weapon.getAmmoType();
            if (ammoType == null || refunded[ammoType.ordinal()]) continue;
            refunded[ammoType.ordinal()] = true;
            int rounds = GameMath.fractionOfMaximum(ammoType.getReserveCap(), fractionOfCap);
            weapon.refundReserveRounds(rounds);
            report(rounds, HudConstants.RELIC_FEEDBACK_AMMO_UNIT + ammoType.getDisplayName().toUpperCase());
        }
    }

    private void report(int amount, String unit) {
        if (amount <= 0 || feedbackListener == null) return;
        feedbackListener.onRelicTriggered("+" + amount + unit + HudConstants.RELIC_FEEDBACK_SUFFIX, true);
    }
}
