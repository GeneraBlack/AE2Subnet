package com.ae2subnet.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import com.ae2subnet.blockentity.SubPatternProviderBlockEntity;
import com.ae2subnet.init.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public class SubPatternProviderMenu extends AEBaseMenu {

    private final SubPatternProviderBlockEntity host;

    @GuiSync(0)
    public int machineFacing = 0;

    @GuiSync(1)
    public int unlockMode = 0;

    @GuiSync(2)
    public boolean autoPull = true;

    @GuiSync(3)
    public int workerState = 0;

    @GuiSync(4)
    public boolean subnetPowered = false;

    @GuiSync(5)
    public int maxEnergyRate = 2000;

    @GuiSync(6)
    public boolean brownoutHalted = false;

    @GuiSync(7)
    public int energyCardCount = 0;

    @GuiSync(8)
    public int batchLimit = 0; // 0 = Auto

    @GuiSync(9)
    public int inFlight = 0; // crafts pushed into the machine whose output is still missing

    public SubPatternProviderMenu(int id, Inventory playerInventory, SubPatternProviderBlockEntity host) {
        super(ModMenuTypes.SUB_PATTERN_PROVIDER.get(), id, playerInventory, host);
        this.host = host;

        var upgradeInv = host.getUpgrades();
        for (int i = 0; i < upgradeInv.size(); i++) {
            int x = 212;
            int y = 68 + i * 18;
            this.addSlot(new MasterPatternProviderMenu.UpgradeCardSlot(upgradeInv, i, x, y), SlotSemantics.UPGRADE);
        }

        // Player main inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9 + 9;
                int x = 26 + col * 18;
                int y = 137 + row * 18;
                this.addSlot(new Slot(playerInventory, slotIndex, x, y), SlotSemantics.PLAYER_INVENTORY);
            }
        }

        // Player hotbar (9 slots)
        for (int col = 0; col < 9; col++) {
            int x = 26 + col * 18;
            int y = 197;
            this.addSlot(new Slot(playerInventory, col, x, y), SlotSemantics.PLAYER_HOTBAR);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.machineFacing = host.getMachineFacing().get3DDataValue();
            this.unlockMode = host.getUnlockMode().ordinal();
            this.autoPull = host.isAutoPull();
            this.workerState = host.getWorkerState().ordinal();
            this.subnetPowered = host.getMainNode().isReady() && (host.getMainNode().isOnline() || host.getMainNode().isPowered() || host.getMainNode().isActive());
            this.maxEnergyRate = host.getMaxEnergyTransferRate();
            this.brownoutHalted = host.isBrownoutHalted();
            this.energyCardCount = host.getEnergyCards();
            this.batchLimit = host.getBatchLimit();
            this.inFlight = host.getInFlightCrafts();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            host.cycleFacing();
            broadcastChanges();
            return true;
        } else if (id == 1) {
            host.cycleUnlockMode();
            broadcastChanges();
            return true;
        } else if (id == 2) {
            host.toggleAutoPull();
            broadcastChanges();
            return true;
        } else if (id == 3) { // Cycle batch limit
            host.cycleBatchLimit();
            broadcastChanges();
            return true;
        }
        return super.clickMenuButton(player, id);
    }


    public SubPatternProviderBlockEntity getHost() {
        return host;
    }
}
