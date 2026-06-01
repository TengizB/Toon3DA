package ge.tbegvadze.toon3d.entity;

/** Visual display state of any weapon; drives which HUD texture is shown each frame. */
public enum WeaponVisualState {
    /** Gun is ready to fire — normal idle texture. */
    NORMAL,
    /** Muzzle-flash frame immediately after a shot — fire texture shown briefly. */
    FIRING,
    /** Gun is cycling/reloading — reload texture held until reload ticks expire. */
    RELOADING
}
