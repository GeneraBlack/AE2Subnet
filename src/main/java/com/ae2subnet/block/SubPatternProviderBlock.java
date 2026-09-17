package com.ae2subnet.block;

import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import com.ae2subnet.api.SubnetDisplayStatus;
import com.ae2subnet.blockentity.SubPatternProviderBlockEntity;
import com.ae2subnet.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SubPatternProviderBlock extends AEBaseEntityBlock<SubPatternProviderBlockEntity> {

    public static final EnumProperty<Direction> MACHINE_FACING = EnumProperty.create("facing", Direction.class);
    public static final EnumProperty<SubnetDisplayStatus> STATUS = EnumProperty.create("status", SubnetDisplayStatus.class);

    public SubPatternProviderBlock(Properties props) {
        super(props);
        setBlockEntity(SubPatternProviderBlockEntity.class, null, null, null);
        registerDefaultState(defaultBlockState()
                .setValue(MACHINE_FACING, Direction.NORTH)
                .setValue(STATUS, SubnetDisplayStatus.OFFLINE));
    }

    @Override
    public BlockEntityType<SubPatternProviderBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SUB_PATTERN_PROVIDER.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SubPatternProviderBlockEntity(pos, state);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, SubPatternProviderBlockEntity be) {
        return state.setValue(STATUS, be.getDisplayStatus());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MACHINE_FACING, STATUS);
    }

    public static BlockPos getClickedTargetPos(BlockPos placementPos, Direction clickedFace, boolean replacingClickedOnBlock) {
        return replacingClickedOnBlock ? placementPos : placementPos.relative(clickedFace.getOpposite());
    }

    public static Direction determinePlacementFacing(boolean isMachine, Direction clickedFace) {
        return isMachine ? clickedFace.getOpposite() : clickedFace;
    }

    public static Direction determinePlacementFacing(BlockPlaceContext context) {
        BlockPos targetPos = getClickedTargetPos(context.getClickedPos(), context.getClickedFace(), context.replacingClickedOnBlock());
        return determinePlacementFacing(context.getLevel(), targetPos, context.getClickedFace());
    }

    public static Direction determinePlacementFacing(@Nullable Level level, BlockPos clickedPos, Direction clickedFace) {
        if (level == null) {
            return determinePlacementFacing(false, clickedFace);
        }
        boolean isMachine = level.getCapability(Capabilities.Item.BLOCK, clickedPos, clickedFace) != null
                || level.getCapability(Capabilities.Fluid.BLOCK, clickedPos, clickedFace) != null
                || level.getCapability(Capabilities.Item.BLOCK, clickedPos, null) != null
                || level.getCapability(Capabilities.Fluid.BLOCK, clickedPos, null) != null;
        return determinePlacementFacing(isMachine, clickedFace);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = determinePlacementFacing(context);
        return defaultBlockState()
                .setValue(MACHINE_FACING, facing)
                .setValue(STATUS, SubnetDisplayStatus.OFFLINE);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide() && type == getBlockEntityType()) {
            return (lvl, pos, state, be) -> {
                if (be instanceof SubPatternProviderBlockEntity subBe) {
                    subBe.serverTick();
                }
            };
        }
        return null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            Direction current = state.getValue(MACHINE_FACING);
            Direction next = Direction.from3DDataValue((current.get3DDataValue() + 1) % 6);
            var be = getBlockEntity(level, pos);
            BlockState newState = state.setValue(MACHINE_FACING, next);
            if (be != null) {
                newState = updateBlockStateFromBlockEntity(newState, be);
            }
            level.setBlockAndUpdate(pos, newState);
            if (be != null) {
                be.onFacingChanged();
                be.markForUpdate();
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        var be = getBlockEntity(level, pos);
        if (be != null && !level.isClientSide()) {
            Direction machineDir = state.getValue(MACHINE_FACING);
            player.sendSystemMessage(Component.literal("§b[Sub-Pattern Provider]§r Status: " +
                    (be.getDisplayStatus() == SubnetDisplayStatus.IDLE ? "§aIDLE (Ready)§r" :
                     be.getDisplayStatus() == SubnetDisplayStatus.BUSY ? "§6BUSY (Crafting)§r" : "§cOFFLINE§r") +
                    " | Machine Facing: §e" + machineDir.getName().toUpperCase() + "§r" +
                    " | Unlock Mode: §7" + be.getUnlockMode().name() + "§r"));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }
}