package com.ae2subnet.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import com.ae2subnet.blockentity.SubPatternProviderBlockEntity;
import com.ae2subnet.init.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public class SubPatternProviderMenu extends AEBaseMenu {

    private final SubPatternProviderBlockEntity host;

    @GuiSync(0)
    public int workerState = 0; // 0: Free, 1: Occupied

    @GuiSync(1)
    public int unlockMode = 0; // 0: ON_OUTPUT_RETURN, 1: ON_MACHINE_EMPTY

    @GuiSync(2)
    public boolean autoPull = true;

    @GuiSync(3)
    public int machineFacing = 0; // Direction 3D data value

    @GuiSync(4)
    public int maxEnergyRate = 2000;

    @GuiSync(5)
    public boolean brownoutHalted = false;

    @GuiSync(6)
    public boolean subnetPowered = false;

    @GuiSync(7)
    public int energyCardCount = 0;

    public SubPatternProviderMenu(int id, Inventory playerInventory, SubPatternProviderBlockEntity host) {
        super(ModMenuTypes.SUB_PATTERN_PROVIDER.get(), id, playerInventory, host);
        this.host = host;

        // 4 Upgrade Slots on the right
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
            this.workerState = host.getWorkerState().ordinal();
            this.unlockMode = host.getUnlockMode().ordinal();
            this.autoPull = host.isAutoPull();
            this.machineFacing = host.getMachineFacing().get3DDataValue();
            this.maxEnergyRate = host.getMaxEnergyTransferRate();
            this.brownoutHalted = host.isBrownoutHalted();
            this.subnetPowered = host.getMainNode().isPowered();
            this.energyCardCount = host.getEnergyCards();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) { // Cycle facing
            host.cycleFacing();
            broadcastChanges();
            return true;
        } else if (id == 1) { // Cycle unlock mode
            host.cycleUnlockMode();
            broadcastChanges();
            return true;
        } else if (id == 2) { // Toggle auto pull
            host.toggleAutoPull();
            broadcastChanges();
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    public SubPatternProviderBlockEntity getHost() {
        return host;
    }
}
