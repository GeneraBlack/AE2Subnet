package com.ae2subnet.service;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.ae2subnet.api.IMasterPatternProvider;
import com.ae2subnet.api.ISubnetWorker;
import com.ae2subnet.api.WorkerState;
import com.ae2subnet.util.BatchTracker;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MassModeServiceTest {

    static class Worker implements ISubnetWorker {
        private final boolean mass;
        private final String expected;
        private final BatchTracker<String> tracker = new BatchTracker<>();
        int pushes = 0;

        Worker(boolean mass, String expected) {
            this.mass = mass;
            this.expected = expected;
        }

        @Override
        public WorkerState getWorkerState() {
            return WorkerState.FREE;
        }

        @Override
        public boolean canAcceptInputs(KeyCounter[] inputHolder) {
            return true;
        }

        @Override
        public boolean pushInputs(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master) {
            pushes++;
            tracker.add(expected, 1);
            return true;
        }

        @Override
        public void markFree() {
        }

        @Override
        public void markOccupied() {
        }

        @Override
        public BlockPos getWorkerPos() {
            return BlockPos.ZERO;
        }

        @Override
        public boolean isValidWorker() {
            return true;
        }

        @Override
        public boolean isMassWorker() {
            return mass;
        }
    }

    private static IMasterPatternProvider master(boolean mass) {
        return new IMasterPatternProvider() {
            @Override
            public void acceptReturn(GenericStack stack) {
            }

            @Override
            public boolean isMassMode() {
                return mass;
            }
        };
    }

    @Test
    void individualMasterIgnoresMassWorkers() {
        var service = new SubnetWorkerService(null);
        var single = new Worker(false, "a");
        var mass = new Worker(true, "a");
        service.registerWorker(mass);
        service.registerWorker(single);

        Assertions.assertSame(single, service.findAndClaimWorker(null, new KeyCounter[0], master(false)));
        Assertions.assertEquals(0, mass.pushes);
    }

    @Test
    void massMasterIgnoresIndividualWorkers() {
        var service = new SubnetWorkerService(null);
        var single = new Worker(false, "a");
        var mass = new Worker(true, "a");
        service.registerWorker(single);
        service.registerWorker(mass);

        Assertions.assertSame(mass, service.findAndClaimWorker(null, new KeyCounter[0], master(true)));
        Assertions.assertEquals(0, single.pushes);
    }

    @Test
    void noMatchingWorkerMeansNoClaim() {
        var service = new SubnetWorkerService(null);
        service.registerWorker(new Worker(false, "a"));
        Assertions.assertNull(service.findAndClaimWorker(null, new KeyCounter[0], master(true)));
    }

    @Test
    void countsAreSplitByMode() {
        var service = new SubnetWorkerService(null);
        service.registerWorker(new Worker(false, "a"));
        service.registerWorker(new Worker(false, "a"));
        service.registerWorker(new Worker(true, "a"));

        Assertions.assertEquals(2, service.getTotalWorkerCount(false));
        Assertions.assertEquals(1, service.getTotalWorkerCount(true));
        Assertions.assertEquals(2, service.getIdleWorkerCount(false));
        Assertions.assertEquals(1, service.getIdleWorkerCount(true));
        Assertions.assertEquals(0, service.getBusyWorkerCount(true));
        Assertions.assertEquals(3, service.getTotalWorkerCount());
    }

    @Test
    void trackerCreditIsStrictButRestoredCraftsAcceptAnything() {
        var t = new BatchTracker<String>();
        t.add("ingot", 2);
        Assertions.assertEquals(0, t.creditExpected("dust", 5));
        Assertions.assertEquals(1, t.size());
        Assertions.assertEquals(2, t.creditExpected("ingot", 5));
        Assertions.assertTrue(t.isEmpty());

        var restored = new BatchTracker<String>();
        restored.addWildcards(1);
        Assertions.assertEquals(3, restored.creditExpected("anything", 3));
        Assertions.assertTrue(restored.isEmpty());
    }
}
