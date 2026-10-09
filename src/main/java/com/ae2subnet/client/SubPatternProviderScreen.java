package com.ae2subnet.client;

import com.ae2subnet.menu.SubPatternProviderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class SubPatternProviderScreen extends AbstractContainerScreen<SubPatternProviderMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("ae2subnet", "textures/gui/sub_pattern_provider.png");

    private Button facingBtn;
    private Button modeBtn;
    private Button pullBtn;

    private static final int HUD_LEFT = 16;
    private static final int HUD_RIGHT = 216;

    public SubPatternProviderScreen(SubPatternProviderMenu menu, Inventory playerInventory, Component title) {
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

        this.facingBtn = Button.builder(Component.literal("Machine: NORTH"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(xo + 26, yo + 68, 148, 17).build();

        this.modeBtn = Button.builder(Component.literal("Unlock: Output Return"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            }
        }).bounds(xo + 26, yo + 88, 148, 17).build();

        this.pullBtn = Button.builder(Component.literal("Auto-Pull: Enabled"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2);
            }
        }).bounds(xo + 26, yo + 108, 148, 17).build();

        this.addRenderableWidget(facingBtn);
        this.addRenderableWidget(modeBtn);
        this.addRenderableWidget(pullBtn);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (facingBtn != null) {
            Direction dir = Direction.from3DDataValue(menu.machineFacing);
            facingBtn.setMessage(Component.literal("Facing: " + dir.getName().toUpperCase()));
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, xo, yo, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Top Header
        graphics.text(this.font, Component.literal("Sub-Pattern Provider"), 10, 6, 0xFFE0E0E0, false);
        String statusStr = menu.subnetPowered
                ? (menu.workerState == 1 ? "● BUSY" : "● IDLE")
                : "○ OFFLINE";
        int statusColor = menu.subnetPowered
                ? (menu.workerState == 1 ? 0xFFFFAA00 : 0xFF55FF55)
                : 0xFFFF5555;
        graphics.text(this.font, Component.literal(statusStr).withColor(statusColor),
                HUD_RIGHT - this.font.width(statusStr), 6, statusColor, false);

        // Telemetry HUD (left column + right-aligned column, one row each)
        String pwrStr = menu.subnetPowered ? "Power: §aActive" : "Power: §cUnpowered";
        graphics.text(this.font, pwrStr, HUD_LEFT, 24, 0xFFFFFFFF, false);

        String cardsStr = "Cards: §e" + menu.energyCardCount + "/4";
        drawRight(graphics, cardsStr, 24);

        String rateStr = "Rate: §a" + String.format("%,d", menu.maxEnergyRate) + " FE/t";
        graphics.text(this.font, rateStr, HUD_LEFT, 36, 0xFFFFFFFF, false);

        String guardStr = menu.brownoutHalted ? "Grid: §cHalted (<20%)" : "Grid: §aNormal (>20%)";
        graphics.text(this.font, guardStr, HUD_LEFT, 48, 0xFFFFFFFF, false);

        graphics.text(this.font, this.playerInventoryTitle, 26, 126, 0xFF8E8E9E, false);
    }
}
