package com.ae2subnet.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import com.ae2subnet.api.ISubnetWorkerService;
import com.ae2subnet.blockentity.MasterPatternProviderBlockEntity;
import com.ae2subnet.init.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;

public class MasterPatternProviderMenu extends AEBaseMenu {

    private final MasterPatternProviderBlockEntity host;

    @GuiSync(0)
    public int totalWorkers = 0;

    @GuiSync(1)
    public int idleWorkers = 0;

    @GuiSync(2)
    public int busyWorkers = 0;

    @GuiSync(3)
    public int priority = 0;

    public MasterPatternProviderMenu(int id, Inventory playerInventory, MasterPatternProviderBlockEntity host) {
        super(ModMenuTypes.MASTER_PATTERN_PROVIDER.get(), id, playerInventory, host);
        this.host = host;

        this.createPlayerInventorySlots(playerInventory);

        var patternInv = host.getPatternInventory();
        for (int i = 0; i < patternInv.size(); i++) {
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN, patternInv, i),
                    SlotSemantics.ENCODED_PATTERN);
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
        }

        super.broadcastChanges();
    }

    public MasterPatternProviderBlockEntity getHost() {
        return host;
    }
}