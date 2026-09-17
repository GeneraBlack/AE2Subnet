package com.ae2subnet.block;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import com.ae2subnet.blockentity.MasterPatternProviderBlockEntity;
import com.ae2subnet.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MasterPatternProviderBlock extends AEBaseEntityBlock<MasterPatternProviderBlockEntity> {

    public static final EnumProperty<Direction> SUBNET_FACING = EnumProperty.create("facing", Direction.class);
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public MasterPatternProviderBlock(Properties props) {
        super(props);
        setBlockEntity(MasterPatternProviderBlockEntity.class, null, null, null);
        registerDefaultState(defaultBlockState()
                .setValue(SUBNET_FACING, Direction.NORTH)
                .setValue(ONLINE, false));
    }

    @Override
    public BlockEntityType<MasterPatternProviderBlockEntity> getBlockEntityType() {
        return ModBlockEntities.MASTER_PATTERN_PROVIDER.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MasterPatternProviderBlockEntity(pos, state);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, MasterPatternProviderBlockEntity be) {
        return state.setValue(ONLINE, be.isOnline());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SUBNET_FACING, ONLINE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing;
        Level level = context.getLevel();
        BlockPos clickedPos = SubPatternProviderBlock.getClickedTargetPos(context.getClickedPos(), context.getClickedFace(), context.replacingClickedOnBlock());
        var nodeHost = level.getCapability(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST, clickedPos, null);
        if (nodeHost != null) {
            facing = context.getClickedFace().getOpposite();
        } else {
            facing = context.getNearestLookingDirection().getOpposite();
        }
        return defaultBlockState()
                .setValue(SUBNET_FACING, facing)
                .setValue(ONLINE, false);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            Direction current = state.getValue(SUBNET_FACING);
            Direction next = Direction.from3DDataValue((current.get3DDataValue() + 1) % 6);
            var be = getBlockEntity(level, pos);
            BlockState newState = state.setValue(SUBNET_FACING, next);
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
        if (be != null) {
            if (!level.isClientSide()) {
                be.openMenu(player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}