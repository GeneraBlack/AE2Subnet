package com.ae2subnet.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedBlockEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class MasterPatternProviderBlockEntity extends AENetworkedBlockEntity
        implements ICraftingProvider, IMasterPatternProvider, InternalInventoryHost, IActionHost {

    private final IManagedGridNode subnetNode = GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE)
            .setVisualRepresentation(ModItems.MASTER_PATTERN_PROVIDER.get())
            .setInWorldNode(true)
            .setTagName("subnet_node")
            .addService(IEnergyOverlayGridConnection.class, this::getMainEnergyServices);

    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this, 16);
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private final IActionSource actionSource = new MachineSource(this::getActionableNode);

    private int priority = 0;

    public MasterPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MASTER_PATTERN_PROVIDER.get(), pos, blockState);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModItems.MASTER_PATTERN_PROVIDER.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this)
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
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.subnetNode.saveToNBT(data);
        this.patternInventory.writeToNBT(data, "patterns", registries);
        data.putInt("priority", this.priority);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.subnetNode.loadFromNBT(data);
        this.patternInventory.readFromNBT(data, "patterns", registries);
        this.priority = data.getInt("priority");
        if (hasLevel()) {
            this.updatePatterns();
        }
    }

    public void updatePatterns() {
        patterns.clear();
        if (level != null && !level.isClientSide()) {
            for (var stack : this.patternInventory) {
                if (!stack.isEmpty()) {
                    var details = PatternDetailsHelper.decodePattern(stack, level);
                    if (details != null) {
                        patterns.add(details);
                    }
                }
            }
            ICraftingProvider.requestUpdate(getMainNode());
        }
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
        if (!getMainNode().isActive() || !this.subnetNode.isActive() || !this.patterns.contains(patternDetails)) {
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
        return service == null || service.getIdleWorkerCount() == 0;
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

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenuTypes.MASTER_PATTERN_PROVIDER.get(), player, locator);
    }
}