package com.ae2subnet.service;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.stacks.AEKey;
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
            // A subnet runs either in individual or in mass mode: workers of the other mode stay untouched
            if (master != null && worker.isMassWorker() != master.isMassMode()) {
                continue;
            }
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

    @Override
    public synchronized int getTotalWorkerCount(boolean mass) {
        int count = 0;
        for (var w : allWorkers) {
            if (!w.isRemoved() && w.isMassWorker() == mass) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized int getIdleWorkerCount(boolean mass) {
        int count = 0;
        for (var w : allWorkers) {
            if (w.isMassWorker() == mass && w.isValidWorker() && w.getWorkerState() == WorkerState.FREE) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized int getBusyWorkerCount(boolean mass) {
        int count = 0;
        for (var w : allWorkers) {
            if (w.isMassWorker() == mass && w.isValidWorker() && w.getWorkerState() == WorkerState.OCCUPIED) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized boolean hasRestoredCrafts() {
        for (var w : allWorkers) {
            if (w.hasRestoredCrafts()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean expectsOutput(AEKey key) {
        for (var w : allWorkers) {
            if (w.expectsOutput(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void creditOutput(AEKey key, long amount) {
        long left = amount;
        for (var w : new ArrayList<>(allWorkers)) {
            if (left <= 0) {
                return;
            }
            left -= Math.max(0, w.creditOutput(key, left));
        }
    }
}