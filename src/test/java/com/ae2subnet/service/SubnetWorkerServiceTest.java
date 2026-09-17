package com.ae2subnet.service;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.ae2subnet.api.IMasterPatternProvider;
import com.ae2subnet.api.ISubnetWorker;
import com.ae2subnet.api.WorkerState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubnetWorkerServiceTest {

    private SubnetWorkerService service;

    static class MockWorker implements ISubnetWorker {
        private WorkerState state = WorkerState.FREE;
        private boolean valid = true;
        private final BlockPos pos;

        MockWorker(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public WorkerState getWorkerState() {
            return state;
        }

        @Override
        public boolean canAcceptInputs(KeyCounter[] inputHolder) {
            return state == WorkerState.FREE;
        }

        @Override
        public boolean pushInputs(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master) {
            markOccupied();
            return true;
        }

        @Override
        public void markFree() {
            this.state = WorkerState.FREE;
        }

        @Override
        public void markOccupied() {
            this.state = WorkerState.OCCUPIED;
        }

        @Override
        public BlockPos getWorkerPos() {
            return pos;
        }

        @Override
        public boolean isValidWorker() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }
    }

    @BeforeEach
    void setUp() {
        service = new SubnetWorkerService(null);
    }

    @Test
    void testWorkerRegistrationAndQueue() {
        MockWorker worker1 = new MockWorker(new BlockPos(0, 0, 0));
        MockWorker worker2 = new MockWorker(new BlockPos(1, 0, 0));

        service.registerWorker(worker1);
        service.registerWorker(worker2);

        Assertions.assertEquals(2, service.getTotalWorkerCount());
        Assertions.assertEquals(2, service.getIdleWorkerCount());
        Assertions.assertEquals(0, service.getBusyWorkerCount());

        // Dispatch 1 craft
        ISubnetWorker claimed = service.findAndClaimWorker(null, new KeyCounter[0], null);
        Assertions.assertNotNull(claimed);
        Assertions.assertEquals(worker1, claimed);
        Assertions.assertEquals(WorkerState.OCCUPIED, worker1.getWorkerState());

        Assertions.assertEquals(2, service.getTotalWorkerCount());
        Assertions.assertEquals(1, service.getIdleWorkerCount());
        Assertions.assertEquals(1, service.getBusyWorkerCount());

        // Dispatch second craft
        ISubnetWorker claimed2 = service.findAndClaimWorker(null, new KeyCounter[0], null);
        Assertions.assertNotNull(claimed2);
        Assertions.assertEquals(worker2, claimed2);
        Assertions.assertEquals(WorkerState.OCCUPIED, worker2.getWorkerState());

        Assertions.assertEquals(0, service.getIdleWorkerCount());
        Assertions.assertEquals(2, service.getBusyWorkerCount());

        // When both busy, dispatch fails immediately
        ISubnetWorker claimed3 = service.findAndClaimWorker(null, new KeyCounter[0], null);
        Assertions.assertNull(claimed3);

        // Mark worker1 free
        worker1.markFree();
        service.markFree(worker1);
        Assertions.assertEquals(1, service.getIdleWorkerCount());
        Assertions.assertEquals(1, service.getBusyWorkerCount());

        // Now worker1 can be claimed again
        ISubnetWorker claimedAgain = service.findAndClaimWorker(null, new KeyCounter[0], null);
        Assertions.assertEquals(worker1, claimedAgain);
    }

    @Test
    void testInvalidWorkerCleanup() {
        MockWorker worker = new MockWorker(new BlockPos(0, 0, 0));
        service.registerWorker(worker);
        Assertions.assertEquals(1, service.getIdleWorkerCount());

        worker.setValid(false); // e.g. broken or unloaded
        ISubnetWorker claimed = service.findAndClaimWorker(null, new KeyCounter[0], null);
        Assertions.assertNull(claimed);
        Assertions.assertEquals(0, service.getIdleWorkerCount());
    }
}