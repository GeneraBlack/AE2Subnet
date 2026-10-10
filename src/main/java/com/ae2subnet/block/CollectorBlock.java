package com.ae2subnet.block;

import appeng.block.AEBaseEntityBlock;
import com.ae2subnet.blockentity.CollectorBlockEntity;
import com.ae2subnet.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Return block of the subnet mass mode. */
public class CollectorBlock extends AEBaseEntityBlock<CollectorBlockEntity> {

    public CollectorBlock(Properties props) {
        super(props);
        setBlockEntity(CollectorBlockEntity.class, null, null, null);
    }

    @Override
    public BlockEntityType<CollectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.COLLECTOR.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CollectorBlockEntity(pos, state);
    }
}
