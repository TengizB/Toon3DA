package ge.tbegvadze.toon3d.door;

/**
 * Cosmetic callback notified when a door STARTS to move (procedural-sound-effects order 4).
 *
 * <p>Fired from {@link DoorManager} at the two transitions a player hears: a door beginning to open
 * ({@code requestOpen}, from CLOSED or reversing out of CLOSING) and a door beginning to close
 * (the auto-close in {@code notifyPlayerSettled}, or an arena door slammed shut by
 * {@code lockArenaDoor}). Kept in the door package so the manager has no dependency on the audio
 * layer — the same rule {@code enemy/EnemyAttackListener} states. Implemented by {@code World}.
 */
public interface DoorStateListener {
    void onDoorOpening(int tileColumn, int tileRow);
    void onDoorClosing(int tileColumn, int tileRow);
}
