package com.ae2subnet.client;

import com.ae2subnet.init.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.ae2subnet.AE2Subnet;

@EventBusSubscriber(modid = AE2Subnet.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.MASTER_PATTERN_PROVIDER.get(), MasterPatternProviderScreen::new);
    }
}