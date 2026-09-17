package com.ae2subnet.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import com.ae2subnet.api.IMasterPatternProvider;
import com.ae2subnet.api.ISubnetWorker;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.api.UnlockMode;
import com.ae2subnet.api.WorkerState;
import com.ae2subnet.block.SubPatternProviderBlock;
import com.ae2subnet.init.ModBlockEntities;
import com.ae2subnet.init.ModItems;
import appeng.api.stacks.GenericStack;
import appeng.util.InsertionOnlyResourceHandlerWithJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.blockentity.ServerTickingBlockEntity;

import java.lang.ref.WeakReference;
import java.util.EnumSet;

public class SubPatternProviderBlockEntity extends AENetworkedBlockEntity
        implements ISubnetWorker, IActionHost, IGridTickable, ServerTickingBlockEntity {

    private WorkerState state = WorkerState.FREE;
    private UnlockMode unlockMode = UnlockMode.ON_OUTPUT_RETURN;
    private boolean autoPull = true;
    private int busyHoldTicks = 0;

    private WeakReference<IMasterPatternProvider> activeMaster = null;
    private final IActionSource actionSource = new MachineSource(this::getActionableNode);

    public SubPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SUB_PATTERN_PROVIDER.get(), pos, blockState);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModItems.SUB_PATTERN_PROVIDER.get())
                .addService(ISubnetWorker.class, this)
                .addService(IGridTickable.class, this)
                .setIdlePowerUsage(0.5);
    }

    @Override
    public java.util.Set<Direction> getGridConnectableSides(appeng.api.orientation.BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(getMachineFacing()));
    }

    public Direction getMachineFacing() {
        BlockState state = getBlockState();
        if (state.hasProperty(SubPatternProviderBlock.MACHINE_FACING)) {
            return state.getValue(SubPatternProviderBlock.MACHINE_FACING);
        }
        return Direction.NORTH;
    }

    public BlockPos getMachinePos() {
        return getBlockPos().relative(getMachineFacing());
    }

    public void onFacingChanged() {
        onGridConnectableSidesChanged();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        if (dir == getMachineFacing()) {
            return null; // Machine face does not connect to AE cables
        }
        return super.getGridNode(dir);
    }

    @Override
    public void onReady() {
        onGridConnectableSidesChanged();
        super.onReady();
        markFree();
        markForUpdate();
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
        data.putString("workerState", state.name());
        data.putString("unlockMode", unlockMode.name());
        data.putBoolean("autoPull", autoPull);
        data.putInt("busyHoldTicks", busyHoldTicks);
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        data.getString("workerState").ifPresent(val -> {
            try {
                this.state = WorkerState.valueOf(val);
            } catch (Exception ignored) {
                this.state = WorkerState.FREE;
            }
        });
        data.getString("unlockMode").ifPresent(val -> {
            try {
                this.unlockMode = UnlockMode.valueOf(val);
            } catch (Exception ignored) {
                this.unlockMode = UnlockMode.ON_OUTPUT_RETURN;
            }
        });
        this.autoPull = data.getBooleanOr("autoPull", false);
        this.busyHoldTicks = data.getIntOr("busyHoldTicks", 0);
    }

    public com.ae2subnet.api.SubnetDisplayStatus getDisplayStatus() {
        if (level != null && level.isClientSide()) {
            if (getBlockState().hasProperty(SubPatternProviderBlock.STATUS)) {
                return getBlockState().getValue(SubPatternProviderBlock.STATUS);
            }
        }
        boolean hasPower = getMainNode().isReady() && (getMainNode().isOnline() || getMainNode().isPowered() || getMainNode().isActive());
        if (!hasPower) {
            return com.ae2subnet.api.SubnetDisplayStatus.OFFLINE;
        }
        return (state == WorkerState.OCCUPIED || busyHoldTicks > 0)
                ? com.ae2subnet.api.SubnetDisplayStatus.BUSY
                : com.ae2subnet.api.SubnetDisplayStatus.IDLE;
    }

    @Override
    public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State state) {
        super.onMainNodeStateChanged(state);
        markForUpdate();
    }
    public WorkerState getWorkerState() {
        return state;
    }

    public UnlockMode getUnlockMode() {
        return unlockMode;
    }

    public void setUnlockMode(UnlockMode mode) {
        this.unlockMode = mode;
        saveChanges();
    }

    public boolean isAutoPull() {
        return autoPull;
    }

    public void setAutoPull(boolean autoPull) {
        this.autoPull = autoPull;
        saveChanges();
    }

    @Override
    public BlockPos getWorkerPos() {
        return getBlockPos();
    }

    @Override
    public boolean isValidWorker() {
        return !isRemoved() && hasLevel() && getMainNode().isActive();
    }

    private PatternProviderTarget getTargetForInsert(appeng.api.stacks.AEKey what, long amount, Actionable mode) {
        if (level == null) return null;
        var sided = PatternProviderTarget.get(level, getMachinePos(), null, getMachineFacing().getOpposite(), actionSource);
        if (sided != null && sided.insert(what, amount, Actionable.SIMULATE) > 0) {
            return sided;
        }
        var unsided = PatternProviderTarget.get(level, getMachinePos(), null, null, actionSource);
        if (unsided != null && unsided.insert(what, amount, Actionable.SIMULATE) > 0) {
            return unsided;
        }
        for (Direction d : Direction.values()) {
            var dirTarget = PatternProviderTarget.get(level, getMachinePos(), null, d, actionSource);
            if (dirTarget != null && dirTarget.insert(what, amount, Actionable.SIMULATE) > 0) {
                return dirTarget;
            }
        }
        return sided != null ? sided : unsided;
    }

    @Override
    public boolean canAcceptInputs(KeyCounter[] inputHolder) {
        if (level == null || state != WorkerState.FREE || !isValidWorker()) {
            return false;
        }

        for (var inputList : inputHolder) {
            for (var input : inputList) {
                var target = getTargetForInsert(input.getKey(), input.getLongValue(), Actionable.SIMULATE);
                if (target == null) {
                    return false;
                }
                long inserted = target.insert(input.getKey(), input.getLongValue(), Actionable.SIMULATE);
                if (inserted == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean pushInputs(IPatternDetails patternDetails, KeyCounter[] inputHolder, IMasterPatternProvider master) {
        if (level == null || !isValidWorker()) {
            return false;
        }

        this.activeMaster = new WeakReference<>(master);

        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            var target = getTargetForInsert(what, amount, Actionable.MODULATE);
            if (target != null) {
                target.insert(what, amount, Actionable.MODULATE);
            }
        });

        markOccupied();
        return true;
    }

    @Override
    public void markFree() {
        this.state = WorkerState.FREE;
        this.activeMaster = null;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            var service = grid.getService(ISubnetWorkerService.class);
            if (service != null) {
                service.markFree(this);
            }
        }
        saveChanges();
        if (this.busyHoldTicks <= 0) {
            markForUpdate();
        }
    }

    @Override
    public void markOccupied() {
        this.state = WorkerState.OCCUPIED;
        this.busyHoldTicks = 20;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            var service = grid.getService(ISubnetWorkerService.class);
            if (service != null) {
                service.markOccupied(this);
            }
        }
        saveChanges();
        markForUpdate();
    }

    public void serverTick() {
        if (this.busyHoldTicks > 0) {
            this.busyHoldTicks--;
            if (this.busyHoldTicks == 0 && this.state == WorkerState.FREE) {
                markForUpdate();
            }
        }
    }

    private final ResourceHandler<ItemResource> returnItemHandler = new InsertionOnlyResourceHandlerWithJournal<ItemResource, GenericStack>(ItemResource.EMPTY) {
        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (pendingSideEffect != null) {
                return 0;
            }
            var what = AEItemKey.of(resource);
            updateSnapshots(transaction);
            pendingSideEffect = new GenericStack(what, amount);
            return amount;
        }

        @Override
        protected void onRootCommit(GenericStack originalState) {
            if (pendingSideEffect != null) {
                handleOutputReturned(pendingSideEffect.what(), pendingSideEffect.amount());
                pendingSideEffect = null;
            }
        }
    };

    private final ResourceHandler<FluidResource> returnFluidHandler = new InsertionOnlyResourceHandlerWithJournal<FluidResource, GenericStack>(FluidResource.EMPTY) {
        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (pendingSideEffect != null) {
                return 0;
            }
            var what = AEFluidKey.of(resource);
            updateSnapshots(transaction);
            pendingSideEffect = new GenericStack(what, amount);
            return amount;
        }

        @Override
        protected void onRootCommit(GenericStack originalState) {
            if (pendingSideEffect != null) {
                handleOutputReturned(pendingSideEffect.what(), pendingSideEffect.amount());
                pendingSideEffect = null;
            }
        }
    };

    private void handleOutputReturned(appeng.api.stacks.AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        if (activeMaster != null && activeMaster.get() != null) {
            activeMaster.get().acceptReturn(key, amount);
        } else if (getMainNode().isActive() && getMainNode().getGrid() != null) {
            getMainNode().getGrid().getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, actionSource);
        }

        if (state == WorkerState.OCCUPIED) {
            markFree();
        }
    }

    @Nullable
    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        if (side == getMachineFacing()) {
            return returnItemHandler;
        }
        return null;
    }

    @Nullable
    public ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        if (side == getMachineFacing()) {
            return returnFluidHandler;
        }
        return null;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(5, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level == null || level.isClientSide() || state != WorkerState.OCCUPIED) {
            return TickRateModulation.IDLE;
        }

        // Strategy 1: Check if machine is empty
        if (unlockMode == UnlockMode.ON_MACHINE_EMPTY) {
            var itemHandler = level.getCapability(Capabilities.Item.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
            if (itemHandler != null) {
                boolean empty = true;
                for (int i = 0; i < itemHandler.size(); i++) {
                    if (itemHandler.getAmountAsLong(i) > 0) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    markFree();
                    return TickRateModulation.URGENT;
                }
            }
        }

        // Strategy 2: Active output pull fallback
        if (autoPull) {
            var itemHandler = level.getCapability(Capabilities.Item.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
            if (itemHandler != null) {
                for (int i = 0; i < itemHandler.size(); i++) {
                    var resource = itemHandler.getResource(i);
                    if (!resource.isEmpty()) {
                        try (var tx = Transaction.openRoot()) {
                            int extracted = itemHandler.extract(i, resource, 64, tx);
                            if (extracted > 0) {
                                tx.commit();
                                handleOutputReturned(AEItemKey.of(resource), extracted);
                                return TickRateModulation.URGENT;
                            }
                        }
                    }
                }
            }
        }

        return TickRateModulation.SAME;
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModItems.SUB_PATTERN_PROVIDER.get();
    }
}