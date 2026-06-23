package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Manages a fixed-size array of weapon slots for the player's active loadout.
 *
 * Invariants:
 *   - Slot count is always WeaponConstants.WEAPON_SLOT_COUNT (currently 3; slot 2 is permanently locked).
 *   - activeSlotIndex always refers to a slot that is either filled, or the only
 *     candidate when all slots are empty (index 0 in that case).
 *   - No slot can hold two weapons; each weapon occupies exactly one slot.
 *
 * Slot lifecycle:
 *   tryEquip  → places a weapon in the lowest free slot; auto-selects if loadout was empty.
 *   selectSlot → changes the active slot index; no-op when the target slot is empty or out of range.
 *   removeSlot → clears a slot; if that was the active slot a new active is found automatically.
 */
public final class Loadout {

    private final Weapon[] slots;
    private int activeSlotIndex;

    public Loadout() {
        this.slots           = new Weapon[WeaponConstants.WEAPON_SLOT_COUNT];
        this.activeSlotIndex = 0;
    }

    /**
     * Places the weapon in the lowest free slot.
     * If the loadout was empty (no active weapon yet) the new slot is auto-selected.
     * Returns false when all slots are occupied.
     */
    public boolean tryEquip(Weapon weapon) {
        if (weapon == null) return false;
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slotIndex == WeaponConstants.WEAPON_GUN_SLOT_LOCKED_INDEX) continue;
            if (slots[slotIndex] == null) {
                boolean wasEmpty = (active() == null);
                slots[slotIndex] = weapon;
                if (wasEmpty) {
                    activeSlotIndex = slotIndex;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Changes the active slot to the specified index.
     * No-op when the index is out of range or the target slot is empty.
     */
    public void selectSlot(int index) {
        if (index < 0 || index >= slots.length) return;
        if (slots[index] == null) return;
        activeSlotIndex = index;
    }

    /**
     * Searches forward from (from + 1), wrapping around, for the next filled slot.
     * Returns the same index when no other filled slot exists (either alone or all empty).
     */
    public int nextFilledSlot(int from) {
        for (int offset = 1; offset < slots.length; offset++) {
            int candidateIndex = (from + offset) % slots.length;
            if (slots[candidateIndex] != null) {
                return candidateIndex;
            }
        }
        return from;
    }

    /**
     * Searches backward from (from - 1), wrapping around, for the previous filled slot.
     * Returns the same index when no other filled slot exists (either alone or all empty).
     */
    public int previousFilledSlot(int from) {
        for (int offset = 1; offset < slots.length; offset++) {
            int candidateIndex = ((from - offset) % slots.length + slots.length) % slots.length;
            if (slots[candidateIndex] != null) {
                return candidateIndex;
            }
        }
        return from;
    }

    /**
     * Returns the weapon in the active slot, or null when the active slot is empty
     * (which only occurs when the entire loadout is empty).
     */
    public Weapon active() {
        return slots[activeSlotIndex];
    }

    /**
     * Clears the given slot and returns the removed weapon.
     * If the removed slot was the active slot, the next filled slot is selected
     * (or the previous if no next exists). Returns null when the slot was already empty
     * or the index is out of range.
     */
    public Weapon removeSlot(int index) {
        if (index < 0 || index >= slots.length) return null;
        Weapon removed = slots[index];
        if (removed == null) return null;
        slots[index] = null;
        if (activeSlotIndex == index) {
            // Try to find any other filled slot to keep the player armed.
            int nextCandidate = nextFilledSlot(index);
            // nextFilledSlot returns `index` when no other slot is filled; in that case
            // the loadout is completely empty and activeSlotIndex stays at 0 (safe default).
            activeSlotIndex = (nextCandidate != index) ? nextCandidate : 0;
        }
        return removed;
    }

    /** Returns the weapon in the given slot, or null if the slot is empty or out of range. */
    public Weapon getSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.length) return null;
        return slots[slotIndex];
    }

    /**
     * Returns the index of the slot currently holding the exact given weapon reference,
     * or -1 when the weapon is not present in any slot. Identity comparison (==) is used
     * because the arsenal keeps a single instance per weapon type, so a matching reference
     * means "this weapon is already in the loadout".
     */
    public int slotIndexOf(Weapon weapon) {
        if (weapon == null) return -1;
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slots[slotIndex] == weapon) return slotIndex;
        }
        return -1;
    }

    public int     getActiveSlotIndex() { return activeSlotIndex; }
    public int     getSlotCount()       { return slots.length; }

    /**
     * True when the given slot is permanently locked and can never hold a weapon.
     * Locked slots are skipped by tryEquip() and never count as free space.
     * (Currently the future ranged slot at WEAPON_GUN_SLOT_LOCKED_INDEX.)
     */
    public boolean isSlotLocked(int slotIndex) {
        return slotIndex == WeaponConstants.WEAPON_GUN_SLOT_LOCKED_INDEX;
    }

    /**
     * True when every assignable (non-locked) slot is occupied.
     * Locked slots are ignored — they can never hold a weapon, so their permanent
     * emptiness must not make the loadout look like it still has free space.
     * This is what drives the pickup card to show the SWAP list instead of an
     * EQUIP button that would target a locked slot.
     */
    public boolean isFull() {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (isSlotLocked(slotIndex)) continue;
            if (slots[slotIndex] == null) return false;
        }
        return true;
    }
}
