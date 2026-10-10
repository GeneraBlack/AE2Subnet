package com.ae2subnet.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.ae2subnet.util.ScaleLogic;
import com.ae2subnet.util.ScaledPattern;
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
        var pending = data.list("pending", GenericStack.CODEC);
        int wildcards = 0;
        for (var p : inFlight.snapshot()) {
            if (p.key() == null) {
                wildcards++;
            } else {
                pending.add(new GenericStack(p.key(), p.remaining()));
            }
        }
        data.putInt("inFlight", wildcards); // crafts whose output is unknown (also what older versions saved)
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        inFlight.clear();
        for (var stack : data.listOrEmpty("pending", GenericStack.CODEC)) {
            inFlight.add(stack.what(), stack.amount());
        }
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
        // Whether the machines take the inputs is decided by pushInputs: a scaled push may be split over several of them
        var sides = machineSides();
        return !sides.isEmpty() && inFlight.size() < BatchLogic.MAX_BATCH * sides.size();
    }

    /** Inputs of a single craft of {@code pattern}: key and amount. */
    private static List<GenericStack> perCraftInputs(IPatternDetails pattern) {
        List<GenericStack> list = new ArrayList<>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length > 0) {
                list.add(new GenericStack(possible[0].what(), possible[0].amount() * input.getMultiplier()));
            }
        }
        return list;
    }

    /** How many crafts (up to {@code limit}) of {@code perCraft} the machine on {@code side} takes right now. */
    private int maxCrafts(Direction side, List<GenericStack> perCraft, int limit) {
        if (perCraft.isEmpty()) {
            return 0;
        }
        int lo = 0;
        int hi = limit;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            boolean fits = true;
            for (var in : perCraft) {
                if (resolveTarget(side, in.what(), in.amount() * mid) == null) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /**
     * How many crafts of {@code pattern} this block can hand to its machines in one push, summed over all of them. The
     * Master uses it to decide how far it may scale a pattern.
     */
    public int craftCapacity(IPatternDetails pattern) {
        if (level == null) {
            return 0;
        }
        var perCraft = perCraftInputs(pattern);
        long total = 0;
        for (var side : machineSides()) {
            total += maxCrafts(side, perCraft, ScaleLogic.MAX_FACTOR);
        }
        return (int) Math.min(total, ScaleLogic.MAX_FACTOR);
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
        int factor = ScaledPattern.factorOf(patternDetails);
        if (factor > 1) {
            if (!pushScaled(patternDetails, inputHolder, master, sides, factor)) {
                return false;
            }
        } else {
            int idx = findMachine(sides, inputHolder);
            if (idx < 0) {
                return false;
            }
            Direction side = sides.get(idx);
            nextSide = (idx + 1) % sides.size();
            pushTo(side, patternDetails, inputHolder, 1, master);
        }

        var primary = patternDetails.getPrimaryOutput();
        inFlight.add(primary != null ? primary.what() : null, primary != null ? primary.amount() : 0);
        refreshWorkerState();
        return true;
    }

    /** Splits {@code factor} crafts over the machines in whole crafts. Nothing is inserted if they do not all fit. */
    private boolean pushScaled(IPatternDetails scaled, KeyCounter[] inputHolder, IMasterPatternProvider master,
                               List<Direction> sides, int factor) {
        var perCraft = perCraftInputs(ScaledPattern.unwrap(scaled));
        int[] caps = new int[sides.size()];
        for (int i = 0; i < caps.length; i++) {
            caps[i] = maxCrafts(sides.get(i), perCraft, factor);
        }
        int[] shares = ScaleLogic.distribute(factor, caps);
        if (shares == null) {
            return false;
        }
        for (int i = 0; i < shares.length; i++) {
            if (shares[i] > 0) {
                pushTo(sides.get(i), scaled, inputHolder, factor, master, shares[i]);
            }
        }
        return true;
    }

    private void pushTo(Direction side, IPatternDetails pattern, KeyCounter[] inputHolder, int factor,
                        IMasterPatternProvider master) {
        pushTo(side, pattern, inputHolder, factor, master, factor);
    }

    /** Inserts {@code crafts} of {@code factor} crafts worth of {@code inputHolder} into the machine on {@code side}. */
    private void pushTo(Direction side, IPatternDetails pattern, KeyCounter[] inputHolder, int factor,
                        IMasterPatternProvider master, int crafts) {
        pattern.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            long part = factor == crafts ? amount : amount / factor * crafts;
            if (part <= 0) {
                return;
            }
            var target = resolveTarget(side, what, part);
            long inserted = target != null ? target.insert(what, part, Actionable.MODULATE) : 0;
            if (inserted < part && master != null) {
                master.acceptReturn(what, part - inserted); // never lose what the machine did not take
            }
        });
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
    public boolean hasRestoredCrafts() {
        return inFlight.hasWildcards();
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
