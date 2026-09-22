package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * Every gameplay sound recipe, and the bindings from game concepts onto them
 * (procedural-sound-effects orders 1 and 2).
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

    /** Registers every sound and binding. Called once, from {@code GameAudio}'s constructor. */
    public static void bootstrap(SoundRegistry registry) {
        registerWeaponFire(registry);
        registerWeaponState(registry);
        registerPlayerState(registry);
        registerImpacts(registry);
        registerWorld(registry);
        bindWeapons(registry);
    }

    // =====================================================================================
    // Weapon fire
    // =====================================================================================
    private static void registerWeaponFire(SoundRegistry registry) {

        // The ANCHOR every other gun is mixed against: broadband body + crack + chest punch.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_SHOTGUN, SoundCategory.PLAYER_WEAPON)
                .volume(0.85f).cycleSpread(0.5f).loudness(80)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.24f)
                              .lowPass(1800f).envelope(0.001f, 7f).amplitude(0.90f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.05f)
                              .highPass(2500f).envelope(0.0008f, 14f).amplitude(0.55f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.18f)
                              .sweep(150f, 60f).envelope(0.001f, 5f).amplitude(0.50f).build())
                .build());

        // The two-stage onset IS this weapon's identity, so its pitch varies least in the catalog:
        // a big gun should sound the same every time.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_DOUBLE_BARREL, SoundCategory.PLAYER_WEAPON)
                .volume(1.00f).cycleSpread(0.15f).loudness(95)
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
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .lowPass(4000f).envelope(0.001f, 9f).amplitude(0.80f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.08f)
                              .sweep(300f, 150f).envelope(0.001f, 9f).amplitude(0.40f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_PISTOL, SoundCategory.PLAYER_WEAPON)
                .volume(0.55f).cycleSpread(0.7f).loudness(55)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.09f)
                              .lowPass(3000f).envelope(0.001f, 10f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.07f)
                              .sweep(400f, 180f).envelope(0.001f, 10f).amplitude(0.35f).build())
                .build());

        // Energy weapons read as PITCHED. Tone where the ballistics are noise is the whole contrast.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_PLASMA, SoundCategory.PLAYER_WEAPON)
                .volume(0.70f).cycleSpread(0.6f).loudness(60)
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
                .volume(0.70f).cycleSpread(0.6f).loudness(60)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.24f)
                              .frequency(600f).amplitudeModulation(60f, 1.0f)
                              .envelope(0.001f, 6f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.15f)
                              .highPass(4000f).amplitudeModulation(90f, 0.7f)
                              .envelope(0.001f, 8f).amplitude(0.30f)
                              .noiseSeed(SEED_CRACK).build())
                .build());

        // The four melee weapons must be separable by MASS.
        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_FIST, SoundCategory.PLAYER_WEAPON)
                .volume(0.45f).cycleSpread(0.9f).loudness(20)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.09f)
                              .sweep(160f, 90f).envelope(0.001f, 10f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.04f)
                              .lowPass(700f).envelope(0.001f, 10f).amplitude(0.35f)
                              .noiseSeed(SEED_BODY).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_KNIFE, SoundCategory.PLAYER_WEAPON)
                .volume(0.45f).cycleSpread(0.9f).loudness(15)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.08f)
                              .bandPass(3000f, 6000f).envelope(0.001f, 12f).amplitude(0.60f)
                              .noiseSeed(SEED_CRACK).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.02f)
                              .frequency(2400f).envelope(0.0008f, 14f).amplitude(0.30f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.FIRE_HAMMER, SoundCategory.PLAYER_WEAPON)
                .volume(0.75f).cycleSpread(0.5f).loudness(45)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.26f)
                              .sweep(110f, 55f).envelope(0.001f, 5f).amplitude(0.95f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .lowPass(500f).envelope(0.001f, 9f).amplitude(0.45f)
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
                .volume(0.50f).cycleSpread(0.2f).loudness(30)
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

        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_RELOAD_START, SoundCategory.PLAYER_WEAPON)
                .volume(0.45f).cycleSpread(0.4f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.05f)
                              .lowPass(2500f).envelope(0.0008f, 14f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.13f)
                              .bandPass(800f, 4000f).amplitudeModulation(25f, 0.5f)
                              .envelope(0.001f, 8f).amplitude(0.35f)
                              .delay(0.05f).noiseSeed(SEED_TAIL).build())
                .build());

        // The player must know the gun is hot again without reading the ammo digits — which still
        // say so, which is why this is legal.
        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_RELOAD_COMPLETE, SoundCategory.PLAYER_WEAPON)
                .volume(0.50f).cycleSpread(0.3f).loudness(25)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.03f)
                              .lowPass(3500f).envelope(0.0008f, 14f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.06f)
                              .frequency(900f).envelope(0.0008f, 14f).amplitude(0.35f).build())
                .build());

        // Descending pair = "swapped".
        registry.register(SoundDefinition
                .builder(GameSoundId.WEAPON_SWITCH, SoundCategory.INTERFACE)
                .volume(0.40f).cycleSpread(0.2f).loudness(15)
                .layers(
                    SoundLayer.builder(WaveformKind.SINE, 0.03f)
                              .frequency(1100f).envelope(0.0008f, 14f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.03f)
                              .frequency(780f).envelope(0.0008f, 14f).amplitude(0.70f)
                              .delay(0.05f).build())
                .build());
    }

    // =====================================================================================
    // Player state
    // =====================================================================================
    private static void registerPlayerState(SoundRegistry registry) {

        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_HURT_LIGHT, SoundCategory.PLAYER_STATE)
                .volume(0.65f).cycleSpread(0.7f)
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
                .volume(0.90f).cycleSpread(0.4f)
                .layers(
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.30f)
                              .sweep(100f, 45f).envelope(0.001f, 4f).amplitude(1.00f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.12f)
                              .lowPass(900f).envelope(0.001f, 6f).amplitude(0.60f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.25f)
                              .frequency(40f).envelope(0.02f, 3f).amplitude(0.50f).build())
                .build());

        // A perfect fifth reads as struck METAL. GUARDED and FLANKED already print different
        // coloured text; the ear learns the difference faster than the eye does.
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_GUARDED, SoundCategory.PLAYER_STATE)
                .volume(0.60f).cycleSpread(0.3f)
                .layers(
                    SoundLayer.builder(WaveformKind.SQUARE, 0.20f)
                              .frequency(900f).envelope(0.001f, 7f).amplitude(0.60f).build(),
                    SoundLayer.builder(WaveformKind.SQUARE, 0.20f)
                              .frequency(1350f).envelope(0.001f, 7f).amplitude(0.40f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.02f)
                              .highPass(3000f).envelope(0.0008f, 14f).amplitude(0.30f)
                              .noiseSeed(SEED_CRACK).build())
                .build());

        // The same clang, dulled and detuned to an INHARMONIC ratio, so it reads as "wrong".
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_FLANKED, SoundCategory.PLAYER_STATE)
                .volume(0.70f).cycleSpread(0.3f)
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

        // The longest sound in the game. Plays under the fade on the transition to DEAD; the DEATH
        // STROKE screen that follows is SILENT, and this must not put anything into that silence.
        registry.register(SoundDefinition
                .builder(GameSoundId.PLAYER_DEATH, SoundCategory.PLAYER_STATE)
                .volume(1.00f).cycleSpread(0f)
                .layers(
                    SoundLayer.builder(WaveformKind.EXPONENTIAL_CHIRP_SINE, 0.90f)
                              .sweep(300f, 40f).envelope(0.01f, 2.5f).amplitude(0.90f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.90f)
                              .sweptLowPass(3000f, 200f).envelope(0.05f, 2.5f).amplitude(0.55f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.80f)
                              .frequency(55f).envelope(0.02f, 1.5f).amplitude(0.40f).build())
                .build());

        // Cheapest, highest-value sound in the catalog: a refused tap must never be
        // indistinguishable from a dropped tap.
        registry.register(SoundDefinition
                .builder(GameSoundId.MOVE_BLOCKED, SoundCategory.PLAYER_STATE)
                .volume(0.35f).cycleSpread(0.5f).minimumRetriggerSeconds(0.10f)
                .layers(
                    SoundLayer.builder(WaveformKind.SINE, 0.07f)
                              .frequency(90f).envelope(0.001f, 12f).amplitude(0.70f).build(),
                    SoundLayer.builder(WaveformKind.NOISE, 0.03f)
                              .lowPass(400f).envelope(0.001f, 12f).amplitude(0.30f)
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
                .volume(0.55f).cycleSpread(0.8f)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.10f)
                              .lowPass(1500f).envelope(0.001f, 10f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.SINE, 0.05f)
                              .frequency(180f).envelope(0.001f, 10f).amplitude(0.35f).build())
                .build());

        registry.register(SoundDefinition
                .builder(GameSoundId.IMPACT_ENERGY, SoundCategory.ENEMY)
                .volume(0.55f).cycleSpread(0.8f)
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
                .volume(0.55f).cycleSpread(0.8f)
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
                .volume(0.55f).cycleSpread(0.6f)
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
    // The per-FAMILY voices are a later order; these generic three are the fallback every family
    // uses until then, and remain the fallback for a family with no binding afterwards.
    // =====================================================================================
    private static void registerWorld(SoundRegistry registry) {

        // Short and hard — an attack must cut through whatever else is happening.
        registry.register(SoundDefinition
                .builder(GameSoundId.ENEMY_ATTACK_MELEE, SoundCategory.ENEMY)
                .volume(0.55f).cycleSpread(0.6f).minimumRetriggerSeconds(0.08f).loudness(35)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.15f)
                              .bandPass(400f, 1800f).amplitudeModulation(18f, 0.8f)
                              .envelope(0.001f, 10f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.15f)
                              .sweep(90f, 130f).envelope(0.001f, 10f).amplitude(0.45f).build())
                .build());

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

        // The longest of the three, and the only one that falls.
        registry.register(SoundDefinition
                .builder(GameSoundId.ENEMY_DEATH, SoundCategory.ENEMY)
                .volume(0.60f).cycleSpread(0.6f).minimumRetriggerSeconds(0.10f).loudness(45)
                .layers(
                    SoundLayer.builder(WaveformKind.NOISE, 0.40f)
                              .lowPass(700f).envelope(0.002f, 3f).amplitude(0.70f)
                              .noiseSeed(SEED_BODY).build(),
                    SoundLayer.builder(WaveformKind.CHIRP_SINE, 0.40f)
                              .sweep(160f, 60f).envelope(0.002f, 3f).amplitude(0.55f).build())
                .build());

        // ANCHOR: the loudest event in the game. Reused verbatim for a launched grenade's
        // detonation — one recipe, two events.
        registry.register(SoundDefinition
                .builder(GameSoundId.BARREL_EXPLOSION, SoundCategory.ENVIRONMENT)
                .volume(1.00f).cycleSpread(0.3f)
                .priority(SoundConstants.GAME_SFX_PRIORITY_EXPLOSION).loudness(100)
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
