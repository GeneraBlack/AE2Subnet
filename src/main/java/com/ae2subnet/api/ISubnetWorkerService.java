package com.ae2subnet.api;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridService;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

public interface ISubnetWorkerService extends IGridService {
    void registerWorker(ISubnetWorker worker);

    void unregisterWorker(ISubnetWorker worker);

    void markOccupied(ISubnetWorker worker);

    void markFree(ISubnetWorker worker);

    @Nullable
    ISubnetWorker findAndClaimWorker(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master);

    int getTotalWorkerCount();

    int getIdleWorkerCount();

    int getBusyWorkerCount();
}