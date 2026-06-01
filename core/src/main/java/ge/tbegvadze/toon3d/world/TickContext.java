package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.entity.Player;

/**
 * Immutable snapshot of the game state at the moment a tick fires.
 * One instance is owned by TickEventBus and mutated before each dispatch.
 * Subscribers must read fields during onTick() and must NOT retain this reference.
 */
public final class TickContext {

    int       playerTileColumn;
    int       playerTileRow;
    Player    player;
    long      tickIndex;
    TickCause cause;

    public int       getPlayerTileColumn() { return playerTileColumn; }
    public int       getPlayerTileRow()    { return playerTileRow; }
    public Player    getPlayer()           { return player; }
    public long      getTickIndex()        { return tickIndex; }
    public TickCause getCause()            { return cause; }
}
