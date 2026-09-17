package com.ae2subnet.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.util.inv.AppEngInternalInventory;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.blockentity.MasterPatternProviderBlockEntity;
import com.ae2subnet.init.ModMenuTypes;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MasterPatternProviderMenu extends AEBaseMenu {

    public static class PatternSlot extends Slot {
        private final AppEngInternalInventory inv;
        private final int invSlot;

        public PatternSlot(AppEngInternalInventory inv, int invSlot, int x, int y) {
            super(new SimpleContainer(0), invSlot, x, y);
            this.inv = inv;
            this.invSlot = invSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }

        @Override
        public ItemStack getItem() {
            return inv.getStackInSlot(invSlot);
        }

        @Override
        public void set(ItemStack stack) {
            inv.setItemDirect(invSlot, stack);
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            return inv.extractItem(invSlot, amount, false);
        }

        @Override
        public int getMaxStackSize() {
            return inv.getSlotLimit(invSlot);
        }
    }

    private final MasterPatternProviderBlockEntity host;

    @GuiSync(0)
    public int totalWorkers = 0;

    @GuiSync(1)
    public int idleWorkers = 0;

    @GuiSync(2)
    public int busyWorkers = 0;

    @GuiSync(3)
    public int priority = 0;

    @GuiSync(4)
    public boolean mainActive = false;

    @GuiSync(5)
    public boolean subnetPowered = false;

    public MasterPatternProviderMenu(int id, Inventory playerInventory, MasterPatternProviderBlockEntity host) {
        super(ModMenuTypes.MASTER_PATTERN_PROVIDER.get(), id, playerInventory, host);
        this.host = host;

        // 16 Pattern slots (2 rows of 8)
        var patternInv = host.getPatternInventory();
        for (int i = 0; i < patternInv.size(); i++) {
            int col = i % 8;
            int row = i / 8;
            int x = 34 + col * 18;
            int y = 79 + row * 18;

            this.addSlot(new PatternSlot(patternInv, i, x, y), SlotSemantics.ENCODED_PATTERN);
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
            var subnetGrid = host.getSubnetNode().getGrid();
            if (subnetGrid != null) {
                var service = subnetGrid.getService(ISubnetWorkerService.class);
                if (service != null) {
                    this.totalWorkers = service.getTotalWorkerCount();
                    this.idleWorkers = service.getIdleWorkerCount();
                    this.busyWorkers = service.getBusyWorkerCount();
                } else {
                    this.totalWorkers = 0;
                    this.idleWorkers = 0;
                    this.busyWorkers = 0;
                }
            } else {
                this.totalWorkers = 0;
                this.idleWorkers = 0;
                this.busyWorkers = 0;
            }
            this.priority = host.getPatternPriority();
            this.mainActive = host.getMainNode().isActive();
            this.subnetPowered = host.getSubnetNode().isPowered();
        }

        super.broadcastChanges();
    }

    public MasterPatternProviderBlockEntity getHost() {
        return host;
    }
}