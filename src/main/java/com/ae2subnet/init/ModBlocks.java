package com.ae2subnet.init;

import com.ae2subnet.AE2Subnet;
import com.ae2subnet.block.MasterPatternProviderBlock;
import com.ae2subnet.block.SubPatternProviderBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2Subnet.MOD_ID);

    public static final DeferredBlock<MasterPatternProviderBlock> MASTER_PATTERN_PROVIDER = BLOCKS.registerBlock(
            "master_pattern_provider",
            MasterPatternProviderBlock::new,
            props -> props
                    .strength(2.2f, 11.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<SubPatternProviderBlock> SUB_PATTERN_PROVIDER = BLOCKS.registerBlock(
            "sub_pattern_provider",
            SubPatternProviderBlock::new,
            props -> props
                    .strength(2.2f, 11.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
    );

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}