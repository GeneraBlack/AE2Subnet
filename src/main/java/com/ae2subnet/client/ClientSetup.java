package com.ae2subnet.client;

import com.ae2subnet.init.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import appeng.core.definitions.AEItems;
import com.ae2subnet.init.ModItems;

import com.ae2subnet.AE2Subnet;

@EventBusSubscriber(modid = AE2Subnet.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.MASTER_PATTERN_PROVIDER.get(), MasterPatternProviderScreen::new);
        event.register(ModMenuTypes.SUB_PATTERN_PROVIDER.get(), SubPatternProviderScreen::new);
    }

    /** Explains which upgrade cards our blocks accept and what they do, since AE2 cannot show that itself. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        var tip = event.getToolTip();
        if (stack.is(AEItems.CAPACITY_CARD.asItem())) {
            tip.add(Component.translatable("tooltip.ae2subnet.card.capacity").withStyle(ChatFormatting.GRAY));
        } else if (stack.is(AEItems.ENERGY_CARD.asItem())) {
            tip.add(Component.translatable("tooltip.ae2subnet.card.energy").withStyle(ChatFormatting.GRAY));
        } else if (stack.is(ModItems.MASTER_PATTERN_PROVIDER.get())) {
            tip.add(Component.translatable("tooltip.ae2subnet.master.cards").withStyle(ChatFormatting.GRAY));
        } else if (stack.is(ModItems.SUB_PATTERN_PROVIDER.get())) {
            tip.add(Component.translatable("tooltip.ae2subnet.sub.cards").withStyle(ChatFormatting.GRAY));
        }
    }}