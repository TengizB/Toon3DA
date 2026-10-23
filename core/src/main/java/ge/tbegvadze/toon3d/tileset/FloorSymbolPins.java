package ge.tbegvadze.toon3d.tileset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MEANING IS PINNED (level-generation-overhaul R29): the palette-request helper every generated floor uses. It
 * adds the placed prop kits' symbol -> sprite demand to the allocation request (exactly as a SIGNATURE room's
 * {@link RoomSymbolDemand} is added), and always reserves the terminal and the explosive barrel: World treats
 * every 'T' as a story LOG terminal and every 'E' as a live barrel, so neither symbol may ever render as some
 * other object. No allocator algorithm changes. Headless — no LibGDX.
 */
public final class FloorSymbolPins {

    /** The story LOG terminal (StoryUiConstants.STORY_LOG_TERMINAL_SYMBOL) and its sprite. */
    public static final char   TERMINAL_SYMBOL    = 'T';
    public static final String TERMINAL_SPRITE_ID = "prop_terminal";
    /** The live explosive barrel and its sprite. */
    public static final char   BARREL_SYMBOL      = 'E';
    public static final String BARREL_SPRITE_ID   = "prop_explosive_barrel";

    private FloorSymbolPins() {}

    /** The pins every generated floor reserves, whatever its rooms ('T' and 'E'). */
    public static Map<Character, String> alwaysPinned() {
        Map<Character, String> pins = new LinkedHashMap<>();
        pins.put(TERMINAL_SYMBOL, TERMINAL_SPRITE_ID);
        pins.put(BARREL_SYMBOL, BARREL_SPRITE_ID);
        return Collections.unmodifiableMap(pins);
    }

    /** Adds the floor's kit demand (may be empty) and the always-pinned symbols to an allocation request. */
    public static SymbolAllocationRequest.Builder pin(SymbolAllocationRequest.Builder request,
                                                      Map<Character, String> kitDemand) {
        if (kitDemand != null && !kitDemand.isEmpty()) request.placedRoom(RoomSymbolDemand.of(kitDemand));
        request.placedRoom(RoomSymbolDemand.of(alwaysPinned()));
        return request;
    }
}
