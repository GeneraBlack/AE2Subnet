package com.ae2subnet.util;

/**
 * Pure (Minecraft-free) rules for the Sub-Pattern Provider batch mode: how many crafts a single provider may
 * keep in flight at the same time.
 */
public final class BatchLogic {

    /** Hard cap for crafts in flight per provider. */
    public static final int MAX_BATCH = 64;

    /** Cycle order of the GUI button. 0 means "Auto" (bounded by what the machine accepts, up to {@link #MAX_BATCH}). */
    private static final int[] STEPS = {0, 1, 2, 4, 8, 16, 32, 64};

    private BatchLogic() {
    }

    /** Effective limit for a configured value (0 = auto). */
    public static int effectiveLimit(int configured) {
        return configured <= 0 ? MAX_BATCH : Math.min(configured, MAX_BATCH);
    }

    /** True while another craft may be dispatched to a provider that already has {@code inFlight} crafts running. */
    public static boolean hasCapacity(int inFlight, int configured) {
        return inFlight < effectiveLimit(configured);
    }

    /** Next configured value in the GUI cycle; unknown values restart at Auto. */
    public static int nextLimit(int configured) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == configured) {
                return STEPS[(i + 1) % STEPS.length];
            }
        }
        return STEPS[0];
    }

    /** Normalizes arbitrary stored values to a valid configured value. */
    public static int sanitize(int configured) {
        for (int step : STEPS) {
            if (step == configured) {
                return configured;
            }
        }
        return 0;
    }

    public static String label(int configured) {
        return configured <= 0 ? "Auto" : Integer.toString(configured);
    }
}
