package com.ae2subnet.api;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNodeService;
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
}