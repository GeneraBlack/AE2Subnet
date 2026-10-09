package com.ae2subnet.blockentity;

import com.ae2subnet.api.UnlockMode;
import com.ae2subnet.util.PatternPaging;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExpansionAndGuiLogicTest {

    public static int calculateMaxPages(int capacityCards) {
        return PatternPaging.maxPages(capacityCards);
    }

    public static int calculateActualSlotIndex(int page, int invSlot) {
        return PatternPaging.slotIndex(page, invSlot);
    }

    public static Direction getNextDirection(Direction current) {
        return PatternPaging.nextFacing(current);
    }

    public static UnlockMode getNextUnlockMode(UnlockMode current) {
        return PatternPaging.nextUnlockMode(current);
    }

    @Test
    void testPageClamping() {
        Assertions.assertEquals(0, PatternPaging.clampPage(-3, 5));
        Assertions.assertEquals(2, PatternPaging.clampPage(2, 5));
        Assertions.assertEquals(1, PatternPaging.clampPage(4, 2), "page must fall back when Capacity Cards are removed");
        Assertions.assertEquals(0, PatternPaging.clampPage(3, 1));
        Assertions.assertEquals(80, PatternPaging.TOTAL_SLOTS);
    }

    @Test
    void testCapacityCardPageScaling() {
        // 0 Cards: Base page (16 patterns)
        Assertions.assertEquals(1, calculateMaxPages(0));
        // 1 Card: 2 pages (32 patterns)
        Assertions.assertEquals(2, calculateMaxPages(1));
        // 2 Cards: 3 pages (48 patterns)
        Assertions.assertEquals(3, calculateMaxPages(2));
        // 3 Cards: 4 pages (64 patterns)
        Assertions.assertEquals(4, calculateMaxPages(3));
        // 4 Cards: 5 pages (80 patterns)
        Assertions.assertEquals(5, calculateMaxPages(4));

        // Bounds clamping
        Assertions.assertEquals(1, calculateMaxPages(-2));
        Assertions.assertEquals(5, calculateMaxPages(10));
    }

    @Test
    void testSlotIndexMappingAcrossPages() {
        // Page 0 (slots 0..15)
        Assertions.assertEquals(0, calculateActualSlotIndex(0, 0));
        Assertions.assertEquals(15, calculateActualSlotIndex(0, 15));

        // Page 1 (slots 16..31)
        Assertions.assertEquals(16, calculateActualSlotIndex(1, 0));
        Assertions.assertEquals(31, calculateActualSlotIndex(1, 15));

        // Page 2 (slots 32..47)
        Assertions.assertEquals(32, calculateActualSlotIndex(2, 0));
        Assertions.assertEquals(47, calculateActualSlotIndex(2, 15));

        // Page 4 (slots 64..79)
        Assertions.assertEquals(64, calculateActualSlotIndex(4, 0));
        Assertions.assertEquals(79, calculateActualSlotIndex(4, 15));
    }

    @Test
    void testDirectionCycling() {
        Direction dir = Direction.DOWN; // 0
        dir = getNextDirection(dir);
        Assertions.assertEquals(Direction.UP, dir); // 1
        dir = getNextDirection(dir);
        Assertions.assertEquals(Direction.NORTH, dir); // 2
        dir = getNextDirection(dir);
        Assertions.assertEquals(Direction.SOUTH, dir); // 3
        dir = getNextDirection(dir);
        Assertions.assertEquals(Direction.WEST, dir); // 4
        dir = getNextDirection(dir);
        Assertions.assertEquals(Direction.EAST, dir); // 5
        dir = getNextDirection(dir);
        Assertions.assertEquals(Direction.DOWN, dir); // loops back to 0
    }

    @Test
    void testUnlockModeCycling() {
        UnlockMode mode = UnlockMode.ON_OUTPUT_RETURN;
        mode = getNextUnlockMode(mode);
        Assertions.assertEquals(UnlockMode.ON_MACHINE_EMPTY, mode);
        mode = getNextUnlockMode(mode);
        Assertions.assertEquals(UnlockMode.ON_OUTPUT_RETURN, mode);
    }
}
