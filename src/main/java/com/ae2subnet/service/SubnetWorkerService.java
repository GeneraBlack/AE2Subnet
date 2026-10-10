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
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SubnetWorkerService implements ISubnetWorkerService, IGridServiceProvider {

    private final IGrid grid;
    private final Set<ISubnetWorker> allWorkers = ConcurrentHashMap.newKeySet();
    private final Queue<ISubnetWorker> idleQueue = new ConcurrentLinkedQueue<>();

    public SubnetWorkerService(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        if (gridNode.getOwner() instanceof ISubnetWorker worker) {
            registerWorker(worker);
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        if (gridNode.getOwner() instanceof ISubnetWorker worker) {
            unregisterWorker(worker);
        }
    }

    @Override
    public synchronized void registerWorker(ISubnetWorker worker) {
        if (allWorkers.add(worker)) {
            if (worker.getWorkerState() == WorkerState.FREE) {
                if (!idleQueue.contains(worker)) {
                    idleQueue.offer(worker);
                }
            }
        }
    }

    @Override
    public synchronized void unregisterWorker(ISubnetWorker worker) {
        allWorkers.remove(worker);
        idleQueue.remove(worker);
    }

    @Override
    public synchronized void markOccupied(ISubnetWorker worker) {
        idleQueue.remove(worker);
    }

    @Override
    public synchronized void markFree(ISubnetWorker worker) {
        if (allWorkers.contains(worker) && worker.isValidWorker()) {
            if (!idleQueue.contains(worker)) {
                idleQueue.offer(worker);
            }
        }
    }

    @Override
    @Nullable
    public synchronized ISubnetWorker findAndClaimWorker(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master) {
        Iterator<ISubnetWorker> it = idleQueue.iterator();
        while (it.hasNext()) {
            ISubnetWorker worker = it.next();
            if (!worker.isValidWorker() || !allWorkers.contains(worker)) {
                it.remove();
                continue;
            }

            if (worker.getWorkerState() != WorkerState.FREE) {
                it.remove();
                continue;
            }

            // A subnet runs either in individual or in mass mode: workers of the other mode stay untouched
            if (master != null && worker.isMassWorker() != master.isMassMode()) {
                continue;
            }

            if (worker.canAcceptInputs(inputHolder)) {
                it.remove();
                boolean success = worker.pushInputs(patternDetails, inputHolder, master);
                if (success) {
                    // A worker with spare capacity re-queues itself at the tail (round-robin)
                    return worker;
                } else if (worker.getWorkerState() == WorkerState.FREE && !idleQueue.contains(worker)) {
                    // Re-offer if push failed unexpectedly
                    idleQueue.offer(worker);
                }
            }
        }
        return null;
    }
    @Override
    public int getTotalWorkerCount() {
        return allWorkers.size();
    }

    @Override
    public int getIdleWorkerCount() {
        return idleQueue.size();
    }

    @Override
    public int getBusyWorkerCount() {
        return Math.max(0, allWorkers.size() - idleQueue.size());
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
