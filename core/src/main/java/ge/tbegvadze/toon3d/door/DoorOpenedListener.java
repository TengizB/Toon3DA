package ge.tbegvadze.toon3d.door;

/**
 * Callback fired when the PLAYER opens a door (a CLOSED -> OPENING transition requested by the player), so a
 * relic can react (relics-run-changing-artifacts). A second listener slot on {@link DoorManager}, beside the
 * audio {@link DoorStateListener}; the door package never imports the relic package.
 */
public interface DoorOpenedListener {
    void onDoorOpenedByPlayer(int tileColumn, int tileRow);
}
