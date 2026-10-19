package ge.tbegvadze.toon3d.enemy;

/**
 * What a HUNTED floor's clock reports (floor-twists R9/R12), so the enemy package never imports the HUD, the
 * narrative layer or audio — the {@link EnemyVoiceListener} idiom. World turns these into event text; the
 * balance simulator counts them. All three fire from inside {@link EnemyManager#takeTurn} or an enemy death.
 */
public interface HuntedPursuitListener {

    /** WAITING -> WARNED: {@code TWIST_HUNTED_WARNING_TURNS} turns before the arrival. */
    void onHunterWarning();

    /** WARNED -> ARRIVED: the hunter was printed at the arrival tile (its alert voice has already fired). */
    void onHunterArrived(int tileColumn, int tileRow);

    /** ARRIVED -> DONE: the hunter died, by any route; its bounty is already on the floor. */
    void onHunterKilled(int tileColumn, int tileRow);
}
