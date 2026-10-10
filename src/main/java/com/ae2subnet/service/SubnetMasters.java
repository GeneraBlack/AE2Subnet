package com.ae2subnet.service;

import appeng.api.networking.IGrid;
import com.ae2subnet.blockentity.MasterPatternProviderBlockEntity;
import org.jetbrains.annotations.Nullable;

/** Finds the Master Pattern Provider that owns a subnet grid. */
public final class SubnetMasters {

    private SubnetMasters() {
    }

    @Nullable
    public static MasterPatternProviderBlockEntity find(@Nullable IGrid subnetGrid) {
        if (subnetGrid == null) {
            return null;
        }
        for (var master : subnetGrid.getMachines(MasterPatternProviderBlockEntity.class)) {
            return master;
        }
        return null;
    }
}
