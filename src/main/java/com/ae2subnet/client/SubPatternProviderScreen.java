package com.ae2subnet.client;

import com.ae2subnet.menu.SubPatternProviderMenu;
import com.ae2subnet.util.BatchLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class SubPatternProviderScreen extends AbstractContainerScreen<SubPatternProviderMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2subnet", "textures/gui/sub_pattern_provider.png");

    private static final int HUD_LEFT = 16;
    private static final int HUD_RIGHT = 216;

    private Button facingBtn;
    private Button batchBtn;
    private Button modeBtn;
    private Button pullBtn;

    public SubPatternProviderScreen(SubPatternProviderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 232;
        this.imageHeight = 230;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.facingBtn = Button.builder(Component.literal("Face: NORTH"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(this.leftPos + 26, this.topPos + 68, 72, 17).build();

        this.batchBtn = Button.builder(Component.literal("Batch: Auto"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 3);
            }
        }).bounds(this.leftPos + 102, this.topPos + 68, 72, 17).build();

        this.modeBtn = Button.builder(Component.literal("Unlock: On Return"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            }
        }).bounds(this.leftPos + 26, this.topPos + 88, 148, 17).build();

        this.pullBtn = Button.builder(Component.literal("Auto-Pull: ON"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2);
            }
        }).bounds(this.leftPos + 26, this.topPos + 108, 148, 17).build();

        this.addRenderableWidget(facingBtn);
        this.addRenderableWidget(batchBtn);
        this.addRenderableWidget(modeBtn);
        this.addRenderableWidget(pullBtn);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (facingBtn != null) {
            Direction dir = Direction.from3DDataValue(menu.machineFacing);
            facingBtn.setMessage(Component.literal("Face: " + dir.getName().toUpperCase()));
        }
        if (batchBtn != null) {
            batchBtn.setMessage(Component.literal("Batch: " + BatchLogic.label(menu.batchLimit)));
        }
        if (modeBtn != null) {
            String modeStr = menu.unlockMode == 0 ? "On Return" : "On Empty";
            modeBtn.setMessage(Component.literal("Unlock: " + modeStr));
        }
        if (pullBtn != null) {
            pullBtn.setMessage(Component.literal("Auto-Pull: " + (menu.autoPull ? "ON" : "OFF")));
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
        guiGraphics.drawString(this.font, Component.literal("Sub-Pattern Provider"), 10, 6, 0xE0E0E0, false);
        boolean busy = menu.workerState == 1 || menu.inFlight > 0;
        String statusStr = menu.subnetPowered
                ? (busy ? "● BUSY" : "● IDLE")
                : "○ OFFLINE";
        int statusColor = menu.subnetPowered
                ? (busy ? 0xFFAA00 : 0x55FF55)
                : 0xFF5555;
        guiGraphics.drawString(this.font, Component.literal(statusStr).withColor(statusColor),
                HUD_RIGHT - this.font.width(statusStr), 6, statusColor, false);

        // Telemetry HUD (left column + right-aligned column, one row each)
        String pwrStr = menu.subnetPowered ? "Power: §aActive" : "Power: §cUnpowered";
        guiGraphics.drawString(this.font, pwrStr, HUD_LEFT, 24, 0xFFFFFF, false);

        String cardsStr = "Cards: §e" + menu.energyCardCount + "/4";
        guiGraphics.drawString(this.font, cardsStr, HUD_RIGHT - this.font.width(cardsStr), 24, 0xFFFFFF, false);

        String rateStr = "Rate: §a" + String.format("%,d", menu.maxEnergyRate) + " FE/t";
        guiGraphics.drawString(this.font, rateStr, HUD_LEFT, 36, 0xFFFFFF, false);

        String craftsStr = "Crafts: §e" + menu.inFlight + "/" + BatchLogic.effectiveLimit(menu.batchLimit);
        guiGraphics.drawString(this.font, craftsStr, HUD_RIGHT - this.font.width(craftsStr), 36, 0xFFFFFF, false);

        String guardStr = menu.brownoutHalted ? "Grid: §cHalted (<20%)" : "Grid: §aNormal (>20%)";
        guiGraphics.drawString(this.font, guardStr, HUD_LEFT, 48, 0xFFFFFF, false);

        guiGraphics.drawString(this.font, this.playerInventoryTitle, 26, 126, 0x8E8E9E, false);
    }
}
