package com.ae2subnet.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScaleLogicTest {

    @Test
    void offAndUnknownCapacityMeanNoScaling() {
        assertEquals(1, ScaleLogic.resolveFactor(ScaleLogic.MODE_OFF, 500));
        assertEquals(1, ScaleLogic.resolveFactor(ScaleLogic.MODE_AUTO, 0));
        assertEquals(1, ScaleLogic.resolveFactor(ScaleLogic.MODE_AUTO, 1));
    }

    @Test
    void autoUsesCapacityUpToMax() {
        assertEquals(37, ScaleLogic.resolveFactor(ScaleLogic.MODE_AUTO, 37));
        assertEquals(ScaleLogic.MAX_FACTOR, ScaleLogic.resolveFactor(ScaleLogic.MODE_AUTO, 100000));
    }

    @Test
    void fixedLevelIsClampedByCapacity() {
        assertEquals(16, ScaleLogic.resolveFactor(3, 500));
        assertEquals(10, ScaleLogic.resolveFactor(3, 10));
        assertEquals(1024, ScaleLogic.resolveFactor(6, 5000));
    }

    @Test
    void modesCycle() {
        int m = 0;
        for (int i = 0; i < ScaleLogic.MODE_COUNT; i++) {
            m = ScaleLogic.nextMode(m);
        }
        assertEquals(0, m);
    }

    @Test
    void distributeSpreadsEvenly() {
        assertArrayEquals(new int[]{4, 3, 3}, ScaleLogic.distribute(10, new int[]{64, 64, 64}));
    }

    @Test
    void distributeRespectsCaps() {
        assertArrayEquals(new int[]{2, 8}, ScaleLogic.distribute(10, new int[]{2, 64}));
    }

    @Test
    void distributeFailsWhenTooLarge() {
        assertNull(ScaleLogic.distribute(100, new int[]{10, 10}));
        assertNull(ScaleLogic.distribute(5, new int[0]));
    }
}
