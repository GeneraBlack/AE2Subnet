package com.ae2subnet.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.crafting.pattern.AEProcessingPattern;
import com.ae2subnet.util.ScaleLogic;
import com.ae2subnet.util.ScaledPattern;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.energy.IEnergyOverlayGridConnection;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.MachineSource;
import appeng.me.service.EnergyService;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.ae2subnet.api.IMasterPatternProvider;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.block.MasterPatternProviderBlock;
import com.ae2subnet.init.ModBlockEntities;
import com.ae2subnet.init.ModItems;
import com.ae2subnet.init.ModMenuTypes;
import com.ae2subnet.util.EnergyGrids;
import com.ae2subnet.util.PatternPaging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class MasterPatternProviderBlockEntity extends AENetworkedBlockEntity
        implements ICraftingProvider, IGridTickable, IMasterPatternProvider, InternalInventoryHost, IActionHost, IUpgradeableObject {

    private final IManagedGridNode subnetNode = GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE)
            .setVisualRepresentation(ModItems.MASTER_PATTERN_PROVIDER.get())
            .setInWorldNode(true)
            .setTagName("subnet_node")
            .addService(IEnergyOverlayGridConnection.class, this::getMainEnergyServices);

    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this, 80);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModItems.MASTER_PATTERN_PROVIDER.get(), 4, this::onUpgradesChanged);
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private final IActionSource actionSource = new MachineSource(this::getActionableNode);

    private int priority = 0;
    private long lastFeInsertTime = Long.MIN_VALUE / 2;
    private boolean massMode = false;
    private int scaleMode = ScaleLogic.MODE_AUTO;
    private final List<IPatternDetails> basePatterns = new ArrayList<>();
    private final java.util.Map<IPatternDetails, Integer> observedCapacity = new java.util.HashMap<>();
    private int lastBalancerCount = -1;

    public MasterPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MASTER_PATTERN_PROVIDER.get(), pos, blockState);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModItems.MASTER_PATTERN_PROVIDER.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this)
                .addService(IGridTickable.class, this)
                .addService(IEnergyOverlayGridConnection.class, this::getSubnetEnergyServices)
                .setIdlePowerUsage(2.0);
    }

    public boolean isOnline() {
        if (level != null && level.isClientSide()) {
            return getBlockState().hasProperty(MasterPatternProviderBlock.ONLINE) && getBlockState().getValue(MasterPatternProviderBlock.ONLINE);
        }
        boolean mainOnline = getMainNode().isReady() && (getMainNode().isOnline() || getMainNode().isPowered() || getMainNode().isActive());
        boolean subnetOnline = this.subnetNode.isReady() && (this.subnetNode.isOnline() || this.subnetNode.isPowered() || this.subnetNode.isActive());
        return mainOnline || subnetOnline;
    }

    @Override
    public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State state) {
        super.onMainNodeStateChanged(state);
        invalidateOverlayGrids();
        markForUpdate();
    }

    public void invalidateOverlayGrids() {
        var mainGrid = getMainNode().getGrid();
        if (mainGrid != null && mainGrid.getEnergyService() instanceof EnergyService es) {
            es.invalidateOverlayEnergyGrid();
        }
        var subGrid = this.subnetNode.getGrid();
        if (subGrid != null && subGrid.getEnergyService() instanceof EnergyService es) {
            es.invalidateOverlayEnergyGrid();
        }
    }

    public IManagedGridNode getSubnetNode() {
        return this.subnetNode;
    }

    public Direction getSubnetFacing() {
        BlockState state = getBlockState();
        if (state.hasProperty(MasterPatternProviderBlock.SUBNET_FACING)) {
            return state.getValue(MasterPatternProviderBlock.SUBNET_FACING);
        }
        return Direction.NORTH;
    }

    public void onFacingChanged() {
        Direction subnetFacing = getSubnetFacing();
        this.subnetNode.setExposedOnSides(EnumSet.of(subnetFacing));
        this.getMainNode().setExposedOnSides(EnumSet.complementOf(EnumSet.of(subnetFacing)));
        invalidateOverlayGrids();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        if (dir == getSubnetFacing()) {
            return this.subnetNode.getNode();
        }
        return super.getGridNode(dir);
    }

    @Override
    public void onReady() {
        super.onReady();
        this.subnetNode.create(getLevel(), getBlockPos());
        this.onFacingChanged();
        this.updatePatterns();
        invalidateOverlayGrids();
        this.markForUpdate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        invalidateOverlayGrids();
        this.subnetNode.destroy();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateOverlayGrids();
        this.subnetNode.destroy();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var stack : this.patternInventory) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (var stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.patternInventory.clear();
        this.upgrades.clear();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public int getMaxPages() {
        return PatternPaging.maxPages(upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD));
    }

    public void onUpgradesChanged() {
        int maxAllowedSlots = getMaxPages() * PatternPaging.PAGE_SIZE;
        if (level != null && !level.isClientSide()) {
            for (int i = maxAllowedSlots; i < patternInventory.size(); i++) {
                ItemStack stack = patternInventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, getBlockPos().getX() + 0.5, getBlockPos().getY() + 1.0, getBlockPos().getZ() + 0.5, stack);
                    patternInventory.setItemDirect(i, ItemStack.EMPTY);
                }
            }
        }
        updatePatterns();
        saveChanges();
        markForUpdate();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.subnetNode.saveToNBT(data);
        this.patternInventory.writeToNBT(data, "patterns", registries);
        this.upgrades.writeToNBT(data, "upgrades", registries);
        data.putInt("priority", this.priority);
        data.putBoolean("massMode", this.massMode);
        data.putInt("scaleMode", this.scaleMode);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.subnetNode.loadFromNBT(data);
        this.patternInventory.readFromNBT(data, "patterns", registries);
        this.upgrades.readFromNBT(data, "upgrades", registries);
        this.priority = data.getInt("priority");
        this.massMode = data.getBoolean("massMode");
        this.scaleMode = data.contains("scaleMode")
                ? Math.floorMod(data.getInt("scaleMode"), ScaleLogic.MODE_COUNT)
                : ScaleLogic.MODE_AUTO;
        if (hasLevel()) {
            this.updatePatterns();
        }
    }

    @Override
    public boolean isMassMode() {
        return massMode;
    }

    /** Switches the subnet between individual mode (Sub-Pattern Providers) and mass mode (Loadbalancer + Collector). */
    public void toggleMassMode() {
        this.massMode = !this.massMode;
        observedCapacity.clear();
        if (massMode && scaleMode != ScaleLogic.MODE_OFF) {
            measureCapacities();
        }
        rebuildExposedPatterns();
        saveChanges();
        markForUpdate();
    }

    public void updatePatterns() {
        basePatterns.clear();
        if (level != null && !level.isClientSide()) {
            int maxSlots = getMaxPages() * PatternPaging.PAGE_SIZE;
            for (int i = 0; i < maxSlots; i++) {
                ItemStack stack = this.patternInventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    var details = PatternDetailsHelper.decodePattern(stack, level);
                    if (details != null) {
                        basePatterns.add(details);
                    }
                }
            }
        }
        rebuildExposedPatterns();
    }

    /** Publishes the patterns, scaled to the current factor where mass mode allows it. */
    private void rebuildExposedPatterns() {
        patterns.clear();
        if (level != null && !level.isClientSide()) {
            for (var base : basePatterns) {
                int factor = massMode && base instanceof AEProcessingPattern
                        ? ScaleLogic.resolveFactor(scaleMode, observedCapacity.getOrDefault(base, 0))
                        : 1;
                patterns.add(factor > 1 ? new ScaledPattern(base, factor) : base);
            }
            ICraftingProvider.requestUpdate(getMainNode());
        }
    }

    @Override
    public boolean isPatternOutput(appeng.api.stacks.AEKey key) {
        for (var base : basePatterns) {
            for (var out : base.getOutputs()) {
                if (out.what().equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isPatternInput(appeng.api.stacks.AEKey key) {
        for (var base : basePatterns) {
            for (var in : base.getInputs()) {
                for (var possible : in.getPossibleInputs()) {
                    if (possible.what().equals(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isKnownPattern(IPatternDetails details) {
        return patterns.contains(details) || basePatterns.contains(ScaledPattern.unwrap(details));
    }

    /**
     * Measures how many crafts of each pattern one push can hand to the machines. The highest value seen is kept, so a
     * pattern does not shrink while the machines are momentarily full (running jobs keep their pattern).
     */
    private void measureCapacities() {
        var grid = this.subnetNode.getGrid();
        if (grid == null) {
            return;
        }
        var balancers = grid.getMachines(LoadbalancerBlockEntity.class);
        if (balancers.size() != lastBalancerCount) {
            lastBalancerCount = balancers.size();
            observedCapacity.clear();
        }
        boolean changed = false;
        for (var base : basePatterns) {
            if (!(base instanceof AEProcessingPattern)) {
                continue;
            }
            int best = observedCapacity.getOrDefault(base, 0);
            for (var balancer : balancers) {
                best = Math.max(best, balancer.craftCapacity(base));
            }
            if (best != observedCapacity.getOrDefault(base, 0)) {
                observedCapacity.put(base, best);
                changed = true;
            }
        }
        if (changed) {
            rebuildExposedPatterns();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(100, 100, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level != null && !level.isClientSide() && massMode && scaleMode != ScaleLogic.MODE_OFF) {
            measureCapacities();
        }
        return TickRateModulation.SAME;
    }

    public int getScaleMode() {
        return scaleMode;
    }

    /** Switches to the next scale mode: off, auto, or a fixed upper limit for the factor. */
    public void cycleScaleMode() {
        this.scaleMode = ScaleLogic.nextMode(this.scaleMode);
        observedCapacity.clear();
        if (massMode && scaleMode != ScaleLogic.MODE_OFF) {
            measureCapacities();
        }
        rebuildExposedPatterns();
        saveChanges();
        markForUpdate();
    }


    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        saveChanges();
        updatePatterns();
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return this.patterns;
    }

    @Override
    public int getPatternPriority() {
        return this.priority;
    }

    public void setPatternPriority(int priority) {
        this.priority = priority;
        saveChanges();
        ICraftingProvider.requestUpdate(getMainNode());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!getMainNode().isActive() || !this.subnetNode.isActive() || !isKnownPattern(patternDetails)) {
            return false;
        }

        var subnetGrid = this.subnetNode.getGrid();
        if (subnetGrid == null) {
            return false;
        }

        var service = subnetGrid.getService(ISubnetWorkerService.class);
        if (service == null) {
            return false;
        }

        var worker = service.findAndClaimWorker(patternDetails, inputHolder, this);
        return worker != null;
    }

    @Override
    public boolean isBusy() {
        if (!getMainNode().isActive() || !this.subnetNode.isActive()) {
            return true;
        }
        var subnetGrid = this.subnetNode.getGrid();
        if (subnetGrid == null) {
            return true;
        }
        var service = subnetGrid.getService(ISubnetWorkerService.class);
        return service == null || service.getIdleWorkerCount(massMode) == 0;
    }

    @Override
    public void acceptReturn(GenericStack stack) {
        if (stack != null && getMainNode().isActive() && getMainNode().getGrid() != null) {
            var storage = getMainNode().getGrid().getStorageService().getInventory();
            storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, this.actionSource);
        }
    }

    List<EnergyService> getMainEnergyServices() {
        var grid = getMainNode().getGrid();
        if (grid != null && grid.getEnergyService() instanceof EnergyService es) {
            return Collections.singletonList(es);
        }
        return Collections.emptyList();
    }

    List<EnergyService> getSubnetEnergyServices() {
        var grid = this.subnetNode.getGrid();
        if (grid != null && grid.getEnergyService() instanceof EnergyService es) {
            return Collections.singletonList(es);
        }
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    public AppEngInternalInventory getPatternInventory() {
        return patternInventory;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModItems.MASTER_PATTERN_PROVIDER.get();
    }

    public boolean isExternalEnergyConnected() {
        if (level == null) return false;
        return (level.getGameTime() - lastFeInsertTime) < 40;
    }

    private final IEnergyStorage energyHandler = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            var targetGrid = subnetNode.getGrid() != null ? subnetNode.getGrid() : getMainNode().getGrid();
            if (targetGrid == null) return 0;
            var es = targetGrid.getEnergyService();
            if (es == null) return 0;

            double demandAE = es.getEnergyDemand(PowerUnit.FE.convertTo(PowerUnit.AE, (double) maxReceive));
            int canAccept = (int) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, demandAE));
            int toInsert = Math.min(maxReceive, canAccept);
            if (toInsert > 0) {
                if (!simulate) {
                    double aeAmount = PowerUnit.FE.convertTo(PowerUnit.AE, toInsert);
                    es.injectPower(aeAmount, Actionable.MODULATE);
                    if (level != null) {
                        lastFeInsertTime = level.getGameTime();
                    }
                }
                return toInsert;
            }
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            var targetGrid = subnetNode.getGrid() != null ? subnetNode.getGrid() : getMainNode().getGrid();
            if (targetGrid != null && targetGrid.getEnergyService() != null) {
                var totals = EnergyGrids.overlayTotals(targetGrid.getEnergyService());
                return (int) Math.min(Integer.MAX_VALUE, Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, totals.stored())));
            }
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            var targetGrid = subnetNode.getGrid() != null ? subnetNode.getGrid() : getMainNode().getGrid();
            if (targetGrid != null && targetGrid.getEnergyService() != null) {
                var totals = EnergyGrids.overlayTotals(targetGrid.getEnergyService());
                return (int) Math.min(Integer.MAX_VALUE, Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, totals.max())));
            }
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    @Nullable
    public IEnergyStorage getEnergyHandler(@Nullable Direction side) {
        if (side == getSubnetFacing()) {
            return null;
        }
        return energyHandler;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenuTypes.MASTER_PATTERN_PROVIDER.get(), player, locator);
    }
}