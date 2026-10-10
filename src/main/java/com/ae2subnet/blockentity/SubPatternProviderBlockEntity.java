package com.ae2subnet.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
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
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import com.ae2subnet.api.IMasterPatternProvider;
import com.ae2subnet.api.ISubnetWorker;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.api.UnlockMode;
import com.ae2subnet.api.WorkerState;
import com.ae2subnet.block.SubPatternProviderBlock;
import com.ae2subnet.init.ModBlockEntities;
import com.ae2subnet.init.ModItems;
import com.ae2subnet.init.ModMenuTypes;
import com.ae2subnet.util.BatchLogic;
import com.ae2subnet.util.BatchTracker;
import com.ae2subnet.util.EnergyGrids;
import com.ae2subnet.util.EnergyLogic;
import com.ae2subnet.util.PatternPaging;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubPatternProviderBlockEntity extends AENetworkedBlockEntity
        implements ISubnetWorker, IActionHost, IGridTickable, ServerTickingBlockEntity, IUpgradeableObject {

    private WorkerState state = WorkerState.FREE;
    private UnlockMode unlockMode = UnlockMode.ON_OUTPUT_RETURN;
    private boolean autoPull = true;
    private int busyHoldTicks = 0;

    // Batch mode: crafts pushed into the machine that have not delivered their output yet (0 = Auto limit)
    private int batchLimit = 0;
    private final BatchTracker<AEKey> inFlight = new BatchTracker<>();
    // Items pushed in as inputs while crafts are in flight; auto-pull must not take them back out
    private final Set<AEKey> pendingInputKeys = new HashSet<>();
    // Per-tick FE budget shared by active push and passive pull so the card limit really is "FE per tick"
    private long energyBudgetTick = -1;
    private int energyBudgetUsed = 0;

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModItems.SUB_PATTERN_PROVIDER.get(), 4, this::onUpgradesChanged);
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
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public void onUpgradesChanged() {
        saveChanges();
        markForUpdate();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString("workerState", state.name());
        data.putString("unlockMode", unlockMode.name());
        data.putBoolean("autoPull", autoPull);
        data.putInt("busyHoldTicks", busyHoldTicks);
        data.putInt("batchLimit", batchLimit);
        data.putInt("inFlight", inFlight.size());
        this.upgrades.writeToNBT(data, "upgrades", registries);
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
        this.autoPull = !data.contains("autoPull") || data.getBoolean("autoPull");
        if (data.contains("busyHoldTicks")) {
            this.busyHoldTicks = data.getInt("busyHoldTicks");
        }
        this.batchLimit = BatchLogic.sanitize(data.getInt("batchLimit"));
        // Worlds from before batch mode only know OCCUPIED: that was exactly one craft in flight
        int savedInFlight = data.contains("inFlight") ? data.getInt("inFlight") : (this.state == WorkerState.OCCUPIED ? 1 : 0);
        this.inFlight.clear();
        this.inFlight.addWildcards(Math.max(0, savedInFlight));
        this.state = BatchLogic.hasCapacity(this.inFlight.size(), this.batchLimit) ? WorkerState.FREE : WorkerState.OCCUPIED;
        this.upgrades.readFromNBT(data, "upgrades", registries);

        int legacyCards = data.getInt("energyCards");
        if (legacyCards > 0 && upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD) == 0) {
            for (int i = 0; i < Math.min(4, legacyCards); i++) {
                upgrades.setItemDirect(i, new ItemStack(AEItems.ENERGY_CARD.asItem(), 1));
            }
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
        return (!inFlight.isEmpty() || state == WorkerState.OCCUPIED || busyHoldTicks > 0)
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

    public int getEnergyCards() {
        return upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD);
    }

    public void setEnergyCards(int count) {
        int clamped = Math.max(0, Math.min(4, count));
        for (int i = 0; i < upgrades.size(); i++) {
            upgrades.setItemDirect(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < clamped; i++) {
            upgrades.setItemDirect(i, new ItemStack(AEItems.ENERGY_CARD.asItem(), 1));
        }
        onUpgradesChanged();
    }

    public void cycleFacing() {
        Direction next = PatternPaging.nextFacing(getMachineFacing());
        if (level != null && getBlockState().hasProperty(SubPatternProviderBlock.MACHINE_FACING)) {
            BlockState newState = getBlockState().setValue(SubPatternProviderBlock.MACHINE_FACING, next);
            newState = newState.setValue(SubPatternProviderBlock.STATUS, getDisplayStatus());
            level.setBlockAndUpdate(getBlockPos(), newState);
            onFacingChanged();
            markForUpdate();
        }
    }

    public void cycleUnlockMode() {
        this.unlockMode = PatternPaging.nextUnlockMode(this.unlockMode);
        saveChanges();
        markForUpdate();
    }

    public void toggleAutoPull() {
        this.autoPull = !this.autoPull;
        saveChanges();
        markForUpdate();
    }

    /** Configured batch limit: 0 = Auto, otherwise 1..{@link BatchLogic#MAX_BATCH}. */
    public int getBatchLimit() {
        return batchLimit;
    }

    public void cycleBatchLimit() {
        this.batchLimit = BatchLogic.nextLimit(this.batchLimit);
        refreshWorkerState();
    }

    /** Crafts pushed into the machine that have not delivered their output yet. */
    public int getInFlightCrafts() {
        return inFlight.size();
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenuTypes.SUB_PATTERN_PROVIDER.get(), player, locator);
    }

    public int getMaxEnergyTransferRate() {
        return EnergyLogic.maxTransferRate(getEnergyCards());
    }

    public boolean isBrownoutHalted() {
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        var es = grid.getEnergyService();
        if (es == null) return false;
        var totals = EnergyGrids.overlayTotals(es);
        return EnergyLogic.isBrownout(totals.stored(), totals.max());
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
        if (level == null || state != WorkerState.FREE || !isValidWorker()
                || !BatchLogic.hasCapacity(inFlight.size(), batchLimit)) {
            return false;
        }

        for (var inputList : inputHolder) {
            for (var input : inputList) {
                var target = getTargetForInsert(input.getKey(), input.getLongValue(), Actionable.SIMULATE);
                if (target == null) {
                    return false;
                }
                // The machine must take the complete amount, otherwise part of the craft would be lost
                long inserted = target.insert(input.getKey(), input.getLongValue(), Actionable.SIMULATE);
                if (inserted < input.getLongValue()) {
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
            long inserted = target != null ? target.insert(what, amount, Actionable.MODULATE) : 0;
            if (inserted < amount) {
                deliverToNetwork(what, amount - inserted); // never lose what the machine did not take
            } else {
                pendingInputKeys.add(what);
            }
        });

        var primary = patternDetails.getPrimaryOutput();
        inFlight.add(primary != null ? primary.what() : null, primary != null ? primary.amount() : 0);

        busyHoldTicks = 20;
        getMainNode().ifPresent((grid, gridNode) -> grid.getTickManager().alertDevice(gridNode));
        refreshWorkerState();
        return true;
    }

    /**
     * Derives FREE/OCCUPIED from the crafts in flight and tells the worker service. A provider with spare batch
     * capacity is re-queued at the tail, which spreads crafts evenly across all machines.
     */
    private void refreshWorkerState() {
        boolean full = !BatchLogic.hasCapacity(inFlight.size(), batchLimit);
        this.state = full ? WorkerState.OCCUPIED : WorkerState.FREE;
        if (inFlight.isEmpty()) {
            this.activeMaster = null;
            this.pendingInputKeys.clear();
        }
        var grid = getMainNode().getGrid();
        if (grid != null) {
            var service = grid.getService(ISubnetWorkerService.class);
            if (service != null) {
                if (full) {
                    service.markOccupied(this);
                } else {
                    service.markFree(this);
                }
            }
        }
        saveChanges();
        markForUpdate();
    }

    @Override
    public void markFree() {
        // External request to free the worker: forget everything in flight
        inFlight.clear();
        refreshWorkerState();
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
        pushEnergyToMachine();
    }

    private int remainingEnergyBudget() {
        if (level == null) return 0;
        long now = level.getGameTime();
        if (now != energyBudgetTick) {
            energyBudgetTick = now;
            energyBudgetUsed = 0;
        }
        return Math.max(0, getMaxEnergyTransferRate() - energyBudgetUsed);
    }

    private void consumeEnergyBudget(int fe) {
        if (remainingEnergyBudget() >= 0) {
            energyBudgetUsed += Math.max(0, fe);
        }
    }

    private void pushEnergyToMachine() {
        if (level == null || level.isClientSide() || !getMainNode().isActive()) {
            return;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) return;
        var es = grid.getEnergyService();
        if (es == null) return;

        var totals = EnergyGrids.overlayTotals(es);
        int availableFE = EnergyLogic.deliverableFE(remainingEnergyBudget(), totals.stored(), totals.max());
        if (availableFE <= 0) {
            return;
        }

        Direction machineFacing = getMachineFacing();
        BlockPos machinePos = getMachinePos();
        var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, machinePos, machineFacing.getOpposite());
        if (target == null) {
            target = level.getCapability(Capabilities.EnergyStorage.BLOCK, machinePos, null);
        }
        if (target == null || !target.canReceive()) {
            return;
        }

        double simulatedAE = es.extractAEPower(PowerUnit.FE.convertTo(PowerUnit.AE, availableFE), Actionable.SIMULATE, PowerMultiplier.ONE);
        int canExtractFE = (int) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, simulatedAE));
        int toPush = Math.min(availableFE, canExtractFE);
        if (toPush <= 0) {
            return;
        }

        int accepted = target.receiveEnergy(toPush, true);
        if (accepted > 0) {
            double neededAE = PowerUnit.FE.convertTo(PowerUnit.AE, accepted);
            double extractedAE = es.extractAEPower(neededAE, Actionable.MODULATE, PowerMultiplier.ONE);
            int actualFE = (int) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, extractedAE));
            int inserted = target.receiveEnergy(actualFE, false);
            consumeEnergyBudget(inserted);
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
        deliverToNetwork(key, amount);

        if (!inFlight.isEmpty() && inFlight.returned(key, amount) > 0) {
            refreshWorkerState();
        }
    }

    /** Sends an item/fluid back to the master (or straight into the network if there is no active master). */
    private void deliverToNetwork(appeng.api.stacks.AEKey key, long amount) {
        if (activeMaster != null && activeMaster.get() != null) {
            activeMaster.get().acceptReturn(key, amount);
        } else if (getMainNode().isActive() && getMainNode().getGrid() != null) {
            getMainNode().getGrid().getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, actionSource);
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

    private final IEnergyStorage energyHandler = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0; // Sub-Pattern Provider delivers power to machines, does not accept input
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            var grid = getMainNode().getGrid();
            if (grid == null) return 0;
            var es = grid.getEnergyService();
            if (es == null) return 0;

            var totals = EnergyGrids.overlayTotals(es);
            int budget = remainingEnergyBudget();
            int maxSafeFE = EnergyLogic.deliverableFE(budget, totals.stored(), totals.max());
            int toExtractFE = Math.min(maxExtract, maxSafeFE);
            if (toExtractFE <= 0) return 0;

            double wantedAE = PowerUnit.FE.convertTo(PowerUnit.AE, toExtractFE);
            double extractedAE = es.extractAEPower(wantedAE, simulate ? Actionable.SIMULATE : Actionable.MODULATE, PowerMultiplier.ONE);
            int actualFE = (int) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, extractedAE));
            if (!simulate && actualFE > 0) {
                consumeEnergyBudget(actualFE);
            }
            return actualFE;
        }

        @Override
        public int getEnergyStored() {
            var grid = getMainNode().getGrid();
            if (grid != null && grid.getEnergyService() != null) {
                var totals = EnergyGrids.overlayTotals(grid.getEnergyService());
                return (int) Math.min(Integer.MAX_VALUE, Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, EnergyLogic.safeAE(totals.stored(), totals.max()))));
            }
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            var grid = getMainNode().getGrid();
            if (grid != null && grid.getEnergyService() != null) {
                var totals = EnergyGrids.overlayTotals(grid.getEnergyService());
                return (int) Math.min(Integer.MAX_VALUE, Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, totals.max())));
            }
            return 0;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    @Nullable
    public IEnergyStorage getEnergyHandler(@Nullable Direction side) {
        if (side == getMachineFacing()) {
            return energyHandler;
        }
        return null;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(5, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level == null || level.isClientSide() || inFlight.isEmpty()) {
            return TickRateModulation.IDLE;
        }

        // Strategy 1: Check if machine is empty -> every craft in flight is done
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
                    inFlight.clear();
                    markFree();
                    return TickRateModulation.URGENT;
                }
            }
        }

        // Strategy 2: Active output pull fallback (never pulls back items that were just pushed in as inputs)
        if (autoPull) {
            boolean fluidPulled = pullFluids();
            var itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
            if (itemHandler == null && fluidPulled) {
                return TickRateModulation.URGENT;
            }
            if (itemHandler != null) {
                boolean pulled = fluidPulled;
                for (int i = 0; i < itemHandler.getSlots() && !inFlight.isEmpty(); i++) {
                    var sim = itemHandler.extractItem(i, 64, true);
                    if (sim.isEmpty()) {
                        continue;
                    }
                    var key = AEItemKey.of(sim);
                    if (pendingInputKeys.contains(key) && !inFlight.expects(key)) {
                        continue;
                    }
                    var extracted = itemHandler.extractItem(i, 64, false);
                    if (!extracted.isEmpty()) {
                        handleOutputReturned(AEItemKey.of(extracted), extracted.getCount());
                        pulled = true;
                    }
                }
                if (pulled) {
                    return TickRateModulation.URGENT;
                }
            }
        }

        return TickRateModulation.SAME;
    }


    /** Pulls fluid outputs out of the machine (same rules as for items: never what was just pushed in as input). */
    private boolean pullFluids() {
        var fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
        if (fluidHandler == null) {
            fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, getMachinePos(), null);
        }
        if (fluidHandler == null) {
            return false;
        }
        boolean pulled = false;
        for (int i = 0; i < fluidHandler.getTanks() && !inFlight.isEmpty(); i++) {
            var inTank = fluidHandler.getFluidInTank(i);
            if (inTank.isEmpty()) {
                continue;
            }
            var key = AEFluidKey.of(inTank);
            if (pendingInputKeys.contains(key) && !inFlight.expects(key)) {
                continue;
            }
            var sim = fluidHandler.drain(inTank.copyWithAmount(inTank.getAmount()), IFluidHandler.FluidAction.SIMULATE);
            if (sim.isEmpty()) {
                continue;
            }
            var drained = fluidHandler.drain(sim, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                handleOutputReturned(AEFluidKey.of(drained), drained.getAmount());
                pulled = true;
            }
        }
        return pulled;
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