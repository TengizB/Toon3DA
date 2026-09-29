package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.enemy.EnemyFamily;
import ge.tbegvadze.toon3d.enemy.EnemyVoiceMoment;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * Every gameplay sound recipe, and the bindings from game concepts onto them
 * (procedural-sound-effects orders 1-6).
 *
 * <p><b>This is the content file.</b>  Adding a sound is ONE {@code register(...)} call here plus,
 * for a weapon, one {@code bindWeapon(...)} line — never an edit to the synthesiser, the mixer, the
 * registry or a switch statement anywhere.  Same discipline as {@code narrative/BarkCatalog} and
 * {@code route/RouteRegistries}.
 *
 * <p><b>The design rule behind the numbers.</b>  Ballistic weapons are NOISE (a broadband body plus
 * a bright crack plus a low punch); energy weapons are PITCHED (descending tones where the
 * ballistics are noise).  That contrast is what makes fourteen weapons distinguishable by ear on a
 * phone speaker.  Melee weapons separate by MASS: fist dull, knife bright, hammer heavy, chainsaw
 * continuous.
 *
 * <p><b>Order 6 — a Half-Life-inspired facility.</b>  Not a copy of any sound: the production
 * LANGUAGE of an industrial research facility heard through its own hard rooms.  Loud shots and
 * explosions ring off the concrete ({@code roomEcho}); doors, magazines, casings, grating and a
 * raised guard are struck METAL ({@code WaveformKind.METAL}); doors are pneumatic (hiss, slide,
 * seated clank); the suit speaks in clean bleeps (heal, armour charge, level-up, low-health warning,
 * the flatline under death); and guns are HANDLED (the shotgun pumps, the pistol drops a casing, a
 * melee swing whooshes before it lands).  No voice, ever.
 *
 * <p>Volumes are pre-mix and were derived against the shotgun as the anchor.  They are placeholders
 * for a real playtest pass on a device — the doc that specifies them says so, and tuning them is
 * expected to be a one-line edit here.
 */
public final class GameSoundCatalog {

    private GameSoundCatalog() {}

    // Distinct noise seeds per layer, so two noise layers in the same sound do not phase-cancel
    // into something thinner than either of them alone.
    private static final long SEED_BODY    = 0x1111L;
    private static final long SEED_CRACK   = 0x2222L;
    private static final long SEED_TAIL    = 0x3333L;
    private static final long SEED_DEBRIS  = 0x4444L;
    private static final long SEED_HISS    = 0x5555L;

    /** Registers every sound and binding. Called once, from {@code GameAudio}'s constructor. */
    public static void bootstrap(SoundRegistry registry) {
        registerWeaponFire(registry);
        registerWeaponState(registry);
        registerPlayerState(registry);
        registerImpacts(registry);
        registerWorld(registry);
        registerEnemyVoices(registry);
        registerFacility(registry);
        registerOrderSix(registry);
        bindWeapons(registry);
    }

    // =====================================================================================
    // Weapon fire
    // =====================================================================================
    private static void registerWeaponFire(SoundRegistry registry) {

        // The ANCHOR every other gun is mixed against: broadband body + crack + chest punch, then
        // the room answers and the PUMP racks the next shell — the gun is handled, not just fired.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_SHOTGUN, SoundCategory.PLAYER_WEAPON)
                .volume(0.85f).cycleSpread(0.5f).loudness(80)
                .roomEcho(0.060f, 0.40f, 1800f, 0.35f)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.24f)
                              .lowPass(1800f).envelope(0.001f, 7f).amplitude(0.90f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.05f)
                              .highPass(2500f).envelope(0.0008f, 14f).amplitude(0.55f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.18f)
                              .sweep(150f, 60f).envelope(0.001f, 5f).amplitude(0.50f).build(),
                    // pump back...
                    SoundLayer.builder(WaveformKind.METAL, 0.05f)
                              .frequency(420f).envelope(0.0008f, 10f).amplitude(0.30f)
                              .delay(0.38f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.04f)
                              .bandPass(800f, 3000f).envelope(0.0008f, 12f).amplitude(0.35f)
                              .delay(0.38f).noiseSeed(SEED_DEBRIS).build(),
                    // ...and forward
                    SoundLayer.builder(WaveformKind.METAL, 0.05f)
                              .frequency(520f).envelope(0.0008f, 10f).amplitude(0.35f)
                              .delay(0.50f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.04f)
                              .bandPass(800f, 3000f).envelope(0.0008f, 12f).amplitude(0.35f)
                              .delay(0.50f).noiseSeed(SEED_TAIL).build())
                .build());

        // The two-stage onset IS this weapon's identity, so its pitch varies least in the catalog:
        // a big gun should sound the same every time.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_DOUBLE_BARREL, SoundCategory.PLAYER_WEAPON)
                .volume(1.00f).cycleSpread(0.15f).loudness(95)
                .roomEcho(0.070f, 0.45f, 1500f, 0.40f)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.30f)
                              .lowPass(1400f).envelope(0.001f, 5f).amplitude(1.00f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.30f)
                              .lowPass(1400f).envelope(0.001f, 5f).amplitude(0.85f)
                              .delay(0.035f).noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.28f)
                              .sweep(130f, 45f).envelope(0.001f, 5f).amplitude(0.60f).build())
                .build());

        // Tiny and dry: the chaingun's character is the RHYTHM of repeats, not the single shot,
        // which is why it carries the widest pitch cycle and the shortest re-trigger window.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_CHAINGUN, SoundCategory.PLAYER_WEAPON)
                .volume(0.55f).cycleSpread(1.0f).minimumRetriggerSeconds(0.04f).loudness(65)
                .roomEcho(0.035f, 0.25f, 2000f, 0.22f)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.08f)
                              .bandPass(400f, 3500f).envelope(0.0008f, 11f).amplitude(0.75f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.06f)
                              .sweep(220f, 120f).envelope(0.0008f, 10f).amplitude(0.35f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_ASSAULT_RIFLE, SoundCategory.PLAYER_WEAPON)
                .volume(0.62f).cycleSpread(0.8f).minimumRetriggerSeconds(0.04f).loudness(70)
                .roomEcho(0.040f, 0.30f, 2200f, 0.25f)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .lowPass(4000f).envelope(0.001f, 9f).amplitude(0.80f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.08f)
                              .sweep(300f, 150f).envelope(0.001f, 9f).amplitude(0.40f).build())
                .build());

        // A short corridor slap, then the spent casing bouncing twice on the floor.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_PISTOL, SoundCategory.PLAYER_WEAPON)
                .volume(0.55f).cycleSpread(0.7f).loudness(55)
                .roomEcho(0.045f, 0.35f, 2200f, 0.30f)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.09f)
                              .lowPass(3000f).envelope(0.001f, 10f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.07f)
                              .sweep(400f, 180f).envelope(0.001f, 10f).amplitude(0.35f).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.08f)
                              .frequency(1150f).envelope(0.0008f, 8f).amplitude(0.12f)
                              .delay(0.22f).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.06f)
                              .frequency(1150f).envelope(0.0008f, 9f).amplitude(0.07f)
                              .delay(0.31f).build())
                .build());

        // Energy weapons read as PITCHED. Tone where the ballistics are noise is the whole contrast.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_PLASMA, SoundCategory.PLAYER_WEAPON)
                .volume(0.42f).cycleSpread(0.6f).loudness(60)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.20f)
                              .sweep(1400f, 380f).envelope(0.001f, 6f).amplitude(0.80f).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SQUARE, 0.20f)
                              .sweep(700f, 190f).envelope(0.001f, 6f).amplitude(0.22f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.03f)
                              .highPass(4000f).envelope(0.0008f, 14f).amplitude(0.30f)
                              .noiseSeed(SEED_CRACK).build())
                .build());

        // The only weapon with a long tail — it should sound like it went through the wall.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_RAILGUN, SoundCategory.PLAYER_WEAPON)
                .volume(0.95f).cycleSpread(0.3f).loudness(90)
                .roomEcho(0.090f, 0.45f, 3000f, 0.35f)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.10f)
                              .sweep(2200f, 300f).envelope(0.001f, 10f).amplitude(0.85f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.12f)
                              .highPass(1800f).envelope(0.001f, 8f).amplitude(0.60f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.35f)
                              .sweep(90f, 50f).envelope(0.001f, 3f).amplitude(0.45f).build())
                .build());

        // NO transient at all. The absence of a click is precisely what makes noise read as flame
        // rather than as a gunshot.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_INCINERATOR, SoundCategory.PLAYER_WEAPON)
                .volume(0.65f).cycleSpread(0.8f).loudness(50)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.45f)
                              .sweptLowPass(900f, 3000f).envelope(0.08f, 2f).amplitude(0.85f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.45f)
                              .frequency(70f).envelope(0.08f, 2f).amplitude(0.30f).build())
                .build());

        // Launch and detonation are two events: the launch stays small so the explosion is the payoff.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_ROCKET, SoundCategory.PLAYER_WEAPON)
                .volume(0.70f).cycleSpread(0.4f).loudness(75)
                .roomEcho(0.060f, 0.35f, 1500f, 0.25f)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.12f)
                              .sweep(320f, 90f).envelope(0.001f, 8f).amplitude(0.85f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.04f)
                              .lowPass(900f).envelope(0.001f, 12f).amplitude(0.45f)
                              .noiseSeed(SEED_BODY).build())
                .build());

        // Full-depth ring modulation zeroes the signal between chops — the ear hears "electricity".
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_ARC_CANNON, SoundCategory.PLAYER_WEAPON)
                .volume(0.50f).cycleSpread(0.6f).loudness(60)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.24f)
                              .frequency(600f).amplitudeModulation(60f, 1.0f)
                              .envelope(0.001f, 6f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.15f)
                              .highPass(4000f).amplitudeModulation(90f, 0.7f)
                              .envelope(0.001f, 8f).amplitude(0.30f)
                              .noiseSeed(SEED_CRACK).build())
                .build());

        // The four melee weapons must be separable by MASS. Fist, knife and hammer each SWING first
        // (order 6): a band of air whooshes past, and the contact lands 60 ms behind it. The chainsaw
        // needs no whoosh — it is already continuous.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_FIST, SoundCategory.PLAYER_WEAPON)
                .volume(0.45f).cycleSpread(0.9f).loudness(20)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.12f)
                              .highPass(300f).sweptLowPass(700f, 2500f)
                              .envelope(0.05f, 4f).amplitude(0.40f)
                              .noiseSeed(SEED_HISS).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.09f)
                              .sweep(160f, 90f).envelope(0.001f, 10f).amplitude(0.70f)
                              .delay(0.06f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.04f)
                              .lowPass(700f).envelope(0.001f, 10f).amplitude(0.35f)
                              .delay(0.06f)
                              .noiseSeed(SEED_BODY).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_KNIFE, SoundCategory.PLAYER_WEAPON)
                .volume(0.45f).cycleSpread(0.9f).loudness(15)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.12f)
                              .highPass(300f).sweptLowPass(1200f, 4500f)
                              .envelope(0.05f, 4f).amplitude(0.45f)
                              .noiseSeed(SEED_HISS).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.08f)
                              .bandPass(3000f, 6000f).envelope(0.001f, 12f).amplitude(0.60f)
                              .delay(0.06f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.02f)
                              .frequency(2400f).envelope(0.0008f, 14f).amplitude(0.30f)
                              .delay(0.06f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_HAMMER, SoundCategory.PLAYER_WEAPON)
                .volume(0.75f).cycleSpread(0.5f).loudness(45)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.12f)
                              .highPass(300f).sweptLowPass(500f, 1800f)
                              .envelope(0.05f, 4f).amplitude(0.50f)
                              .noiseSeed(SEED_HISS).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.26f)
                              .sweep(110f, 55f).envelope(0.001f, 5f).amplitude(0.95f)
                              .delay(0.06f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .lowPass(500f).envelope(0.001f, 9f).amplitude(0.45f)
                              .delay(0.06f)
                              .noiseSeed(SEED_BODY).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_CHAINSAW, SoundCategory.PLAYER_WEAPON)
                .volume(0.70f).cycleSpread(0.8f).loudness(70)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.30f)
                              .lowPass(2500f).amplitudeModulation(35f, 0.9f)
                              .envelope(0.06f, 2f).amplitude(0.85f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.30f)
                              .frequency(90f).amplitudeModulation(35f, 0.6f)
                              .envelope(0.06f, 2f).amplitude(0.35f).build())
                .build());

        // A weapon added later with no binding is never silent. That is the whole point of this row.
        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_FIRE_DEFAULT, SoundCategory.PLAYER_WEAPON)
                .volume(0.60f).cycleSpread(0.7f).loudness(60)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.14f)
                              .lowPass(3000f).envelope(0.001f, 9f).amplitude(0.75f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.12f)
                              .sweep(250f, 120f).envelope(0.001f, 9f).amplitude(0.35f).build())
                .build());
    }

    // =====================================================================================
    // Weapon state
    // =====================================================================================
    private static void registerWeaponState(SoundRegistry registry) {

        // This turn consumed an action and produced no shot. The rising pitch says "again, and it
        // goes off" — redundant with the CHARGING event text, which is what makes it legal.
        registry.register(SoundDefinition
                .builder(GameSoundId.RAILGUN_CHARGE, SoundCategory.PLAYER_WEAPON)
                .volume(0.28f).cycleSpread(0.2f).loudness(30)
                .layers(
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.45f)
                              .sweep(180f, 900f).envelope(0.30f, 2f).amplitude(0.75f).build(),
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.45f)
                              .sweep(360f, 1800f).envelope(0.30f, 2f).amplitude(0.20f).build())
                .build());

        // Two dry clicks is the universal "empty" idiom; pairs with the empty-fire teaching bark.
        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_DRY_FIRE, SoundCategory.PLAYER_WEAPON)
                .volume(0.45f).cycleSpread(0.4f).loudness(10)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.008f)
                              .highPass(2000f).envelope(0.0008f, 14f).amplitude(0.60f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.008f)
                              .highPass(2000f).envelope(0.0008f, 14f).amplitude(0.60f)
                              .delay(0.012f).noiseSeed(SEED_CRACK).build())
                .build());

        // Magazine released: a metal clack, then the slide rattle.
        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_RELOAD_START, SoundCategory.PLAYER_WEAPON)
                .volume(0.45f).cycleSpread(0.4f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.METAL, 0.06f)
                              .frequency(700f).envelope(0.0008f, 10f).amplitude(0.35f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.05f)
                              .lowPass(2500f).envelope(0.0008f, 14f).amplitude(0.60f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.13f)
                              .bandPass(800f, 4000f).amplitudeModulation(25f, 0.5f)
                              .envelope(0.001f, 8f).amplitude(0.35f)
                              .delay(0.05f).noiseSeed(SEED_TAIL).build())
                .build());

        // The player must know the gun is hot again without reading the ammo digits — which still
        // say so, which is why this is legal. Magazine seated (low clack), then the slide racked
        // (bright clack): two metal events read as "done" where one reads as "started".
        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_RELOAD_COMPLETE, SoundCategory.PLAYER_WEAPON)
                .volume(0.50f).cycleSpread(0.3f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.03f)
                              .lowPass(1800f).envelope(0.0008f, 14f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.07f)
                              .frequency(480f).envelope(0.0008f, 10f).amplitude(0.35f).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.08f)
                              .frequency(900f).envelope(0.0008f, 9f).amplitude(0.35f)
                              .delay(0.09f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.03f)
                              .highPass(2500f).envelope(0.0008f, 14f).amplitude(0.35f)
                              .delay(0.09f).noiseSeed(SEED_CRACK).build())
                .build());

        // The HUD's weapon-select tick: a descending pair of clean, slightly edged bleeps.
        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_SWITCH, SoundCategory.INTERFACE)
                .volume(0.40f).cycleSpread(0.2f).loudness(15)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(1100f).lowPass(2800f).envelope(0.0008f, 14f)
                              .amplitude(0.45f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(780f).lowPass(2800f).envelope(0.0008f, 14f)
                              .amplitude(0.45f).delay(0.05f).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.05f)
                              .frequency(600f).envelope(0.0008f, 10f).amplitude(0.20f)
                              .delay(0.05f).build())
                .build());
    }

    // =====================================================================================
    // Player state
    // =====================================================================================
    private static void registerPlayerState(SoundRegistry registry) {

        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_HURT_LIGHT, SoundCategory.PLAYER_STATE)
                .volume(0.65f).cycleSpread(0.7f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.16f)
                              .sweep(120f, 70f).envelope(0.001f, 8f).amplitude(0.80f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.06f)
                              .lowPass(1200f).envelope(0.001f, 9f).amplitude(0.45f)
                              .noiseSeed(SEED_BODY).build())
                .build());

        // Not a voice: there is no VO in this game and there must not be one.
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_HURT_HEAVY, SoundCategory.PLAYER_STATE)
                .volume(0.90f).cycleSpread(0.4f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.30f)
                              .sweep(100f, 45f).envelope(0.001f, 4f).amplitude(1.00f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.12f)
                              .lowPass(900f).envelope(0.001f, 6f).amplitude(0.60f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.25f)
                              .frequency(40f).envelope(0.02f, 3f).amplitude(0.50f).build())
                .build());

        // Struck METAL, ringing: the blow glanced off the guard. GUARDED and FLANKED already print
        // different coloured text; the ear learns the difference faster than the eye does.
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_GUARDED, SoundCategory.PLAYER_STATE)
                .volume(0.40f).cycleSpread(0.3f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.METAL, 0.30f)
                              .frequency(620f).envelope(0.001f, 5f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.20f)
                              .frequency(930f).envelope(0.001f, 7f).amplitude(0.35f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.02f)
                              .highPass(3000f).envelope(0.0008f, 14f).amplitude(0.30f)
                              .noiseSeed(SEED_CRACK).build())
                .build());

        // The same clang, dulled and detuned to an INHARMONIC ratio, so it reads as "wrong".
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_FLANKED, SoundCategory.PLAYER_STATE)
                .volume(0.48f).cycleSpread(0.3f).loudness(15)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.24f)
                              .frequency(900f).lowPass(1200f)
                              .envelope(0.001f, 7f).amplitude(0.60f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.24f)
                              .frequency(1269f).lowPass(1200f)
                              .envelope(0.001f, 7f).amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.16f)
                              .sweep(120f, 70f).envelope(0.001f, 8f).amplitude(0.50f).build())
                .build());

        // The longest sound in the game. Plays under the fade on the transition to DEAD, and ends on
        // the suit's FLATLINE — one steady tone, the last thing the body reports. The DEATH STROKE
        // screen that follows is SILENT, and this must not put anything into that silence: the tone
        // ends inside the 1.0 s cap, well before the stroke appears.
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_DEATH, SoundCategory.PLAYER_STATE)
                .volume(0.80f).cycleSpread(0f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.90f)
                              .sweep(300f, 40f).envelope(0.01f, 2.5f).amplitude(0.90f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.90f)
                              .sweptLowPass(3000f, 200f).envelope(0.05f, 2.5f).amplitude(0.55f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.80f)
                              .frequency(55f).envelope(0.02f, 1.5f).amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.40f)
                              .frequency(988f).envelope(0.01f, 1.2f).amplitude(0.22f)
                              .delay(0.58f).build())
                .build());

        // Cheapest, highest-value sound in the catalog: a refused tap must never be
        // indistinguishable from a dropped tap.
        registry.register(SoundDefinition
                .builder(GameSoundId.MOVE_BLOCKED, SoundCategory.PLAYER_STATE)
                .volume(0.55f).cycleSpread(0.5f).minimumRetriggerSeconds(0.10f).loudness(5)
                .layers(
                    SoundLayer.builder(WaveformKind.SINE, 0.07f)
                              .frequency(90f).envelope(0.001f, 12f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.05f)
                              .frequency(220f).envelope(0.001f, 12f).amplitude(0.45f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.03f)
                              .lowPass(1100f).envelope(0.001f, 12f).amplitude(0.45f)
                              .noiseSeed(SEED_BODY).build())
                .build());
    }

    // =====================================================================================
    // Impacts — the impact's character belongs to the WEAPON, not the victim. Four recipes
    // cover every weapon against every enemy, forever.
    // =====================================================================================
    private static void registerImpacts(SoundRegistry registry) {

        registry.register(SoundDefinition
                .builder(GameSoundId.IMPACT_BALLISTIC, SoundCategory.ENEMY)
                .volume(0.55f).cycleSpread(0.8f).loudness(30)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .lowPass(1500f).envelope(0.001f, 10f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.05f)
                              .frequency(180f).envelope(0.001f, 10f).amplitude(0.35f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.IMPACT_ENERGY, SoundCategory.ENEMY)
                .volume(0.55f).cycleSpread(0.8f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.14f)
                              .highPass(2500f).amplitudeModulation(120f, 0.6f)
                              .envelope(0.001f, 9f).amplitude(0.60f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.08f)
                              .sweep(900f, 400f).envelope(0.001f, 9f).amplitude(0.35f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.IMPACT_MELEE, SoundCategory.ENEMY)
                .volume(0.55f).cycleSpread(0.8f).loudness(20)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .lowPass(900f).envelope(0.001f, 9f).amplitude(0.75f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.10f)
                              .sweep(200f, 110f).envelope(0.001f, 9f).amplitude(0.40f).build())
                .build());

        // Bright, thin, obviously NOT flesh — the block/shard "clink".
        registry.register(SoundDefinition
                .builder(GameSoundId.IMPACT_BLOCKED, SoundCategory.ENEMY)
                .volume(0.35f).cycleSpread(0.6f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.12f)
                              .frequency(1400f).envelope(0.0008f, 12f).amplitude(0.55f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.12f)
                              .frequency(2100f).envelope(0.0008f, 12f).amplitude(0.35f).build())
                .build());
    }

    // =====================================================================================
    // Enemies and the world
    //
    // The ranged launch is shared by every family. (The order-2 generic melee attack and death were
    // cut in order 4: once every family had a voice they could never play, and the catalog is capped
    // at GAME_SFX_MAX_DISTINCT_SOUNDS.)
    // =====================================================================================
    private static void registerWorld(SoundRegistry registry) {

        registry.register(SoundDefinition
                .builder(GameSoundId.ENEMY_ATTACK_RANGED, SoundCategory.ENEMY)
                .volume(0.50f).cycleSpread(0.6f).minimumRetriggerSeconds(0.08f).loudness(40)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.12f)
                              .sweep(700f, 300f).envelope(0.001f, 9f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.04f)
                              .highPass(2500f).envelope(0.0008f, 13f).amplitude(0.35f)
                              .noiseSeed(SEED_CRACK).build())
                .build());

        // ANCHOR: the loudest event in the game. Reused verbatim for a launched grenade's
        // detonation — one recipe, two events. The longest echo in the catalog: the whole room
        // answers a blast (cut at the 1.0 s cap and faded to zero).
        registry.register(SoundDefinition
                .builder(GameSoundId.BARREL_EXPLOSION, SoundCategory.ENVIRONMENT)
                .volume(1.00f).cycleSpread(0.3f)
                .priority(SoundConstants.GAME_SFX_PRIORITY_EXPLOSION).loudness(100)
                .roomEcho(0.110f, 0.40f, 1200f, 0.35f)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.60f)
                              .sweptLowPass(4000f, 300f).envelope(0.004f, 4f).amplitude(1.00f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.50f)
                              .sweep(110f, 38f).envelope(0.004f, 3f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.04f)
                              .highPass(3000f).envelope(0.0008f, 14f).amplitude(0.50f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.25f)
                              .bandPass(1500f, 5000f).amplitudeModulation(30f, 0.9f)
                              .envelope(0.01f, 6f).amplitude(0.25f)
                              .delay(0.18f).noiseSeed(SEED_DEBRIS).build())
                .build());
    }

    // =====================================================================================
    // Enemy family voices (order 3) — one set per EnemyFamily, never per EnemyType.
    //
    // Each family is ONE base recipe: a function from a moment's SHAPE to its layers. The shape is
    // what the three moments share across every family — ALERT short and rising, ATTACK shortest
    // and hardest, DEATH longest and falling — and the recipe is what makes an insect sound like an
    // insect. A new family is one registerFamilyVoice(...) call plus its three GameSoundIds; a new
    // archetype in an existing family needs nothing at all.
    // =====================================================================================

    /** What one moment does to any family's base recipe. */
    private static final class VoiceShape {
        final float durationSeconds;
        final float attackSeconds;
        final float decayRate;
        /** Multipliers applied to the recipe's own sweep: rising for ALERT, falling for DEATH. */
        final float sweepFromScale;
        final float sweepToScale;
        final float volumeScale;
        final int   loudness;

        VoiceShape(float durationSeconds, float attackSeconds, float decayRate,
                   float sweepFromScale, float sweepToScale, float volumeScale, int loudness) {
            this.durationSeconds = durationSeconds;
            this.attackSeconds   = attackSeconds;
            this.decayRate       = decayRate;
            this.sweepFromScale  = sweepFromScale;
            this.sweepToScale    = sweepToScale;
            this.volumeScale     = volumeScale;
            this.loudness        = loudness;
        }

        float from(float hertz) { return hertz * sweepFromScale; }
        float to(float hertz)   { return hertz * sweepToScale; }
    }

    /** A family's whole voice: the layers it makes for a given moment shape. */
    private interface FamilyVoiceRecipe {
        SoundLayer[] layersFor(VoiceShape shape);
    }

    private static final VoiceShape[] VOICE_SHAPES = new VoiceShape[EnemyVoiceMoment.COUNT];
    static {
        VOICE_SHAPES[EnemyVoiceMoment.ALERT.ordinal()]  =
                new VoiceShape(0.20f, 0.004f, 6f,  0.80f, 1.20f, 0.90f, 40);
        VOICE_SHAPES[EnemyVoiceMoment.ATTACK.ordinal()] =
                new VoiceShape(0.15f, 0.001f, 10f, 1.00f, 0.90f, 1.00f, 35);
        VOICE_SHAPES[EnemyVoiceMoment.DEATH.ordinal()]  =
                new VoiceShape(0.40f, 0.003f, 3f,  1.00f, 0.45f, 1.05f, 45);
    }

    /**
     * The moments a family has its OWN voice for. WIND_UP (order 6) is deliberately absent: it is
     * one shared telegraph sound, bound through the per-moment fallback below.
     */
    private static final EnemyVoiceMoment[] FAMILY_MOMENTS = {
        EnemyVoiceMoment.ALERT, EnemyVoiceMoment.ATTACK, EnemyVoiceMoment.DEATH
    };

    /** Family voices are varied like any enemy sound, and a pile of them never machine-guns. */
    private static final float VOICE_CYCLE_SPREAD      = 0.6f;
    private static final float VOICE_RETRIGGER_SECONDS = 0.25f;

    private static void registerEnemyVoices(SoundRegistry registry) {

        // Wet and organic: a low-passed slop under a rising gurgle.
        registerFamilyVoice(registry, EnemyFamily.ABERRATION, 0.65f, 0.90f,
                GameSoundId.ENEMY_ABERRATION_ALERT, GameSoundId.ENEMY_ABERRATION_ATTACK,
                GameSoundId.ENEMY_ABERRATION_DEATH,
                shape -> new SoundLayer[] {
                    SoundLayer.builder(WaveformKind.NOISE, shape.durationSeconds)
                              .sweptLowPass(shape.from(1300f), shape.to(1300f))
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.65f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, shape.durationSeconds)
                              .sweep(shape.from(90f), shape.to(130f))
                              .amplitudeModulation(11f, 0.4f)
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.50f)
                              .build() });

        // Dry rasp: band-limited breath chopped at a slow flutter. No tone — nothing alive in it.
        registerFamilyVoice(registry, EnemyFamily.UNDEAD, 0.65f, 0.80f,
                GameSoundId.ENEMY_UNDEAD_ALERT, GameSoundId.ENEMY_UNDEAD_ATTACK,
                GameSoundId.ENEMY_UNDEAD_DEATH,
                shape -> new SoundLayer[] {
                    SoundLayer.builder(WaveformKind.NOISE, shape.durationSeconds)
                              .highPass(400f).sweptLowPass(shape.from(1800f), shape.to(1800f))
                              .amplitudeModulation(18f, 0.8f)
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.80f)
                              .noiseSeed(SEED_TAIL).build() });

        // High chitter: bright noise chopped fast enough to read as legs and mandibles.
        registerFamilyVoice(registry, EnemyFamily.INSECT, 0.65f, 1.35f,
                GameSoundId.ENEMY_INSECT_ALERT, GameSoundId.ENEMY_INSECT_ATTACK,
                GameSoundId.ENEMY_INSECT_DEATH,
                shape -> new SoundLayer[] {
                    SoundLayer.builder(WaveformKind.NOISE, shape.durationSeconds)
                              .highPass(2500f).sweptLowPass(shape.from(6000f), shape.to(6000f))
                              .amplitudeModulation(55f, 0.9f)
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.75f)
                              .noiseSeed(SEED_CRACK).build() });

        // Servo: a swept square and a relay tick. NO noise wash at all — the only family that is
        // entirely pitched, which is exactly what makes it read as not-biology.
        registerFamilyVoice(registry, EnemyFamily.MACHINE, 0.45f, 1.15f,
                GameSoundId.ENEMY_MACHINE_ALERT, GameSoundId.ENEMY_MACHINE_ATTACK,
                GameSoundId.ENEMY_MACHINE_DEATH,
                shape -> new SoundLayer[] {
                    SoundLayer.builder(WaveformKind.CHIRP_SQUARE, shape.durationSeconds)
                              .sweep(shape.from(220f), shape.to(260f)).lowPass(2400f)
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.45f)
                              .build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.02f)
                              .frequency(1600f).envelope(0.0008f, 14f).amplitude(0.35f)
                              .build() });

        // The loudest family: a sub-heavy moan under broadband noise, held longer than the rest.
        registerFamilyVoice(registry, EnemyFamily.DEMON, 0.80f, 0.70f,
                GameSoundId.ENEMY_DEMON_ALERT, GameSoundId.ENEMY_DEMON_ATTACK,
                GameSoundId.ENEMY_DEMON_DEATH,
                shape -> new SoundLayer[] {
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, shape.durationSeconds * 1.4f)
                              .sweep(shape.from(70f), shape.to(110f))
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.70f)
                              .build(),
                    SoundLayer.builder(WaveformKind.NOISE, shape.durationSeconds * 1.4f)
                              .sweptLowPass(shape.from(2500f), shape.to(2500f))
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.55f)
                              .noiseSeed(SEED_BODY).build() });

        // Mineral: a struck tone with an INHARMONIC partial at 2.76x (the struck-stone/bell trick)
        // over low-passed grit.
        registerFamilyVoice(registry, EnemyFamily.GOLEM, 0.55f, 1.00f,
                GameSoundId.ENEMY_GOLEM_ALERT, GameSoundId.ENEMY_GOLEM_ATTACK,
                GameSoundId.ENEMY_GOLEM_DEATH,
                shape -> new SoundLayer[] {
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, shape.durationSeconds)
                              .sweep(shape.from(180f), shape.to(180f))
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.50f)
                              .build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, shape.durationSeconds)
                              .sweep(shape.from(180f * 2.76f), shape.to(180f * 2.76f))
                              .envelope(shape.attackSeconds, shape.decayRate * 1.5f)
                              .amplitude(0.30f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, shape.durationSeconds)
                              .lowPass(1500f)
                              .envelope(shape.attackSeconds, shape.decayRate).amplitude(0.45f)
                              .noiseSeed(SEED_DEBRIS).build() });

        // A family with no registerFamilyVoice call borrows the ABERRATION voice — the baseline
        // bestiary — so it is heard, never silent, and costs no extra synthesised sound.
        registry.bindEnemyVoiceFallback(EnemyVoiceMoment.ALERT,  GameSoundId.ENEMY_ABERRATION_ALERT);
        registry.bindEnemyVoiceFallback(EnemyVoiceMoment.ATTACK, GameSoundId.ENEMY_ABERRATION_ATTACK);
        registry.bindEnemyVoiceFallback(EnemyVoiceMoment.DEATH,  GameSoundId.ENEMY_ABERRATION_DEATH);
        // Order 6: no family binds WIND_UP, so every family resolves here, to the one shared sound.
        registry.bindEnemyVoiceFallback(EnemyVoiceMoment.WIND_UP, GameSoundId.ENEMY_WIND_UP);
    }

    /**
     * Expands one family's base recipe into its three moment sounds, registers and binds them.
     *
     * @param launchPitch the pitch the SHARED ranged-launch sound plays at for this family (order 5)
     *                    — heavier bodies lower, chitin and servos higher, so who fired is audible
     *                    without a per-family launch recipe the 64-sound cap has no room for
     */
    private static void registerFamilyVoice(SoundRegistry registry, EnemyFamily family,
                                            float baseVolume, float launchPitch,
                                            GameSoundId alert, GameSoundId attack,
                                            GameSoundId death, FamilyVoiceRecipe recipe) {
        GameSoundId[] idsByMoment = new GameSoundId[EnemyVoiceMoment.COUNT];
        idsByMoment[EnemyVoiceMoment.ALERT.ordinal()]  = alert;
        idsByMoment[EnemyVoiceMoment.ATTACK.ordinal()] = attack;
        idsByMoment[EnemyVoiceMoment.DEATH.ordinal()]  = death;

        for (EnemyVoiceMoment moment : FAMILY_MOMENTS) {
            VoiceShape shape = VOICE_SHAPES[moment.ordinal()];
            registry.register(SoundDefinition
                    .builder(idsByMoment[moment.ordinal()], SoundCategory.ENEMY)
                    .volume(Math.min(1f, baseVolume * shape.volumeScale))
                    .cycleSpread(VOICE_CYCLE_SPREAD)
                    .minimumRetriggerSeconds(VOICE_RETRIGGER_SECONDS)
                    .loudness(shape.loudness)
                    .layers(recipe.layersFor(shape))
                    .build());
        }
        registry.bindEnemyFamily(family, alert, attack, death);
        registry.bindEnemyFamilyLaunchPitch(family, launchPitch);
    }

    // =====================================================================================
    // The facility (order 4): doors, stairs, pickups, progression.
    //
    // Pickups are short and never loud — they are receipts, not events. HEAL and LEVEL_UP are the
    // only two HARMONICALLY PLEASANT sounds in the catalog, on purpose: in a game this grim, "you
    // are less hurt" and "you got stronger" are the moments the ear is allowed to relax.
    // =====================================================================================
    private static void registerFacility(SoundRegistry registry) {

        // Pneumatic: the seal releases (hiss), the slab slides (the wash brightens as the gap
        // widens, over a floor hum), and it seats open with a metal clank.
        registry.register(SoundDefinition
                .builder(GameSoundId.DOOR_OPEN, SoundCategory.ENVIRONMENT)
                .volume(0.45f).cycleSpread(0.4f).minimumRetriggerSeconds(0.15f).loudness(30)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.25f)
                              .highPass(2000f).envelope(0.01f, 5f).amplitude(0.35f)
                              .noiseSeed(SEED_HISS).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.40f)
                              .sweptLowPass(600f, 2200f).envelope(0.05f, 3f).amplitude(0.55f)
                              .delay(0.05f).noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.40f)
                              .frequency(55f).envelope(0.05f, 3f).amplitude(0.35f)
                              .delay(0.05f).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.18f)
                              .frequency(240f).envelope(0.001f, 6f).amplitude(0.35f)
                              .delay(0.40f).build())
                .build());

        // The inverse: the slab slides shut, seats with a heavy metal clank, and the seal hisses.
        registry.register(SoundDefinition
                .builder(GameSoundId.DOOR_CLOSE, SoundCategory.ENVIRONMENT)
                .volume(0.45f).cycleSpread(0.4f).minimumRetriggerSeconds(0.15f).loudness(30)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.30f)
                              .sweptLowPass(2200f, 600f).envelope(0.01f, 4f).amplitude(0.60f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.06f)
                              .lowPass(500f).envelope(0.0008f, 12f).amplitude(0.70f)
                              .delay(0.28f).noiseSeed(SEED_DEBRIS).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.20f)
                              .frequency(200f).envelope(0.001f, 6f).amplitude(0.40f)
                              .delay(0.28f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.18f)
                              .highPass(2000f).envelope(0.02f, 5f).amplitude(0.20f)
                              .delay(0.34f).noiseSeed(SEED_HISS).build())
                .build());

        // The facility's DENY buzz: two short low buzzes, each two squares a few hertz apart so they
        // BEAT — flat, rough and unfriendly. (There is deliberately no UNLOCK sound: a keycard unlock
        // opens the door in the same instant on the same tile, and DOOR_OPEN already says so.) The
        // retrigger interval matters: a held FORWARD against a locked door asks every frame, and must
        // buzz at a readable rhythm, not a drone.
        registry.register(SoundDefinition
                .builder(GameSoundId.DOOR_LOCKED, SoundCategory.ENVIRONMENT)
                .volume(0.50f).cycleSpread(0f).minimumRetriggerSeconds(0.45f).loudness(20)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.08f)
                              .frequency(140f).lowPass(1800f).envelope(0.002f, 5f)
                              .amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.08f)
                              .frequency(147f).lowPass(1800f).envelope(0.002f, 5f)
                              .amplitude(0.30f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.08f)
                              .frequency(140f).lowPass(1800f).envelope(0.002f, 5f)
                              .amplitude(0.40f).delay(0.11f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.08f)
                              .frequency(147f).lowPass(1800f).envelope(0.002f, 5f)
                              .amplitude(0.30f).delay(0.11f).build())
                .build());

        // Three descending tones over a rumble: going down.
        registry.register(SoundDefinition
                .builder(GameSoundId.STAIRS_DESCEND, SoundCategory.ENVIRONMENT)
                .volume(0.55f).cycleSpread(0f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.SINE, 0.14f)
                              .frequency(660f).envelope(0.005f, 5f).amplitude(0.45f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.14f)
                              .frequency(520f).envelope(0.005f, 5f).amplitude(0.45f)
                              .delay(0.12f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.20f)
                              .frequency(390f).envelope(0.005f, 4f).amplitude(0.45f)
                              .delay(0.24f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.50f)
                              .frequency(60f).envelope(0.05f, 2.5f).amplitude(0.40f).build())
                .build());

        // A magazine / shell box: two metal clacks over a short rattle.
        registry.register(SoundDefinition
                .builder(GameSoundId.PICKUP_AMMO, SoundCategory.ENVIRONMENT)
                .volume(0.40f).cycleSpread(0.5f).loudness(10)
                .layers(
                    SoundLayer.builder(WaveformKind.METAL, 0.06f)
                              .frequency(900f).envelope(0.0008f, 10f).amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.03f)
                              .bandPass(1500f, 5000f).envelope(0.0008f, 12f).amplitude(0.35f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.06f)
                              .frequency(700f).envelope(0.0008f, 10f).amplitude(0.35f)
                              .delay(0.05f).build())
                .build());

        // A small sealed pack: a puff of hiss and one clean blip.
        registry.register(SoundDefinition
                .builder(GameSoundId.PICKUP_MEDICAL, SoundCategory.ENVIRONMENT)
                .volume(0.25f).cycleSpread(0.3f).loudness(10)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .highPass(2500f).envelope(0.01f, 5f).amplitude(0.25f)
                              .noiseSeed(SEED_HISS).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.12f)
                              .frequency(880f).envelope(0.02f, 5f).amplitude(0.60f).build())
                .build());

        // The suit CHARGING: a rising electrical whine over a plate click. Plated, not musical.
        registry.register(SoundDefinition
                .builder(GameSoundId.PICKUP_ARMOUR, SoundCategory.ENVIRONMENT)
                .volume(0.45f).cycleSpread(0.4f).loudness(15)
                .layers(
                    SoundLayer.builder(WaveformKind.METAL, 0.05f)
                              .frequency(500f).envelope(0.0008f, 10f).amplitude(0.30f).build(),
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.30f)
                              .sweep(300f, 1200f).envelope(0.03f, 3f).amplitude(0.35f).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SQUARE, 0.30f)
                              .sweep(150f, 600f).lowPass(1800f).envelope(0.03f, 3f)
                              .amplitude(0.12f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.PICKUP_KEYCARD, SoundCategory.ENVIRONMENT)
                .volume(0.55f).cycleSpread(0f).loudness(10)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(660f).lowPass(3000f).envelope(0.0008f, 10f)
                              .amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(880f).lowPass(3000f).envelope(0.0008f, 10f)
                              .amplitude(0.40f).delay(0.045f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.05f)
                              .frequency(1100f).lowPass(3000f).envelope(0.0008f, 8f)
                              .amplitude(0.40f).delay(0.09f).build())
                .build());

        // Heavy and good: a low clunk, a struck-metal body and a bright answering ring.
        registry.register(SoundDefinition
                .builder(GameSoundId.PICKUP_WEAPON, SoundCategory.ENVIRONMENT)
                .volume(0.60f).cycleSpread(0.2f).loudness(20)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.08f)
                              .lowPass(700f).envelope(0.001f, 9f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.25f)
                              .frequency(330f).envelope(0.001f, 5f).amplitude(0.45f).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.20f)
                              .frequency(660f).envelope(0.001f, 6f).amplitude(0.30f)
                              .delay(0.07f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.PICKUP_CREDIT, SoundCategory.ENVIRONMENT)
                .volume(0.35f).cycleSpread(0.6f).loudness(10)
                .layers(
                    SoundLayer.builder(WaveformKind.SINE, 0.08f)
                              .frequency(1500f).envelope(0.001f, 8f).amplitude(0.55f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.06f)
                              .frequency(3000f).envelope(0.001f, 10f).amplitude(0.25f).build())
                .build());

        // The injector: a pressurised hiss, then a rising chime (a fifth, then the octave). Pleasant
        // sound #1 — in a game this grim, "you are less hurt" is where the ear may relax.
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_HEAL, SoundCategory.PLAYER_STATE)
                .volume(0.45f).cycleSpread(0f).loudness(15)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.25f)
                              .highPass(2000f).envelope(0.02f, 4f).amplitude(0.30f)
                              .noiseSeed(SEED_HISS).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.15f)
                              .frequency(440f).envelope(0.05f, 3f).amplitude(0.55f)
                              .delay(0.06f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.20f)
                              .frequency(660f).envelope(0.05f, 3f).amplitude(0.50f)
                              .delay(0.18f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.20f)
                              .frequency(880f).envelope(0.03f, 3f).amplitude(0.35f)
                              .delay(0.30f).build())
                .build());

        // The suit's power-up chime: one edged bleep, then a major triad, sine plus a quiet square.
        // Pleasant sound #2, and the rarer one.
        registry.register(SoundDefinition
                .builder(GameSoundId.LEVEL_UP, SoundCategory.INTERFACE)
                .volume(0.60f).cycleSpread(0f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.04f)
                              .frequency(1320f).lowPass(3000f).envelope(0.0008f, 10f)
                              .amplitude(0.20f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.18f)
                              .frequency(523f).envelope(0.01f, 4f).amplitude(0.45f)
                              .delay(0.06f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.18f)
                              .frequency(659f).envelope(0.01f, 4f).amplitude(0.45f)
                              .delay(0.18f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.21f)
                              .frequency(784f).envelope(0.01f, 3f).amplitude(0.45f)
                              .delay(0.30f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.40f)
                              .frequency(523f).lowPass(2000f).envelope(0.01f, 4f)
                              .amplitude(0.10f).delay(0.06f).build())
                .build());

        // One tick. A chaingun spree must not become a xylophone: the hard re-trigger interval
        // is what guards it.
        registry.register(SoundDefinition
                .builder(GameSoundId.KILL_CONFIRM, SoundCategory.INTERFACE)
                .volume(0.18f).cycleSpread(0f).minimumRetriggerSeconds(0.12f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.SINE, 0.03f)
                              .frequency(1800f).envelope(0.0008f, 12f).amplitude(0.60f).build())
                .build());

        // A boot on steel grating: a dull thud with a faint metal ring under it. The quietest thing
        // in the mix, and the lowest priority — the first voice dropped when the screen is busy.
        // FLAGGED for deletion if a playtest finds it grating; deleting it is this one register() call.
        registry.register(SoundDefinition
                .builder(GameSoundId.FOOTSTEP, SoundCategory.PLAYER_STATE)
                .volume(0.12f).cycleSpread(1.0f).minimumRetriggerSeconds(0.10f).loudness(12)
                .priority(SoundConstants.GAME_SFX_PRIORITY_INTERFACE)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.05f)
                              .lowPass(1400f).envelope(0.0008f, 12f).amplitude(0.85f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.METAL, 0.09f)
                              .frequency(330f).envelope(0.0008f, 8f).amplitude(0.20f).build())
                .build());
    }

    // =====================================================================================
    // Order 6: the moments that were still silent.
    //
    // The suit speaks in clean edged bleeps (SQUARE through a low-pass, never raw); the facility
    // in hiss and metal. Every one is redundant with something already drawn (idea file R1).
    // =====================================================================================
    private static void registerOrderSix(SoundRegistry registry) {

        // The auto-doc: the charger's seal hisses, a charge tone climbs, two bleeps confirm.
        registry.register(SoundDefinition
                .builder(GameSoundId.HEAL_STATION, SoundCategory.PLAYER_STATE)
                .volume(0.50f).cycleSpread(0f).loudness(20)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.30f)
                              .highPass(1800f).envelope(0.02f, 3f).amplitude(0.30f)
                              .noiseSeed(SEED_HISS).build(),
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.45f)
                              .sweep(220f, 880f).envelope(0.10f, 2f).amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.06f)
                              .frequency(880f).lowPass(2500f).envelope(0.002f, 6f)
                              .amplitude(0.25f).delay(0.48f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.08f)
                              .frequency(1175f).lowPass(2500f).envelope(0.002f, 5f)
                              .amplitude(0.25f).delay(0.56f).build())
                .build());

        // The suit's warning: two urgent bleeps and a lower third. Delayed 0.22 s so it FOLLOWS the
        // hurt sound that caused it instead of masking it. The long re-trigger is a second guard on
        // top of the threshold's own re-arm rule.
        registry.register(SoundDefinition
                .builder(GameSoundId.LOW_HEALTH_WARNING, SoundCategory.PLAYER_STATE)
                .volume(0.45f).cycleSpread(0f).minimumRetriggerSeconds(1.0f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.07f)
                              .frequency(1400f).lowPass(3000f).envelope(0.002f, 5f)
                              .amplitude(0.35f).delay(0.22f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.07f)
                              .frequency(1400f).lowPass(3000f).envelope(0.002f, 5f)
                              .amplitude(0.35f).delay(0.34f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.12f)
                              .frequency(1050f).lowPass(3000f).envelope(0.002f, 4f)
                              .amplitude(0.35f).delay(0.46f).build())
                .build());

        // Something big is coming next turn: a servo grind and a whine SWELLING upward (a long
        // attack, so it reads as building, not as a hit). Shared by every family, pitched by size.
        registry.register(SoundDefinition
                .builder(GameSoundId.ENEMY_WIND_UP, SoundCategory.ENEMY)
                .volume(0.55f).cycleSpread(0.4f).minimumRetriggerSeconds(0.15f).loudness(35)
                .layers(
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.35f)
                              .sweep(160f, 640f).envelope(0.25f, 2f).amplitude(0.55f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.35f)
                              .bandPass(600f, 2500f).amplitudeModulation(24f, 0.7f)
                              .envelope(0.25f, 2f).amplitude(0.35f)
                              .noiseSeed(SEED_DEBRIS).build())
                .build());

        // A terminal waking up: a quick run of data bleeps at uneven pitches.
        registry.register(SoundDefinition
                .builder(GameSoundId.TERMINAL_ACCESS, SoundCategory.ENVIRONMENT)
                .volume(0.35f).cycleSpread(0.2f).minimumRetriggerSeconds(0.30f).loudness(10)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(1200f).lowPass(3000f).envelope(0.0008f, 10f)
                              .amplitude(0.30f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(1600f).lowPass(3000f).envelope(0.0008f, 10f)
                              .amplitude(0.30f).delay(0.05f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(900f).lowPass(3000f).envelope(0.0008f, 10f)
                              .amplitude(0.30f).delay(0.10f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(1400f).lowPass(3000f).envelope(0.0008f, 10f)
                              .amplitude(0.30f).delay(0.15f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.05f)
                              .frequency(2000f).lowPass(3000f).envelope(0.0008f, 8f)
                              .amplitude(0.25f).delay(0.20f).build())
                .build());

        // HUD blip UP: the inventory opening.
        registry.register(SoundDefinition
                .builder(GameSoundId.UI_MENU_OPEN, SoundCategory.INTERFACE)
                .volume(0.30f).cycleSpread(0f).minimumRetriggerSeconds(0.10f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(700f).lowPass(2500f).envelope(0.0008f, 10f)
                              .amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.04f)
                              .frequency(1050f).lowPass(2500f).envelope(0.0008f, 9f)
                              .amplitude(0.40f).delay(0.035f).build())
                .build());

        // HUD blip DOWN: the inventory closing — the same pair reversed.
        registry.register(SoundDefinition
                .builder(GameSoundId.UI_MENU_CLOSE, SoundCategory.INTERFACE)
                .volume(0.30f).cycleSpread(0f).minimumRetriggerSeconds(0.10f).loudness(0)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.03f)
                              .frequency(1050f).lowPass(2500f).envelope(0.0008f, 10f)
                              .amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.04f)
                              .frequency(700f).lowPass(2500f).envelope(0.0008f, 9f)
                              .amplitude(0.40f).delay(0.035f).build())
                .build());
    }

    // =====================================================================================
    // Bindings — the only place a weapon's name meets its sound
    // =====================================================================================
    private static void bindWeapons(SoundRegistry registry) {
        registry.bindWeapon(ItemType.WEAPON_PISTOL,         GameSoundId.FIRE_PISTOL);
        registry.bindWeapon(ItemType.WEAPON_SHOTGUN,        GameSoundId.FIRE_SHOTGUN);
        registry.bindWeapon(ItemType.WEAPON_DOUBLE_BARREL,  GameSoundId.FIRE_DOUBLE_BARREL);
        registry.bindWeapon(ItemType.WEAPON_CHAINGUN,       GameSoundId.FIRE_CHAINGUN);
        registry.bindWeapon(ItemType.WEAPON_ASSAULT_RIFLE,  GameSoundId.FIRE_ASSAULT_RIFLE);
        registry.bindWeapon(ItemType.WEAPON_PLASMA,         GameSoundId.FIRE_PLASMA);
        registry.bindWeapon(ItemType.WEAPON_RAILGUN,        GameSoundId.FIRE_RAILGUN);
        registry.bindWeapon(ItemType.WEAPON_INCINERATOR,    GameSoundId.FIRE_INCINERATOR);
        registry.bindWeapon(ItemType.WEAPON_ROCKET,         GameSoundId.FIRE_ROCKET);
        registry.bindWeapon(ItemType.WEAPON_ARC_CANNON,     GameSoundId.FIRE_ARC_CANNON);
        registry.bindWeapon(ItemType.WEAPON_FIST,           GameSoundId.FIRE_FIST);
        registry.bindWeapon(ItemType.WEAPON_KNIFE,          GameSoundId.FIRE_KNIFE);
        registry.bindWeapon(ItemType.WEAPON_HAMMER,         GameSoundId.FIRE_HAMMER);
        registry.bindWeapon(ItemType.WEAPON_CHAINSAW,       GameSoundId.FIRE_CHAINSAW);

        // Impacts group by DAMAGE CHARACTER, not by weapon: four recipes cover every weapon.
        registry.bindWeaponImpact(ItemType.WEAPON_PISTOL,        GameSoundId.IMPACT_BALLISTIC);
        registry.bindWeaponImpact(ItemType.WEAPON_SHOTGUN,       GameSoundId.IMPACT_BALLISTIC);
        registry.bindWeaponImpact(ItemType.WEAPON_DOUBLE_BARREL, GameSoundId.IMPACT_BALLISTIC);
        registry.bindWeaponImpact(ItemType.WEAPON_CHAINGUN,      GameSoundId.IMPACT_BALLISTIC);
        registry.bindWeaponImpact(ItemType.WEAPON_ASSAULT_RIFLE, GameSoundId.IMPACT_BALLISTIC);
        registry.bindWeaponImpact(ItemType.WEAPON_RAILGUN,       GameSoundId.IMPACT_BALLISTIC);
        registry.bindWeaponImpact(ItemType.WEAPON_ROCKET,        GameSoundId.IMPACT_BALLISTIC);
        registry.bindWeaponImpact(ItemType.WEAPON_PLASMA,        GameSoundId.IMPACT_ENERGY);
        registry.bindWeaponImpact(ItemType.WEAPON_ARC_CANNON,    GameSoundId.IMPACT_ENERGY);
        registry.bindWeaponImpact(ItemType.WEAPON_INCINERATOR,   GameSoundId.IMPACT_ENERGY);
        registry.bindWeaponImpact(ItemType.WEAPON_FIST,          GameSoundId.IMPACT_MELEE);
        registry.bindWeaponImpact(ItemType.WEAPON_KNIFE,         GameSoundId.IMPACT_MELEE);
        registry.bindWeaponImpact(ItemType.WEAPON_HAMMER,        GameSoundId.IMPACT_MELEE);
        registry.bindWeaponImpact(ItemType.WEAPON_CHAINSAW,      GameSoundId.IMPACT_MELEE);
    }
}
