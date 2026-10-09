package com.ae2subnet.util;

import appeng.api.config.PowerUnit;

/**
 * Pure (Minecraft-free) energy rules shared by the Sub-Pattern Provider and the unit tests.
 */
public final class EnergyLogic {

    /** Machine delivery is halted when the combined network buffer falls below this fraction. */
    public static final double BROWNOUT_THRESHOLD = 0.20;

    private EnergyLogic() {
    }

    /**
     * Max FE per tick for the given number of installed Energy Cards (clamped to 0-4).
     */
    public static int maxTransferRate(int energyCards) {
        return switch (Math.max(0, Math.min(4, energyCards))) {
            case 1 -> 8_000;
            case 2 -> 32_000;
            case 3 -> 128_000;
            case 4 -> 512_000;
            default -> 2_000;
        };
    }

    /**
     * True if no machine delivery is allowed. A network without any storage counts as halted, because there is
     * nothing that could be delivered.
     */
    public static boolean isBrownout(double storedAE, double maxAE) {
        return maxAE <= 0 || (storedAE / maxAE) < BROWNOUT_THRESHOLD;
    }

    /**
     * AE power that may be taken without dropping below {@link #BROWNOUT_THRESHOLD}.
     */
    public static double safeAE(double storedAE, double maxAE) {
        if (isBrownout(storedAE, maxAE)) {
            return 0;
        }
        return Math.max(0, storedAE - maxAE * BROWNOUT_THRESHOLD);
    }

    /**
     * FE that may be delivered right now: bounded by the remaining per-tick budget and by the brownout reserve.
     */
    public static int deliverableFE(int remainingBudgetFE, double storedAE, double maxAE) {
        if (remainingBudgetFE <= 0) {
            return 0;
        }
        double safeFE = Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, safeAE(storedAE, maxAE)));
        return (int) Math.max(0, Math.min(remainingBudgetFE, safeFE));
    }
}
