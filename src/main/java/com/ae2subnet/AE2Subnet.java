package com.ae2subnet;

import appeng.api.AECapabilities;
import appeng.api.networking.GridServices;
import appeng.blockentity.AEBaseBlockEntity;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.init.ModBlockEntities;
import com.ae2subnet.init.ModBlocks;
import com.ae2subnet.init.ModCreativeTabs;
import com.ae2subnet.init.ModItems;
import com.ae2subnet.init.ModMenuTypes;
import com.ae2subnet.service.SubnetWorkerService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AE2Subnet.MOD_ID)
public class AE2Subnet {
    public static final String MOD_ID = "ae2subnet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AE2Subnet(IEventBus modEventBus) {
        LOGGER.info("AE2Subnet initializing...");

        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // Register custom AE2 Grid Service for subnets
        GridServices.register(ISubnetWorkerService.class, SubnetWorkerService.class);

        modEventBus.addListener(RegisterCapabilitiesEvent.class, this::registerCapabilities);
        modEventBus.addListener(FMLCommonSetupEvent.class, this::commonSetup);

        LOGGER.info("AE2Subnet initialized.");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Grid node host capabilities
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.MASTER_PATTERN_PROVIDER.get(),
                (be, context) -> be
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.SUB_PATTERN_PROVIDER.get(),
                (be, context) -> be
        );

        // Sub-pattern provider machine-facing return capabilities
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.SUB_PATTERN_PROVIDER.get(),
                (be, side) -> be.getItemHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModBlockEntities.SUB_PATTERN_PROVIDER.get(),
                (be, side) -> be.getFluidHandler(side)
        );

        // Energy capabilities (FE supply & high-power input)
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.MASTER_PATTERN_PROVIDER.get(),
                (be, side) -> be.getEnergyHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.SUB_PATTERN_PROVIDER.get(),
                (be, side) -> be.getEnergyHandler(side)
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.MASTER_PATTERN_PROVIDER.get(),
                    ModItems.MASTER_PATTERN_PROVIDER.get()
            );
            AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.SUB_PATTERN_PROVIDER.get(),
                    ModItems.SUB_PATTERN_PROVIDER.get()
            );

            // Register Capacity Card upgrades for Master Pattern Provider
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CAPACITY_CARD, ModBlocks.MASTER_PATTERN_PROVIDER.get(), 4);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CAPACITY_CARD, ModItems.MASTER_PATTERN_PROVIDER.get(), 4);

            // Register Energy Card upgrades for Sub-Pattern Provider
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD, ModBlocks.SUB_PATTERN_PROVIDER.get(), 4);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD, ModItems.SUB_PATTERN_PROVIDER.get(), 4);
        });
    }
}