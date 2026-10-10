package com.ae2subnet.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import com.ae2subnet.api.IMasterPatternProvider;
import com.ae2subnet.api.ISubnetWorker;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.api.SubnetDisplayStatus;
import com.ae2subnet.api.WorkerState;
import com.ae2subnet.init.ModBlockEntities;
import com.ae2subnet.init.ModItems;
import com.ae2subnet.service.SubnetMasters;
import com.ae2subnet.util.BatchLogic;
import com.ae2subnet.util.BatchTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Output block of the mass mode. It feeds every machine that touches it and spreads the crafts evenly over them.
 * Neighbouring Loadbalancer blocks connect through the AE2 grid on their own, so a wall of them acts as one structure
 * without cables or channels. Outputs come back through {@link CollectorBlockEntity collectors}.
 */
public class LoadbalancerBlockEntity extends AENetworkedBlockEntity implements ISubnetWorker, IActionHost {

    private WorkerState state = WorkerState.FREE;
    private final BatchTracker<AEKey> inFlight = new BatchTracker<>();
    private int nextSide = 0;
    private final IActionSource actionSource = new MachineSource(this::getActionableNode);

    public LoadbalancerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LOADBALANCER.get(), pos, blockState);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModItems.LOADBALANCER.get())
                .addService(ISubnetWorker.class, this)
                .setIdlePowerUsage(0.5);
    }

    @Override
    public void onReady() {
        super.onReady();
        refreshWorkerState();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterFromService();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterFromService();
        super.setRemoved();
    }

    private void unregisterFromService() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            var service = grid.getService(ISubnetWorkerService.class);
            if (service != null) {
                service.unregisterWorker(this);
            }
        }
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        data.putInt("inFlight", inFlight.size());
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        // The expected outputs are not saved: restored crafts complete on the first output that comes back
        inFlight.clear();
        inFlight.addWildcards(Math.max(0, data.getIntOr("inFlight", 0)));
        state = inFlight.size() >= capacity() ? WorkerState.OCCUPIED : WorkerState.FREE;
    }

    // ---- machines around this block ----

    private boolean isMachine(Direction side) {
        if (level == null) {
            return false;
        }
        BlockPos pos = getBlockPos().relative(side);
        Direction face = side.getOpposite();
        if (GridHelper.getExposedNode(level, pos, face) != null) {
            return false; // another AE block (cable, loadbalancer, collector ...) is never a machine
        }
        return level.getCapability(Capabilities.Item.BLOCK, pos, face) != null
                || level.getCapability(Capabilities.Fluid.BLOCK, pos, face) != null
                || level.getCapability(Capabilities.Item.BLOCK, pos, null) != null
                || level.getCapability(Capabilities.Fluid.BLOCK, pos, null) != null;
    }

    public List<Direction> machineSides() {
        List<Direction> sides = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            if (isMachine(d)) {
                sides.add(d);
            }
        }
        return sides;
    }

    /** Crafts this block may keep in flight: one full batch per machine it touches. */
    public int capacity() {
        return BatchLogic.MAX_BATCH * Math.max(1, machineSides().size());
    }

    public int getInFlightCrafts() {
        return inFlight.size();
    }

    @Nullable
    private PatternProviderTarget resolveTarget(Direction side, AEKey what, long amount) {
        BlockPos machinePos = getBlockPos().relative(side);
        Direction face = side.getOpposite();
        var sided = PatternProviderTarget.get(level, machinePos, null, face, actionSource);
        if (sided != null && sided.insert(what, amount, Actionable.SIMULATE) >= amount) {
            return sided;
        }
        var unsided = PatternProviderTarget.get(level, machinePos, null, null, actionSource);
        if (unsided != null && unsided.insert(what, amount, Actionable.SIMULATE) >= amount) {
            return unsided;
        }
        for (Direction d : Direction.values()) {
            var other = PatternProviderTarget.get(level, machinePos, null, d, actionSource);
            if (other != null && other.insert(what, amount, Actionable.SIMULATE) >= amount) {
                return other;
            }
        }
        return null;
    }

    private boolean acceptsAll(Direction side, KeyCounter[] inputHolder) {
        for (var inputList : inputHolder) {
            for (var input : inputList) {
                if (resolveTarget(side, input.getKey(), input.getLongValue()) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Index into {@code sides} of the next machine (starting at the round-robin pointer) that takes the whole craft. */
    private int findMachine(List<Direction> sides, KeyCounter[] inputHolder) {
        for (int n = 0; n < sides.size(); n++) {
            int idx = (nextSide + n) % sides.size();
            if (acceptsAll(sides.get(idx), inputHolder)) {
                return idx;
            }
        }
        return -1;
    }

    // ---- ISubnetWorker ----

    @Override
    public WorkerState getWorkerState() {
        return state;
    }

    @Override
    public boolean isMassWorker() {
        return true;
    }

    @Override
    public boolean canAcceptInputs(KeyCounter[] inputHolder) {
        if (level == null || state != WorkerState.FREE || !isValidWorker()) {
            return false;
        }
        var sides = machineSides();
        return !sides.isEmpty()
                && inFlight.size() < BatchLogic.MAX_BATCH * sides.size()
                && findMachine(sides, inputHolder) >= 0;
    }

    @Override
    public boolean pushInputs(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master) {
        if (level == null || !isValidWorker()) {
            return false;
        }
        var sides = machineSides();
        if (sides.isEmpty()) {
            return false;
        }
        int idx = findMachine(sides, inputHolder);
        if (idx < 0) {
            return false;
        }
        Direction side = sides.get(idx);
        nextSide = (idx + 1) % sides.size();

        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            var target = resolveTarget(side, what, amount);
            long inserted = target != null ? target.insert(what, amount, Actionable.MODULATE) : 0;
            if (inserted < amount && master != null) {
                master.acceptReturn(what, amount - inserted); // never lose what the machine did not take
            }
        });

        var primary = patternDetails.getPrimaryOutput();
        inFlight.add(primary != null ? primary.what() : null, primary != null ? primary.amount() : 0);
        refreshWorkerState();
        return true;
    }

    @Override
    public void markFree() {
        inFlight.clear();
        refreshWorkerState();
    }

    @Override
    public void markOccupied() {
        this.state = WorkerState.OCCUPIED;
        saveChanges();
        markForUpdate();
    }

    @Override
    public BlockPos getWorkerPos() {
        return getBlockPos();
    }

    @Override
    public boolean isValidWorker() {
        return !isRemoved() && hasLevel() && getMainNode().isActive();
    }

    @Override
    public boolean expectsOutput(AEKey key) {
        return inFlight.expects(key);
    }

    @Override
    public long creditOutput(AEKey key, long amount) {
        long booked = inFlight.creditExpected(key, amount);
        if (booked > 0) {
            refreshWorkerState();
        }
        return booked;
    }

    private void refreshWorkerState() {
        this.state = inFlight.size() >= capacity() ? WorkerState.OCCUPIED : WorkerState.FREE;
        saveChanges();
        markForUpdate();
    }

    public SubnetDisplayStatus getDisplayStatus() {
        if (level != null && level.isClientSide()) {
            if (getBlockState().hasProperty(com.ae2subnet.block.LoadbalancerBlock.STATUS)) {
                return getBlockState().getValue(com.ae2subnet.block.LoadbalancerBlock.STATUS);
            }
        }
        boolean hasPower = getMainNode().isReady()
                && (getMainNode().isOnline() || getMainNode().isPowered() || getMainNode().isActive());
        if (!hasPower) {
            return SubnetDisplayStatus.OFFLINE;
        }
        return inFlight.isEmpty() ? SubnetDisplayStatus.IDLE : SubnetDisplayStatus.BUSY;
    }

    @Override
    public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        markForUpdate();
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModItems.LOADBALANCER.get();
    }
}
