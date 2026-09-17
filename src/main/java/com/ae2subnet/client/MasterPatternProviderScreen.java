package com.ae2subnet.client;

import com.ae2subnet.menu.MasterPatternProviderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class MasterPatternProviderScreen extends AbstractContainerScreen<MasterPatternProviderMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("ae2subnet", "textures/gui/master_pattern_provider.png");

    public MasterPatternProviderScreen(MasterPatternProviderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 210, 230);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, xo, yo, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Top Header
        graphics.text(this.font, Component.literal("Master Pattern Provider"), 10, 6, 0xFFE0E0E0, false);
        String channelStr = menu.mainActive ? "● 1 Channel" : "○ Offline (No Ch.)";
        int channelColor = menu.mainActive ? 0xFF00E5FF : 0xFFFF5555;
        graphics.text(this.font, Component.literal(channelStr).withColor(channelColor), 110, 6, channelColor, false);

        // Telemetry HUD
        String statusStr = menu.totalWorkers > 0 ? "§a● SUBNET ONLINE" : "§c○ NO WORKERS";
        graphics.text(this.font, statusStr, 16, 24, 0xFFFFFFFF, false);

        String powerStr = menu.subnetPowered ? "Power: §aActive" : (menu.mainActive ? "Power: §eBridged" : "Power: §cUnpowered");
        graphics.text(this.font, powerStr, 120, 24, 0xFFFFFFFF, false);

        String workerCount = "Workers: §f" + menu.totalWorkers;
        graphics.text(this.font, workerCount, 16, 38, 0xFFFFFFFF, false);

        String workerStates = "Idle: §a" + menu.idleWorkers + " §7| Busy: §6" + menu.busyWorkers;
        graphics.text(this.font, workerStates, 95, 38, 0xFFFFFFFF, false);

        String prioStr = "Priority: §e" + menu.priority;
        graphics.text(this.font, prioStr, 16, 51, 0xFFFFFFFF, false);

        // Section Labels
        graphics.text(this.font, Component.literal("Processing Patterns (16 Slots)"), 34, 69, 0xFF8E8E9E, false);
        graphics.text(this.font, this.playerInventoryTitle, 26, 125, 0xFF8E8E9E, false);
    }
}