package com.ae2subnet.api;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNodeService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.BlockPos;

public interface ISubnetWorker extends IGridNodeService {
    WorkerState getWorkerState();

    boolean canAcceptInputs(KeyCounter[] inputHolder);

    boolean pushInputs(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master);

    void markFree();

    void markOccupied();

    BlockPos getWorkerPos();

    boolean isValidWorker();

    default boolean isRemoved() {
        return false;
    }

    /**
     * True for workers of the mass mode (Loadbalancer outputs). The subnet only uses workers that match the mode of
     * its master.
     */
    default boolean isMassWorker() {
        return false;
    }

    /** True if this worker still waits for an output of this kind (used by collectors). */
    default boolean expectsOutput(AEKey key) {
        return false;
    }

    /**
     * Books an output that a collector took from a machine against this worker's pending crafts.
     *
     * @return the part of {@code amount} that belonged to this worker
     */
    default long creditOutput(AEKey key, long amount) {
        return 0;
    }
}