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
            // Machines with side configuration only let items out on their output side: try the touching face first,
            // then the unsided view and every other face
            var tried = new java.util.ArrayList<ResourceHandler<ItemResource>>(7);
            addHandler(tried, level.getCapability(Capabilities.Item.BLOCK, pos, face));
            addHandler(tried, level.getCapability(Capabilities.Item.BLOCK, pos, null));
            for (Direction other : Direction.values()) {
                addHandler(tried, level.getCapability(Capabilities.Item.BLOCK, pos, other));
            }
            for (var handler : tried) {
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
                        int extracted = handler.extract(i, resource, Integer.MAX_VALUE, tx);
                        if (extracted > 0 && deliver(key, extracted)) {
                            tx.commit();
                            pulled = true;
                        }
                    }
                }
            }
        }
        return pulled;
    }

    private static void addHandler(java.util.List<ResourceHandler<ItemResource>> list, @Nullable ResourceHandler<ItemResource> handler) {
        if (handler != null && !list.contains(handler)) {
            list.add(handler);
        }
    }

    /** One-line state report for the right-click message. */
    public String getDiagnostics() {
        var grid = getMainNode().getGrid();
        if (level == null || grid == null) {
            return "no grid";
        }
        var master = SubnetMasters.find(grid);
        var service = grid.getService(ISubnetWorkerService.class);
        StringBuilder sb = new StringBuilder();
        sb.append("active=").append(getMainNode().isActive());
        sb.append(" | master=").append(master == null ? "NONE" : (master.isMassMode() ? "mass" : "SINGLE mode"));
        sb.append(" | outputs=").append(service == null ? "?" : service.getTotalWorkerCount(true));
        for (Direction side : Direction.values()) {
            BlockPos pos = getBlockPos().relative(side);
            if (GridHelper.getExposedNode(level, pos, side.getOpposite()) != null) {
                continue;
            }
            var handler = level.getCapability(Capabilities.Item.BLOCK, pos, side.getOpposite());
            if (handler == null) {
                continue;
            }
            int stacks = 0;
            int expected = 0;
            for (int i = 0; i < handler.size(); i++) {
                var r = handler.getResource(i);
                if (!r.isEmpty()) {
                    stacks++;
                    if (service != null && service.expectsOutput(AEItemKey.of(r))) {
                        expected++;
                    }
                }
            }
            sb.append(" | ").append(side.getName()).append(": ").append(stacks).append(" stacks, ").append(expected).append(" awaited");
        }
        return sb.toString();
    }



    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, false);
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
