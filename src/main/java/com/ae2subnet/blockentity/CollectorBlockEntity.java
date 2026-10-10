package com.ae2subnet.blockentity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.GridHelper;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.util.InsertionOnlyResourceHandlerWithJournal;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.init.ModBlockEntities;
import com.ae2subnet.init.ModItems;
import com.ae2subnet.service.SubnetMasters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Return block of the mass mode. It takes outputs from every machine it touches (only items that a craft in flight
 * waits for, so inputs are never taken back out) and accepts items/fluids from hoppers and pipes on every side. All of
 * it goes to the Master Pattern Provider of the subnet.
 */
public class CollectorBlockEntity extends AENetworkedBlockEntity implements IGridTickable {

    public CollectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.COLLECTOR.get(), pos, blockState);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModItems.COLLECTOR.get())
                .addService(IGridTickable.class, this)
                .setIdlePowerUsage(0.5);
    }

    /**
     * Hands an output to the subnet's master and books it against the crafts in flight.
     *
     * @return false if there is no master in mass mode that could take it
     */
    private boolean deliver(AEKey key, long amount) {
        if (key == null || amount <= 0 || !getMainNode().isActive()) {
            return false;
        }
        var grid = getMainNode().getGrid();
        var master = SubnetMasters.find(grid);
        if (master == null || !master.isMassMode()) {
            return false;
        }
        master.acceptReturn(key, amount);
        var service = grid.getService(ISubnetWorkerService.class);
        if (service != null) {
            service.creditOutput(key, amount);
        }
        return true;
    }

    private boolean pullOutputs() {
        var grid = getMainNode().getGrid();
        if (level == null || grid == null) {
            return false;
        }
        var service = grid.getService(ISubnetWorkerService.class);
        if (service == null) {
            return false;
        }

        boolean pulled = false;
        for (Direction side : Direction.values()) {
            BlockPos pos = getBlockPos().relative(side);
            Direction face = side.getOpposite();
            if (GridHelper.getExposedNode(level, pos, face) != null) {
                continue; // AE blocks are not machines
            }
            var handler = level.getCapability(Capabilities.Item.BLOCK, pos, face);
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < handler.size(); i++) {
                var resource = handler.getResource(i);
                if (resource.isEmpty()) {
                    continue;
                }
                var key = AEItemKey.of(resource);
                if (!service.expectsOutput(key)) {
                    continue;
                }
                try (var tx = Transaction.openRoot()) {
                    int extracted = handler.extract(i, resource, 64, tx);
                    if (extracted > 0 && deliver(key, extracted)) {
                        tx.commit();
                        pulled = true;
                    }
                }
            }
        }
        return pulled;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(5, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.IDLE;
        }
        return pullOutputs() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    // ---- insertion from hoppers / pipes / machines that push their output ----

    private final ResourceHandler<ItemResource> itemInput = new InsertionOnlyResourceHandlerWithJournal<ItemResource, GenericStack>(ItemResource.EMPTY) {
        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (pendingSideEffect != null || !getMainNode().isActive()) {
                return 0;
            }
            var master = SubnetMasters.find(getMainNode().getGrid());
            if (master == null || !master.isMassMode()) {
                return 0;
            }
            updateSnapshots(transaction);
            pendingSideEffect = new GenericStack(AEItemKey.of(resource), amount);
            return amount;
        }

        @Override
        protected void onRootCommit(GenericStack originalState) {
            if (pendingSideEffect != null) {
                deliver(pendingSideEffect.what(), pendingSideEffect.amount());
                pendingSideEffect = null;
            }
        }
    };

    private final ResourceHandler<FluidResource> fluidInput = new InsertionOnlyResourceHandlerWithJournal<FluidResource, GenericStack>(FluidResource.EMPTY) {
        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (pendingSideEffect != null || !getMainNode().isActive()) {
                return 0;
            }
            var master = SubnetMasters.find(getMainNode().getGrid());
            if (master == null || !master.isMassMode()) {
                return 0;
            }
            updateSnapshots(transaction);
            pendingSideEffect = new GenericStack(AEFluidKey.of(resource), amount);
            return amount;
        }

        @Override
        protected void onRootCommit(GenericStack originalState) {
            if (pendingSideEffect != null) {
                deliver(pendingSideEffect.what(), pendingSideEffect.amount());
                pendingSideEffect = null;
            }
        }
    };

    @Nullable
    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return itemInput;
    }

    @Nullable
    public ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return fluidInput;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModItems.COLLECTOR.get();
    }
}
