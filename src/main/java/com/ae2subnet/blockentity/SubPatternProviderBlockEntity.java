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
import com.ae2subnet.init.ModMenuTypes;
import com.ae2subnet.util.BatchLogic;
import com.ae2subnet.util.BatchTracker;
import com.ae2subnet.util.EnergyGrids;
import com.ae2subnet.util.EnergyLogic;
import com.ae2subnet.util.PatternPaging;
import appeng.api.stacks.AEKey;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.util.InsertionOnlyResourceHandlerWithJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import net.minecraft.world.entity.player.Player;

import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.HashSet;
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
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            for (int i = 0; i < upgrades.size(); i++) {
                ItemStack stack = upgrades.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
        super.preRemoveSideEffects(pos, state);
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
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        data.putString("workerState", state.name());
        data.putString("unlockMode", unlockMode.name());
        data.putBoolean("autoPull", autoPull);
        data.putInt("busyHoldTicks", busyHoldTicks);
        data.putInt("batchLimit", batchLimit);
        data.putInt("inFlight", inFlight.size());
        this.upgrades.writeToNBT(data, "upgrades");
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
        this.autoPull = data.getBooleanOr("autoPull", true);
        this.busyHoldTicks = data.getIntOr("busyHoldTicks", 0);
        this.batchLimit = BatchLogic.sanitize(data.getIntOr("batchLimit", 0));
        // Worlds from before batch mode only know OCCUPIED: that was exactly one craft in flight
        int savedInFlight = data.getIntOr("inFlight", this.state == WorkerState.OCCUPIED ? 1 : 0);
        this.inFlight.clear();
        this.inFlight.addWildcards(Math.max(0, savedInFlight));
        this.state = BatchLogic.hasCapacity(this.inFlight.size(), this.batchLimit) ? WorkerState.FREE : WorkerState.OCCUPIED;
        this.upgrades.readFromNBT(data, "upgrades");

        int legacyCards = data.getIntOr("energyCards", 0);
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

    /** True while no FE may be delivered: the combined (main + subnet) buffer is empty/missing or below 20%. */
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

    /** FE that may still be delivered during the current game tick (card limit is FE per tick, not per call). */
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
        if (remainingEnergyBudget() >= 0) { // also rolls the budget over to the current tick
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
            return; // Brownout protection (combined buffer < 20%) or budget used up
        }

        Direction machineFacing = getMachineFacing();
        BlockPos machinePos = getMachinePos();
        var target = level.getCapability(Capabilities.Energy.BLOCK, machinePos, machineFacing.getOpposite());
        if (target == null) {
            target = level.getCapability(Capabilities.Energy.BLOCK, machinePos, null);
        }
        if (target == null) {
            return;
        }

        double simulatedAE = es.extractAEPower(PowerUnit.FE.convertTo(PowerUnit.AE, availableFE), Actionable.SIMULATE, PowerMultiplier.ONE);
        int canExtractFE = (int) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, simulatedAE));
        int toPush = Math.min(availableFE, canExtractFE);
        if (toPush <= 0) {
            return;
        }

        try (var tx = Transaction.openRoot()) {
            int inserted = target.insert(toPush, tx);
            if (inserted > 0) {
                double neededAE = PowerUnit.FE.convertTo(PowerUnit.AE, inserted);
                es.extractAEPower(neededAE, Actionable.MODULATE, PowerMultiplier.ONE);
                consumeEnergyBudget(inserted);
                tx.commit();
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

    private class SubEnergyHandler extends SnapshotJournal<Integer> implements EnergyHandler {
        private int pendingExtractedFE = 0;

        @Override
        protected Integer createSnapshot() {
            return pendingExtractedFE;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            pendingExtractedFE = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            if (pendingExtractedFE > 0) {
                var grid = getMainNode().getGrid();
                if (grid != null && grid.getEnergyService() != null) {
                    double aeAmount = PowerUnit.FE.convertTo(PowerUnit.AE, pendingExtractedFE);
                    grid.getEnergyService().extractAEPower(aeAmount, Actionable.MODULATE, PowerMultiplier.ONE);
                    consumeEnergyBudget(pendingExtractedFE);
                }
                pendingExtractedFE = 0;
            }
        }

        @Override
        public int insert(int maxAmount, TransactionContext transaction) {
            return 0; // Sub-Pattern Provider delivers power to machines, does not accept input
        }

        @Override
        public int extract(int maxAmount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(maxAmount);
            if (maxAmount <= 0) return 0;

            var grid = getMainNode().getGrid();
            if (grid == null) return 0;
            var es = grid.getEnergyService();
            if (es == null) return 0;

            // Brownout protection and the FE/t limit apply to what is already promised in this transaction
            var totals = EnergyGrids.overlayTotals(es);
            int budget = remainingEnergyBudget() - pendingExtractedFE;
            double storedAfterPending = totals.stored() - PowerUnit.FE.convertTo(PowerUnit.AE, pendingExtractedFE);
            int maxSafeFE = EnergyLogic.deliverableFE(budget, storedAfterPending, totals.max());
            int toExtractFE = Math.min(maxAmount, maxSafeFE);

            if (toExtractFE > 0) {
                double wantedAE = PowerUnit.FE.convertTo(PowerUnit.AE, pendingExtractedFE + toExtractFE);
                double simulatedAE = es.extractAEPower(wantedAE, Actionable.SIMULATE, PowerMultiplier.ONE);
                int actualExtractableFE = (int) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, simulatedAE)) - pendingExtractedFE;
                int finalExtractFE = Math.min(toExtractFE, actualExtractableFE);
                if (finalExtractFE > 0) {
                    updateSnapshots(transaction);
                    pendingExtractedFE += finalExtractFE;
                    return finalExtractFE;
                }
            }
            return 0;
        }

        @Override
        public long getAmountAsLong() {
            var grid = getMainNode().getGrid();
            if (grid != null && grid.getEnergyService() != null) {
                var totals = EnergyGrids.overlayTotals(grid.getEnergyService());
                return (long) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, EnergyLogic.safeAE(totals.stored(), totals.max())));
            }
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            var grid = getMainNode().getGrid();
            if (grid != null && grid.getEnergyService() != null) {
                var totals = EnergyGrids.overlayTotals(grid.getEnergyService());
                return (long) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, totals.max()));
            }
            return 0;
        }
    }

    private final EnergyHandler energyHandler = new SubEnergyHandler();

    @Nullable
    public EnergyHandler getEnergyHandler(@Nullable Direction side) {
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
                    inFlight.clear();
                    markFree();
                    return TickRateModulation.URGENT;
                }
            }
        }

        // Strategy 2: Active output pull fallback (never pulls back items that were just pushed in as inputs)
        if (autoPull) {
            boolean fluidPulled = pullFluids();
            var itemHandler = level.getCapability(Capabilities.Item.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
            if (itemHandler == null && fluidPulled) {
                return TickRateModulation.URGENT;
            }
            if (itemHandler != null) {
                boolean pulled = fluidPulled;
                for (int i = 0; i < itemHandler.size() && !inFlight.isEmpty(); i++) {
                    var resource = itemHandler.getResource(i);
                    if (resource.isEmpty()) {
                        continue;
                    }
                    var key = AEItemKey.of(resource);
                    if (pendingInputKeys.contains(key) && !inFlight.expects(key)) {
                        continue;
                    }
                    try (var tx = Transaction.openRoot()) {
                        int extracted = itemHandler.extract(i, resource, 64, tx);
                        if (extracted > 0) {
                            tx.commit();
                            handleOutputReturned(key, extracted);
                            pulled = true;
                        }
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
        var fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, getMachinePos(), getMachineFacing().getOpposite());
        if (fluidHandler == null) {
            fluidHandler = level.getCapability(Capabilities.Fluid.BLOCK, getMachinePos(), null);
        }
        if (fluidHandler == null) {
            return false;
        }
        boolean pulled = false;
        for (int i = 0; i < fluidHandler.size() && !inFlight.isEmpty(); i++) {
            var resource = fluidHandler.getResource(i);
            if (resource.isEmpty()) {
                continue;
            }
            var key = AEFluidKey.of(resource);
            if (pendingInputKeys.contains(key) && !inFlight.expects(key)) {
                continue;
            }
            try (var tx = Transaction.openRoot()) {
                int extracted = fluidHandler.extract(i, resource, Integer.MAX_VALUE, tx);
                if (extracted > 0) {
                    tx.commit();
                    handleOutputReturned(key, extracted);
                    pulled = true;
                }
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