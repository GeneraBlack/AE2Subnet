package com.ae2subnet.block;

import appeng.block.AEBaseEntityBlock;
import com.ae2subnet.api.SubnetDisplayStatus;
import com.ae2subnet.blockentity.LoadbalancerBlockEntity;
import com.ae2subnet.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Output block of the subnet mass mode. */
public class LoadbalancerBlock extends AEBaseEntityBlock<LoadbalancerBlockEntity> {

    public static final EnumProperty<SubnetDisplayStatus> STATUS = EnumProperty.create("status", SubnetDisplayStatus.class);

    public LoadbalancerBlock(Properties props) {
        super(props);
        setBlockEntity(LoadbalancerBlockEntity.class, null, null, null);
        registerDefaultState(defaultBlockState().setValue(STATUS, SubnetDisplayStatus.OFFLINE));
    }

    @Override
    public BlockEntityType<LoadbalancerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LOADBALANCER.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoadbalancerBlockEntity(pos, state);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, LoadbalancerBlockEntity be) {
        return state.setValue(STATUS, be.getDisplayStatus());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STATUS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(STATUS, SubnetDisplayStatus.OFFLINE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        var be = getBlockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            int machines = be.machineSides().size();
            player.sendSystemMessage(Component.literal("§6[Subnet Loadbalancer]§r Machines: §e" + machines
                    + "§r | Crafts: §e" + be.getInFlightCrafts() + "/" + be.capacity()
                    + "§r | Buffer: §e" + be.bufferedCrafts()));
        }
        return InteractionResult.SUCCESS;
    }
}
