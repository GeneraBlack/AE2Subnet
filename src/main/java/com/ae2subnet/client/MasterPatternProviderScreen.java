package com.ae2subnet.client;

import com.ae2subnet.menu.MasterPatternProviderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MasterPatternProviderScreen extends AbstractContainerScreen<MasterPatternProviderMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2subnet", "textures/gui/master_pattern_provider.png");

    public MasterPatternProviderScreen(MasterPatternProviderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 210;
        this.imageHeight = 230;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Top Header
        guiGraphics.drawString(this.font, Component.literal("Master Pattern Provider"), 10, 6, 0xE0E0E0, false);
        String channelStr = menu.mainActive ? "● 1 Channel" : "○ Offline (No Ch.)";
        int channelColor = menu.mainActive ? 0x00E5FF : 0xFF5555;
        guiGraphics.drawString(this.font, Component.literal(channelStr).withColor(channelColor), 110, 6, channelColor, false);

        // Telemetry HUD
        String statusStr = menu.totalWorkers > 0 ? "§a● SUBNET ONLINE" : "§c○ NO WORKERS";
        guiGraphics.drawString(this.font, statusStr, 16, 24, 0xFFFFFF, false);

        String powerStr = menu.subnetPowered ? "Power: §aActive" : (menu.mainActive ? "Power: §eBridged" : "Power: §cUnpowered");
        guiGraphics.drawString(this.font, powerStr, 120, 24, 0xFFFFFF, false);

        String workerCount = "Workers: §f" + menu.totalWorkers;
        guiGraphics.drawString(this.font, workerCount, 16, 38, 0xFFFFFF, false);

        String workerStates = "Idle: §a" + menu.idleWorkers + " §7| Busy: §6" + menu.busyWorkers;
        guiGraphics.drawString(this.font, workerStates, 95, 38, 0xFFFFFF, false);

        String prioStr = "Priority: §e" + menu.priority;
        guiGraphics.drawString(this.font, prioStr, 16, 51, 0xFFFFFF, false);

        // Section Labels
        guiGraphics.drawString(this.font, Component.literal("Processing Patterns (16 Slots)"), 34, 69, 0x8E8E9E, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 26, 125, 0x8E8E9E, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }
}