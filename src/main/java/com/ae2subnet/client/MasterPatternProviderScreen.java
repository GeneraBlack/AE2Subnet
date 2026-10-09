package com.ae2subnet.client;

import com.ae2subnet.menu.MasterPatternProviderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class MasterPatternProviderScreen extends AbstractContainerScreen<MasterPatternProviderMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("ae2subnet", "textures/gui/master_pattern_provider.png");

    private Button prevBtn;
    private Button nextBtn;

    private static final int HUD_LEFT = 16;
    private static final int HUD_RIGHT = 216;

    public MasterPatternProviderScreen(MasterPatternProviderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 232, 230);
    }

    private void drawRight(GuiGraphicsExtractor graphics, String text, int y) {
        graphics.text(this.font, text, HUD_RIGHT - this.font.width(text), y, 0xFFFFFFFF, false);
    }

    @Override
    protected void init() {
        super.init();
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;

        this.prevBtn = Button.builder(Component.literal("<"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(xo + 144, yo + 66, 14, 12).build();

        this.nextBtn = Button.builder(Component.literal(">"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            }
        }).bounds(xo + 160, yo + 66, 14, 12).build();

        this.addRenderableWidget(prevBtn);
        this.addRenderableWidget(nextBtn);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (prevBtn != null) {
            prevBtn.active = menu.currentPage > 0;
        }
        if (nextBtn != null) {
            nextBtn.active = menu.currentPage < menu.maxPages - 1;
        }
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

        // Telemetry HUD (left column + right-aligned column, one row each)
        String statusStr = menu.totalWorkers > 0 ? "§a● SUBNET ONLINE" : "§c○ NO WORKERS";
        graphics.text(this.font, statusStr, HUD_LEFT, 24, 0xFFFFFFFF, false);

        String powerStr = menu.externalEnergyConnected ? "Power: §aFE Direct" :
                (menu.subnetPowered ? "Power: §aActive" : (menu.mainActive ? "Power: §eBridged" : "Power: §cUnpowered"));
        drawRight(graphics, powerStr, 24);

        String workerCount = "Workers: §f" + menu.totalWorkers;
        graphics.text(this.font, workerCount, HUD_LEFT, 36, 0xFFFFFFFF, false);

        String workerStates = "Idle: §a" + menu.idleWorkers + " §7| Busy: §6" + menu.busyWorkers;
        drawRight(graphics, workerStates, 36);

        String prioStr = "Priority: §e" + menu.priority;
        graphics.text(this.font, prioStr, HUD_LEFT, 48, 0xFFFFFFFF, false);

        String channelStr = menu.mainActive ? "§b● 1 Channel" : "§c○ Offline (No Ch.)";
        drawRight(graphics, channelStr, 48);

        // Section Labels
        String pageInfo = "Patterns " + (menu.currentPage + 1) + "/" + menu.maxPages;
        graphics.text(this.font, Component.literal(pageInfo), 34, 69, 0xFF8E8E9E, false);
        graphics.text(this.font, this.playerInventoryTitle, 26, 125, 0xFF8E8E9E, false);
    }
}