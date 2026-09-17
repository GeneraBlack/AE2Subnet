package com.ae2subnet.service;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.stacks.KeyCounter;
import com.ae2subnet.api.IMasterPatternProvider;
import com.ae2subnet.api.ISubnetWorker;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.api.WorkerState;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class SubnetWorkerService implements ISubnetWorkerService, IGridServiceProvider {

    private final IGrid grid;
    private final Set<ISubnetWorker> allWorkers = new LinkedHashSet<>();

    public SubnetWorkerService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public synchronized void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        var worker = gridNode.getService(ISubnetWorker.class);
        if (worker == null && gridNode.getOwner() instanceof ISubnetWorker w) {
            worker = w;
        }
        if (worker != null) {
            registerWorker(worker);
        }
    }

    @Override
    public synchronized void removeNode(IGridNode gridNode) {
        var worker = gridNode.getService(ISubnetWorker.class);
        if (worker == null && gridNode.getOwner() instanceof ISubnetWorker w) {
            worker = w;
        }
        if (worker != null) {
            unregisterWorker(worker);
        }
    }

    @Override
    public synchronized void registerWorker(ISubnetWorker worker) {
        allWorkers.add(worker);
    }

    @Override
    public synchronized void unregisterWorker(ISubnetWorker worker) {
        allWorkers.remove(worker);
    }

    @Override
    public synchronized void markOccupied(ISubnetWorker worker) {
        // State is tracked on worker directly
    }

    @Override
    public synchronized void markFree(ISubnetWorker worker) {
        // State is tracked on worker directly
    }

    @Override
    @Nullable
    public synchronized ISubnetWorker findAndClaimWorker(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master) {
        for (var worker : new ArrayList<>(allWorkers)) {
            if (worker.isValidWorker() && worker.getWorkerState() == WorkerState.FREE) {
                if (worker.canAcceptInputs(inputHolder)) {
                    boolean success = worker.pushInputs(patternDetails, inputHolder, master);
                    if (success) {
                        // Rotate to back of set for fair round-robin load distribution
                        allWorkers.remove(worker);
                        allWorkers.add(worker);
                        return worker;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public synchronized int getTotalWorkerCount() {
        int count = 0;
        for (var w : allWorkers) {
            if (!w.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized int getIdleWorkerCount() {
        int count = 0;
        for (var w : allWorkers) {
            if (w.isValidWorker() && w.getWorkerState() == WorkerState.FREE) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized int getBusyWorkerCount() {
        int count = 0;
        for (var w : allWorkers) {
            if (w.isValidWorker() && w.getWorkerState() == WorkerState.OCCUPIED) {
                count++;
            }
        }
        return count;
    }
}