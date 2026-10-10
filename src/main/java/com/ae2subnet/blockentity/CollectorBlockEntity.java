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
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.init.ModBlockEntities;
import com.ae2subnet.init.ModItems;
import com.ae2subnet.service.SubnetMasters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
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
        com.ae2subnet.AE2Subnet.LOGGER.info("Collector {} delivers {}x {}", getBlockPos(), amount, key);
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
            // Only the face that touches the collector is used: the machine decides there what may leave (output
            // slots only). Unsided or other faces could expose input and fuel slots as well.
            pulled |= pullFluids(service, pos, face);
            var touching = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
            if (touching == null) {
                continue;
            }
            for (int i = 0; i < touching.getSlots(); i++) {
                var sim = touching.extractItem(i, Integer.MAX_VALUE, true);
                if (sim.isEmpty()) {
                    continue;
                }
                var key = AEItemKey.of(sim);
                // Awaited outputs and byproducts (anything that is not a pattern input) come out of
                // the touching face, so inputs and fuel a player put in by hand are left alone
                if (!awaited(service, key) && !isByproduct(key)) {
                    continue;
                }
                if (!canDeliver()) {
                    return pulled;
                }
                var extracted = touching.extractItem(i, sim.getCount(), false);
                if (!extracted.isEmpty()) {
                    deliver(AEItemKey.of(extracted), extracted.getCount());
                    pulled = true;
                }
            }
        }
        return pulled;
    }

    /** Fluid outputs of the machine on the touching face (awaited outputs and byproducts, same rules as items). */
    private boolean pullFluids(ISubnetWorkerService service, BlockPos pos, Direction face) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        if (handler == null) {
            return false;
        }
        boolean pulled = false;
        for (int i = 0; i < handler.getTanks(); i++) {
            var inTank = handler.getFluidInTank(i);
            if (inTank.isEmpty()) {
                continue;
            }
            var key = AEFluidKey.of(inTank);
            if (!awaited(service, key) && !isByproduct(key)) {
                continue;
            }
            if (!canDeliver()) {
                return pulled;
            }
            var sim = handler.drain(inTank.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (sim.isEmpty()) {
                continue;
            }
            var drained = handler.drain(sim, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                deliver(AEFluidKey.of(drained), drained.getAmount());
                pulled = true;
            }
        }
        return pulled;
    }

    private boolean canDeliver() {
        if (!getMainNode().isActive()) {
            return false;
        }
        var master = SubnetMasters.find(getMainNode().getGrid());
        return master != null && master.isMassMode();
    }
    /** An item that no pattern of the master feeds into machines, so it can only be something a machine produced. */
    private boolean isByproduct(AEKey key) {
        var master = SubnetMasters.find(getMainNode().getGrid());
        return master != null && master.isMassMode() && !master.isPatternInput(key);
    }

    /**
     * Whether a craft waits for {@code key}. Crafts restored after a reload do not know their output any more, so they
     * accept every output of the master's patterns.
     */
    private boolean awaited(ISubnetWorkerService service, AEKey key) {
        if (service.expectsOutput(key)) {
            return true;
        }
        if (!service.hasRestoredCrafts()) {
            return false;
        }
        var master = SubnetMasters.find(getMainNode().getGrid());
        return master != null && master.isPatternOutput(key);
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
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side.getOpposite());
            if (handler == null) {
                continue;
            }
            int stacks = 0;
            int expected = 0;
            for (int i = 0; i < handler.getSlots(); i++) {
                var r = handler.getStackInSlot(i);
                if (!r.isEmpty()) {
                    stacks++;
                    if (service != null && awaited(service, AEItemKey.of(r))) {
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

    private final IItemHandler itemInput = new IItemHandler() {
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
            if (stack.isEmpty() || !canDeliver()) {
                return stack;
            }
            if (!simulate) {
                deliver(AEItemKey.of(stack), stack.getCount());
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

    private final IFluidHandler fluidInput = new IFluidHandler() {
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
            if (resource.isEmpty() || !canDeliver()) {
                return 0;
            }
            if (action.execute()) {
                deliver(AEFluidKey.of(resource), resource.getAmount());
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

    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return itemInput;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return fluidInput;
    }
    @Override
    protected Item getItemFromBlockEntity() {
        return ModItems.COLLECTOR.get();
    }
}
