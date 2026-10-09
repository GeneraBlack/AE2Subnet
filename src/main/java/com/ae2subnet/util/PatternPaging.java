package com.ae2subnet.util;

import com.ae2subnet.api.UnlockMode;
import net.minecraft.core.Direction;

/**
 * Pure paging / configuration-cycle rules shared by block entities, menus and unit tests.
 */
public final class PatternPaging {

    public static final int PAGE_SIZE = 16;
    public static final int MAX_PAGES = 5;
    public static final int TOTAL_SLOTS = PAGE_SIZE * MAX_PAGES;

    private PatternPaging() {
    }

    /** Pages unlocked by the given number of Capacity Cards (1 base page + 1 per card, max 5). */
    public static int maxPages(int capacityCards) {
        return Math.max(1, Math.min(MAX_PAGES, 1 + capacityCards));
    }

    public static int slotIndex(int page, int slotOnPage) {
        return page * PAGE_SIZE + slotOnPage;
    }

    public static int clampPage(int page, int maxPages) {
        return Math.max(0, Math.min(page, maxPages - 1));
    }

    public static Direction nextFacing(Direction current) {
        return Direction.from3DDataValue((current.get3DDataValue() + 1) % 6);
    }

    public static UnlockMode nextUnlockMode(UnlockMode current) {
        return current == UnlockMode.ON_OUTPUT_RETURN ? UnlockMode.ON_MACHINE_EMPTY : UnlockMode.ON_OUTPUT_RETURN;
    }
}
