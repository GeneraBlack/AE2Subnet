package com.ae2subnet.blockentity;

import com.ae2subnet.util.BatchLogic;
import com.ae2subnet.util.BatchTracker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BatchLogicTest {

    @Test
    void testEffectiveLimit() {
        Assertions.assertEquals(64, BatchLogic.effectiveLimit(0));
        Assertions.assertEquals(64, BatchLogic.effectiveLimit(-5));
        Assertions.assertEquals(8, BatchLogic.effectiveLimit(8));
        Assertions.assertEquals(64, BatchLogic.effectiveLimit(500));
    }

    @Test
    void testCapacity() {
        Assertions.assertTrue(BatchLogic.hasCapacity(0, 1));
        Assertions.assertFalse(BatchLogic.hasCapacity(1, 1));
        Assertions.assertTrue(BatchLogic.hasCapacity(63, 0));
        Assertions.assertFalse(BatchLogic.hasCapacity(64, 0));
    }

    @Test
    void testCycleVisitsAllStepsAndWraps() {
        int value = 0;
        int[] expected = {1, 2, 4, 8, 16, 32, 64, 0};
        for (int exp : expected) {
            value = BatchLogic.nextLimit(value);
            Assertions.assertEquals(exp, value);
        }
        Assertions.assertEquals(0, BatchLogic.nextLimit(1234));
    }

    @Test
    void testSanitizeAndLabel() {
        Assertions.assertEquals(0, BatchLogic.sanitize(3));
        Assertions.assertEquals(16, BatchLogic.sanitize(16));
        Assertions.assertEquals("Auto", BatchLogic.label(0));
        Assertions.assertEquals("32", BatchLogic.label(32));
    }

    @Test
    void testTrackerCompletesOnFullAmount() {
        var t = new BatchTracker<String>();
        t.add("ingot", 1);
        t.add("ingot", 1);
        Assertions.assertEquals(2, t.size());
        Assertions.assertEquals(1, t.returned("ingot", 1));
        Assertions.assertEquals(1, t.size());
        Assertions.assertEquals(1, t.returned("ingot", 1));
        Assertions.assertTrue(t.isEmpty());
    }

    @Test
    void testTrackerBatchedReturnCompletesSeveralCrafts() {
        var t = new BatchTracker<String>();
        for (int i = 0; i < 8; i++) {
            t.add("ingot", 1);
        }
        Assertions.assertEquals(8, t.returned("ingot", 8));
        Assertions.assertTrue(t.isEmpty());
    }

    @Test
    void testTrackerPartialReturnAccumulates() {
        var t = new BatchTracker<String>();
        t.add("plate", 4);
        Assertions.assertEquals(0, t.returned("plate", 3));
        Assertions.assertEquals(1, t.size());
        Assertions.assertEquals(1, t.returned("plate", 1));
        Assertions.assertTrue(t.isEmpty());
    }

    @Test
    void testTrackerWildcardCompletesOnAnyReturn() {
        var t = new BatchTracker<String>();
        t.addWildcards(2);
        Assertions.assertEquals(1, t.returned("anything", 5));
        Assertions.assertEquals(1, t.size());
    }

    @Test
    void testTrackerUnmatchedOutputCompletesOldest() {
        var t = new BatchTracker<String>();
        t.add("ingot", 1);
        t.add("dust", 1);
        Assertions.assertEquals(1, t.returned("slag", 1));
        Assertions.assertEquals(1, t.size());
        Assertions.assertTrue(t.expects("dust"));
        Assertions.assertFalse(t.expects("ingot"));
    }

    @Test
    void testTrackerIgnoresEmptyReturns() {
        var t = new BatchTracker<String>();
        Assertions.assertEquals(0, t.returned("x", 3));
        t.add("x", 1);
        Assertions.assertEquals(0, t.returned("x", 0));
        Assertions.assertEquals(1, t.size());
    }
}
