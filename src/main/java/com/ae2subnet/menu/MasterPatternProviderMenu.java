package com.ae2subnet.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.util.inv.AppEngInternalInventory;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.blockentity.CollectorBlockEntity;
import com.ae2subnet.blockentity.MasterPatternProviderBlockEntity;
import com.ae2subnet.init.ModMenuTypes;
import com.ae2subnet.util.PatternPaging;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.IUpgradeInventory;
import net.minecraft.world.entity.player.Player;
import java.util.function.IntSupplier;

public class MasterPatternProviderMenu extends AEBaseMenu {

    public static class UpgradeCardSlot extends Slot {
        private final IUpgradeInventory upgrades;
        private final int invSlot;

        public UpgradeCardSlot(IUpgradeInventory upgrades, int invSlot, int x, int y) {
            super(new SimpleContainer(0), invSlot, x, y);
            this.upgrades = upgrades;
            this.invSlot = invSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return upgrades.isItemValid(invSlot, stack);
        }

        @Override
        public ItemStack getItem() {
            return upgrades.getStackInSlot(invSlot);
        }

        @Override
        public void set(ItemStack stack) {
            upgrades.setItemDirect(invSlot, stack);
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            return upgrades.extractItem(invSlot, amount, false);
        }

        @Override
        public int getMaxStackSize() {
            return upgrades.getSlotLimit(invSlot);
        }
    }

    public static class PatternSlot extends Slot {
        private final AppEngInternalInventory inv;
        private final int invSlot;
        private final IntSupplier pageSupplier;

        public PatternSlot(AppEngInternalInventory inv, int invSlot, int x, int y, IntSupplier pageSupplier) {
            super(new SimpleContainer(0), invSlot, x, y);
            this.inv = inv;
            this.invSlot = invSlot;
            this.pageSupplier = pageSupplier;
        }

        private int getActualIndex() {
            return PatternPaging.slotIndex(pageSupplier.getAsInt(), invSlot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }

        @Override
        public ItemStack getItem() {
            int idx = getActualIndex();
            return idx < inv.size() ? inv.getStackInSlot(idx) : ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack stack) {
            int idx = getActualIndex();
            if (idx < inv.size()) {
                inv.setItemDirect(idx, stack);
                setChanged();
            }
        }

        @Override
        public ItemStack remove(int amount) {
            int idx = getActualIndex();
            return idx < inv.size() ? inv.extractItem(idx, amount, false) : ItemStack.EMPTY;
        }

        @Override
        public int getMaxStackSize() {
            int idx = getActualIndex();
            return idx < inv.size() ? inv.getSlotLimit(idx) : 0;
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

    @GuiSync(6)
    public boolean externalEnergyConnected = false;

    @GuiSync(7)
    public int currentPage = 0;

    @GuiSync(8)
    public int maxPages = 1;

    @GuiSync(9)
    public boolean massMode = false;

    @GuiSync(10)
    public int foreignWorkers = 0; // workers of the other mode, ignored by this subnet

    @GuiSync(11)
    public int collectors = 0;

    @GuiSync(12)
    public int scaleMode = 1;

    public MasterPatternProviderMenu(int id, Inventory playerInventory, MasterPatternProviderBlockEntity host) {
        super(ModMenuTypes.MASTER_PATTERN_PROVIDER.get(), id, playerInventory, host);
        this.host = host;

        // 16 Pattern slots (2 rows of 8) for current page
        var patternInv = host.getPatternInventory();
        for (int i = 0; i < 16; i++) {
            int col = i % 8;
            int row = i / 8;
            int x = 34 + col * 18;
            int y = 79 + row * 18;

            this.addSlot(new PatternSlot(patternInv, i, x, y, () -> this.currentPage), SlotSemantics.ENCODED_PATTERN);
        }

        // 4 Upgrade Slots on the right
        var upgradeInv = host.getUpgrades();
        for (int i = 0; i < upgradeInv.size(); i++) {
            int x = 212;
            int y = 68 + i * 18;
            this.addSlot(new UpgradeCardSlot(upgradeInv, i, x, y), SlotSemantics.UPGRADE);
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
            boolean mass = host.isMassMode();
            this.massMode = mass;
            this.scaleMode = host.getScaleMode();
            this.totalWorkers = 0;
            this.idleWorkers = 0;
            this.busyWorkers = 0;
            this.foreignWorkers = 0;
            this.collectors = 0;
            var subnetGrid = host.getSubnetNode().getGrid();
            if (subnetGrid != null) {
                var service = subnetGrid.getService(ISubnetWorkerService.class);
                if (service != null) {
                    this.totalWorkers = service.getTotalWorkerCount(mass);
                    this.idleWorkers = service.getIdleWorkerCount(mass);
                    this.busyWorkers = service.getBusyWorkerCount(mass);
                    this.foreignWorkers = service.getTotalWorkerCount(!mass);
                }
                this.collectors = subnetGrid.getMachines(CollectorBlockEntity.class).size();
            }
            this.priority = host.getPatternPriority();
            this.mainActive = host.getMainNode().isActive();
            this.subnetPowered = host.getSubnetNode().isPowered();
            this.externalEnergyConnected = host.isExternalEnergyConnected();
            this.maxPages = host.getMaxPages();
            this.currentPage = PatternPaging.clampPage(this.currentPage, this.maxPages);
        }

        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) { // Prev Page
            if (this.currentPage > 0) {
                this.currentPage--;
                broadcastChanges();
                return true;
            }
        } else if (id == 1) { // Next Page
            if (this.currentPage < this.maxPages - 1) {
                this.currentPage++;
                broadcastChanges();
                return true;
            }
        } else if (id == 2) { // Toggle individual / mass mode
            host.toggleMassMode();
            broadcastChanges();
            return true;
        } else if (id == 3) { // Cycle the pattern scale (mass mode)
            host.cycleScaleMode();
            broadcastChanges();
            return true;
        }
        return super.clickMenuButton(player, id);
    }


    public MasterPatternProviderBlockEntity getHost() {
        return host;
    }
}