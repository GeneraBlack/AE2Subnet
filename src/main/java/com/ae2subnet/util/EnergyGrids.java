package com.ae2subnet.util;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.EnergyService;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Helpers for AE2 energy services.
 * <p>
 * {@code getStoredPower()} / {@code getMaxStoredPower()} only describe a <b>single</b> grid, while
 * {@code extractAEPower()}, {@code injectPower()} and {@code getEnergyDemand()} operate on the whole energy overlay
 * (main network + subnet, linked by the Master Pattern Provider). Anything that wants to protect the buffer that is
 * actually drained has to look at the overlay totals.
 */
public final class EnergyGrids {

    public record Totals(double stored, double max) {
    }

    private EnergyGrids() {
    }

    public static Totals overlayTotals(IEnergyService service) {
        if (!(service instanceof EnergyService start)) {
            return new Totals(service.getStoredPower(), service.getMaxStoredPower());
        }

        Set<EnergyService> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var todo = new ArrayDeque<EnergyService>();
        todo.add(start);

        double stored = 0;
        double max = 0;
        while (!todo.isEmpty()) {
            var current = todo.poll();
            if (!visited.add(current)) {
                continue;
            }
            stored += current.getStoredPower();
            max += current.getMaxStoredPower();
            for (var connection : current.getOverlayGridConnections()) {
                todo.addAll(connection.connectedEnergyServices());
            }
        }
        return new Totals(stored, max);
    }
}
