package com.ae2subnet.init;

import appeng.menu.implementations.MenuTypeBuilder;
import com.ae2subnet.AE2Subnet;
import com.ae2subnet.blockentity.MasterPatternProviderBlockEntity;
import com.ae2subnet.menu.MasterPatternProviderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AE2Subnet.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<MasterPatternProviderMenu>> MASTER_PATTERN_PROVIDER =
            MENU_TYPES.register("master_pattern_provider", () ->
                    MenuTypeBuilder.create(MasterPatternProviderMenu::new, MasterPatternProviderBlockEntity.class)
                            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(AE2Subnet.MOD_ID, "master_pattern_provider"))
            );

    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}