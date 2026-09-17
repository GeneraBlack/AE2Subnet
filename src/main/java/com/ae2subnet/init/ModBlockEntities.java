package com.ae2subnet.init;

import com.ae2subnet.AE2Subnet;
import com.ae2subnet.blockentity.MasterPatternProviderBlockEntity;
import com.ae2subnet.blockentity.SubPatternProviderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2Subnet.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MasterPatternProviderBlockEntity>> MASTER_PATTERN_PROVIDER =
            BLOCK_ENTITIES.register("master_pattern_provider", () ->
                    BlockEntityType.Builder.of(
                            MasterPatternProviderBlockEntity::new,
                            ModBlocks.MASTER_PATTERN_PROVIDER.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubPatternProviderBlockEntity>> SUB_PATTERN_PROVIDER =
            BLOCK_ENTITIES.register("sub_pattern_provider", () ->
                    BlockEntityType.Builder.of(
                            SubPatternProviderBlockEntity::new,
                            ModBlocks.SUB_PATTERN_PROVIDER.get()
                    ).build(null)
            );

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}