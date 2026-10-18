package ge.tbegvadze.toon3d.relic;

import ge.tbegvadze.toon3d.door.DoorOpenedListener;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.hazard.RelicBlastTarget;
import ge.tbegvadze.toon3d.level.Level;

/**
 * The per-run trigger runner (one class, owned by World and SimWorld, never mirrored code). CP1 declares the
 * contract; CP2 fills the bodies once the seams exist.
 */
public final class RelicRuntime implements DoorOpenedListener {

    private RelicFeedbackListener feedbackListener;

    /** Per-floor reset, then the arrival heal / armour / ammo. CP2. */
    public void beginFloor(Level level, Player player, PlayerInventory inventory, RelicBlastTarget blastTarget) {
        // CP2
    }

    /** R9/R10: the door blast and the door heal, once per door tile per floor. CP2. */
    @Override
    public void onDoorOpenedByPlayer(int tileColumn, int tileRow) {
        // CP2
    }

    /** World routes it to event text; null in the sim. */
    public void setFeedbackListener(RelicFeedbackListener feedbackListener) {
        this.feedbackListener = feedbackListener;
    }
}
