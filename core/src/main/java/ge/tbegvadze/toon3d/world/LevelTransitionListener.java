package ge.tbegvadze.toon3d.world;

/** Callback fired by PlayerController when the player steps onto a stairs-down tile. */
public interface LevelTransitionListener {
    void onDescentRequested();
}
