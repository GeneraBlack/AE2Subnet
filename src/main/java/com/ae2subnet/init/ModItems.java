package com.ae2subnet.init;

import com.ae2subnet.AE2Subnet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2Subnet.MOD_ID);

    public static final DeferredItem<BlockItem> MASTER_PATTERN_PROVIDER = ITEMS.registerSimpleBlockItem(
            ModBlocks.MASTER_PATTERN_PROVIDER
    );

    public static final DeferredItem<BlockItem> SUB_PATTERN_PROVIDER = ITEMS.registerSimpleBlockItem(
            ModBlocks.SUB_PATTERN_PROVIDER
    );

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}