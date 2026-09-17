package com.ae2subnet.block;

import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import com.ae2subnet.api.SubnetDisplayStatus;
import com.ae2subnet.blockentity.SubPatternProviderBlockEntity;
import com.ae2subnet.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SubPatternProviderBlock extends AEBaseEntityBlock<SubPatternProviderBlockEntity> {

    public static final DirectionProperty MACHINE_FACING = BlockStateProperties.FACING;
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

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(MACHINE_FACING, context.getClickedFace().getOpposite())
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
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(heldItem)) {
            Direction current = state.getValue(MACHINE_FACING);
            Direction next = Direction.from3DDataValue((current.get3DDataValue() + 1) % 6);
            level.setBlockAndUpdate(pos, state.setValue(MACHINE_FACING, next));
            var be = getBlockEntity(level, pos);
            if (be != null) {
                be.onFacingChanged();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
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
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}