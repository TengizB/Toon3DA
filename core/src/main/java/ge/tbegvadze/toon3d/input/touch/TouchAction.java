package ge.tbegvadze.toon3d.input.touch;

public enum TouchAction {
    FORWARD, BACK, ROTATE_LEFT, ROTATE_RIGHT, STRAFE_LEFT, STRAFE_RIGHT,
    FIRE, SKIP_TURN, RELOAD, SWITCH_WEAPON,
    HEAL, OPEN_INVENTORY, INSPECT_WEAPON, USE_MACHINE,
    GUARD,
    /** Opens the in-suit pause menu (Story UI order-8 Part C): resume, archive, settings, abandon. */
    PAUSE,
    NONE
}
