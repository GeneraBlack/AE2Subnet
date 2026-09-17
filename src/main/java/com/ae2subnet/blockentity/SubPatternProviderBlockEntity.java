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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
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
                .addService(IGridTickable.class, this)
                .setIdlePowerUsage(0.5);
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
        Direction machineFacing = getMachineFacing();
        getMainNode().setExposedOnSides(EnumSet.complementOf(EnumSet.of(machineFacing)));
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
        super.onReady();
        onFacingChanged();
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
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString("workerState", state.name());
        data.putString("unlockMode", unlockMode.name());
        data.putBoolean("autoPull", autoPull);
        data.putInt("busyHoldTicks", busyHoldTicks);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        if (data.contains("workerState")) {
            try {
                this.state = WorkerState.valueOf(data.getString("workerState"));
            } catch (Exception ignored) {
                this.state = WorkerState.FREE;
            }
        }
        if (data.contains("unlockMode")) {
            try {
                this.unlockMode = UnlockMode.valueOf(data.getString("unlockMode"));
            } catch (Exception ignored) {
                this.unlockMode = UnlockMode.ON_OUTPUT_RETURN;
            }
        }
        if (data.contains("autoPull")) {
            this.autoPull = data.getBoolean("autoPull");
        }
        if (data.contains("busyHoldTicks")) {
            this.busyHoldTicks = data.getInt("busyHoldTicks");
        }
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

    @Override
    public boolean canAcceptInputs(KeyCounter[] inputHolder) {
        if (level == null || state != WorkerState.FREE || !isValidWorker()) {
            return false;
        }

        var target = PatternProviderTarget.get(level, getMachinePos(), null, getMachineFacing().getOpposite(), actionSource);
        if (target == null) {
            return false;
        }

        for (var inputList : inputHolder) {
            for (var input : inputList) {
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

        var target = PatternProviderTarget.get(level, getMachinePos(), null, getMachineFacing().getOpposite(), actionSource);
        if (target == null) {
            return false;
        }

        this.activeMaster = new WeakReference<>(master);

        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            target.insert(what, amount, Actionable.MODULATE);
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

    private final IItemHandler returnItemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                handleOutputReturned(AEItemKey.of(stack), stack.getCount());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }
    };

    private final IFluidHandler returnFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 64000;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            if (action.execute()) {
                handleOutputReturned(AEFluidKey.of(resource), resource.getAmount());
            }
            return resource.getAmount();
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
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
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == getMachineFacing()) {
            return returnItemHandler;
        }
        return null;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
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
            var itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
            if (itemHandler != null) {
                boolean empty = true;
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    if (!itemHandler.getStackInSlot(i).isEmpty()) {
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
            var itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
            if (itemHandler != null) {
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    var sim = itemHandler.extractItem(i, 64, true);
                    if (!sim.isEmpty()) {
                        var extracted = itemHandler.extractItem(i, 64, false);
                        handleOutputReturned(AEItemKey.of(extracted), extracted.getCount());
                        return TickRateModulation.URGENT;
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