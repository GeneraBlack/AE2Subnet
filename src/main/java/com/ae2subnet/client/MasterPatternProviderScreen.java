package com.ae2subnet.client;

import com.ae2subnet.menu.MasterPatternProviderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MasterPatternProviderScreen extends AbstractContainerScreen<MasterPatternProviderMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2subnet", "textures/gui/master_pattern_provider.png");

    private static final int HUD_LEFT = 16;
    private static final int HUD_RIGHT = 216;

    private Button prevBtn;
    private Button nextBtn;

    public MasterPatternProviderScreen(MasterPatternProviderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 232;
        this.imageHeight = 230;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.prevBtn = Button.builder(Component.literal("<"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(this.leftPos + 144, this.topPos + 66, 14, 12).build();

        this.nextBtn = Button.builder(Component.literal(">"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            }
        }).bounds(this.leftPos + 160, this.topPos + 66, 14, 12).build();

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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Top Header
        guiGraphics.drawString(this.font, Component.literal("Master Pattern Provider"), 10, 6, 0xE0E0E0, false);

        // Telemetry HUD (left column + right-aligned column, one row each)
        String statusStr = menu.totalWorkers > 0 ? "§a● SUBNET ONLINE" : "§c○ NO WORKERS";
        guiGraphics.drawString(this.font, statusStr, HUD_LEFT, 24, 0xFFFFFF, false);

        String powerStr = menu.externalEnergyConnected ? "Power: §aFE Direct" :
                (menu.subnetPowered ? "Power: §aActive" : (menu.mainActive ? "Power: §eBridged" : "Power: §cUnpowered"));
        guiGraphics.drawString(this.font, powerStr, HUD_RIGHT - this.font.width(powerStr), 24, 0xFFFFFF, false);

        String workerCount = "Workers: §f" + menu.totalWorkers;
        guiGraphics.drawString(this.font, workerCount, HUD_LEFT, 36, 0xFFFFFF, false);

        String workerStates = "Idle: §a" + menu.idleWorkers + " §7| Busy: §6" + menu.busyWorkers;
        guiGraphics.drawString(this.font, workerStates, HUD_RIGHT - this.font.width(workerStates), 36, 0xFFFFFF, false);

        String prioStr = "Priority: §e" + menu.priority;
        guiGraphics.drawString(this.font, prioStr, HUD_LEFT, 48, 0xFFFFFF, false);

        String channelStr = menu.mainActive ? "§b● 1 Channel" : "§c○ Offline (No Ch.)";
        guiGraphics.drawString(this.font, channelStr, HUD_RIGHT - this.font.width(channelStr), 48, 0xFFFFFF, false);

        // Section Labels
        String pageInfo = "Patterns " + (menu.currentPage + 1) + "/" + menu.maxPages;
        guiGraphics.drawString(this.font, Component.literal(pageInfo), 34, 69, 0x8E8E9E, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 26, 125, 0x8E8E9E, false);
    }
}