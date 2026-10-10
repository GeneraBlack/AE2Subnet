package com.ae2subnet.api;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridService;
import appeng.api.stacks.AEKey;
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

    /** Workers of one mode only (mass = Loadbalancer outputs, otherwise Sub-Pattern Providers). */
    int getTotalWorkerCount(boolean mass);

    int getIdleWorkerCount(boolean mass);

    int getBusyWorkerCount(boolean mass);

    /** True if any worker still waits for this output. */
    boolean expectsOutput(AEKey key);

    /** Distributes an output a collector took from a machine over the workers that wait for it. */
    void creditOutput(AEKey key, long amount);
}