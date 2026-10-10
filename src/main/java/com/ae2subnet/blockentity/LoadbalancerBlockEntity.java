package com.ae2subnet.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
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
public class LoadbalancerBlockEntity extends AENetworkedBlockEntity implements ISubnetWorker, IActionHost, IGridTickable {

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
                .addService(IGridTickable.class, this)
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
        var buf = data.childrenList("buffer");
        for (var p : buffer) {
            var child = buf.addChild();
            child.putInt("crafts", p.crafts);
            var inputs = child.list("inputs", GenericStack.CODEC);
            for (var in : p.perCraft) {
                inputs.add(in);
            }
        }
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        inFlight.clear();
        for (var stack : data.listOrEmpty("pending", GenericStack.CODEC)) {
            inFlight.add(stack.what(), stack.amount());
        }
        inFlight.addWildcards(Math.max(0, data.getIntOr("inFlight", 0)));
        buffer.clear();
        for (var child : data.childrenListOrEmpty("buffer")) {
            List<GenericStack> inputs = new ArrayList<>();
            for (var in : child.listOrEmpty("inputs", GenericStack.CODEC)) {
                inputs.add(in);
            }
            int crafts = child.getIntOr("crafts", 0);
            if (!inputs.isEmpty() && crafts > 0) {
                buffer.add(new Pending(inputs, crafts));
            }
        }
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
        // Only the touching face: the machine decides there which slots take the item. Other faces or the unsided
        // view can reach fuel and output slots (a log would be burned as furnace fuel).
        var sided = PatternProviderTarget.get(level, machinePos, null, face, actionSource);
        if (sided != null) {
            return sided.insert(what, amount, Actionable.SIMULATE) >= amount ? sided : null;
        }
        // Machines without any sided view of their own
        var unsided = PatternProviderTarget.get(level, machinePos, null, null, actionSource);
        if (unsided != null && unsided.insert(what, amount, Actionable.SIMULATE) >= amount) {
            return unsided;
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
        // The block takes every push into its buffer (up to a limit) and hands it to the machines as they have room
        return level != null && state == WorkerState.FREE && isValidWorker() && bufferedCrafts() < BUFFER_MAX_CRAFTS
                && !machineSides().isEmpty();
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
     * How many crafts of {@code pattern} this block can hand to its machines at once, summed over all of them. The
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

    // ---- buffer: what the CPU pushed and the machines have not taken yet ----

    /** Crafts worth of inputs that wait for room in a machine. */
    private static final class Pending {
        final List<GenericStack> perCraft;
        int crafts;

        Pending(List<GenericStack> perCraft, int crafts) {
            this.perCraft = perCraft;
            this.crafts = crafts;
        }
    }

    private static final int BUFFER_MAX_CRAFTS = 4096;
    private final List<Pending> buffer = new ArrayList<>();

    public int bufferedCrafts() {
        int sum = 0;
        for (var p : buffer) {
            sum += p.crafts;
        }
        return sum;
    }

    @Nullable
    private IMasterPatternProvider findMaster() {
        var grid = getMainNode().getGrid();
        return grid == null ? null : SubnetMasters.find(grid);
    }

    @Override
    public boolean pushInputs(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master) {
        if (level == null || !isValidWorker() || machineSides().isEmpty()) {
            return false;
        }
        int factor = ScaledPattern.factorOf(patternDetails);
        if (bufferedCrafts() + factor > BUFFER_MAX_CRAFTS) {
            return false;
        }

        List<GenericStack> perCraft = new ArrayList<>();
        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            long per = amount / factor;
            long rest = amount - per * factor;
            if (per > 0) {
                perCraft.add(new GenericStack(what, per));
            }
            if (rest > 0 && master != null) {
                master.acceptReturn(what, rest); // cannot be split into whole crafts
            }
        });
        if (perCraft.isEmpty()) {
            return false;
        }

        var last = buffer.isEmpty() ? null : buffer.get(buffer.size() - 1);
        if (last != null && last.perCraft.equals(perCraft)) {
            last.crafts += factor;
        } else {
            buffer.add(new Pending(perCraft, factor));
        }

        var primary = patternDetails.getPrimaryOutput();
        inFlight.add(primary != null ? primary.what() : null, primary != null ? primary.amount() : 0);
        refreshWorkerState();
        drain(); // hand over what fits right away
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        return true;
    }

    /** Hands buffered crafts to the machines, spread evenly in whole crafts. @return whether anything moved */
    private boolean drain() {
        if (buffer.isEmpty() || level == null || !isValidWorker()) {
            return false;
        }
        var sides = machineSides();
        if (sides.isEmpty()) {
            return false;
        }
        // Start at a different machine each time so the remainders do not always go to the same one
        java.util.Collections.rotate(sides, -(nextSide % sides.size()));
        nextSide++;

        boolean moved = false;
        for (var it = buffer.iterator(); it.hasNext(); ) {
            var pending = it.next();
            int[] caps = new int[sides.size()];
            long sum = 0;
            for (int i = 0; i < caps.length; i++) {
                caps[i] = maxCrafts(sides.get(i), pending.perCraft, pending.crafts);
                sum += caps[i];
            }
            int amount = (int) Math.min(sum, pending.crafts);
            if (amount <= 0) {
                continue;
            }
            int[] shares = ScaleLogic.distribute(amount, caps);
            if (shares == null) {
                continue;
            }
            for (int i = 0; i < shares.length; i++) {
                if (shares[i] > 0) {
                    insertCrafts(sides.get(i), pending.perCraft, shares[i]);
                }
            }
            pending.crafts -= amount;
            moved = true;
            if (pending.crafts <= 0) {
                it.remove();
            }
        }
        if (moved) {
            saveChanges();
        }
        return moved;
    }

    private void insertCrafts(Direction side, List<GenericStack> perCraft, int crafts) {
        for (var in : perCraft) {
            long part = in.amount() * crafts;
            var target = resolveTarget(side, in.what(), part);
            long inserted = target != null ? target.insert(in.what(), part, Actionable.MODULATE) : 0;
            if (inserted < part) {
                var master = findMaster();
                if (master != null) {
                    master.acceptReturn(in.what(), part - inserted); // never lose what the machine did not take
                }
            }
        }
    }

    /** Gives everything still in the buffer back to the network (block removed). */
    private void returnBuffer() {
        var master = findMaster();
        if (master == null) {
            return;
        }
        for (var p : buffer) {
            for (var in : p.perCraft) {
                master.acceptReturn(in.what(), in.amount() * p.crafts);
            }
        }
        buffer.clear();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            returnBuffer();
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level == null || level.isClientSide() || buffer.isEmpty()) {
            return TickRateModulation.SLOWER;
        }
        return drain() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
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
