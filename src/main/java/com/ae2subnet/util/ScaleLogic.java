package com.ae2subnet.util;

/** Pure logic of the pattern scaling in mass mode. */
public final class ScaleLogic {

    /** Scale modes of the Master: 0 = off, 1 = auto, 2.. = fixed upper limit from {@link #FIXED_LEVELS}. */
    public static final int MODE_OFF = 0;
    public static final int MODE_AUTO = 1;
    public static final int[] FIXED_LEVELS = {4, 16, 64, 256, 1024};
    public static final int MODE_COUNT = 2 + FIXED_LEVELS.length;
    public static final int MAX_FACTOR = 1024;

    private ScaleLogic() {
    }

    public static int nextMode(int mode) {
        return (mode + 1) % MODE_COUNT;
    }

    /**
     * @param mode     scale mode of the master
     * @param capacity crafts per push that the machines can take at most (0 if unknown)
     * @return how many crafts one scaled pattern stands for (1 = no scaling)
     */
    public static int resolveFactor(int mode, int capacity) {
        if (mode == MODE_OFF || capacity <= 1) {
            return 1;
        }
        int cap = Math.min(capacity, MAX_FACTOR);
        if (mode == MODE_AUTO) {
            return cap;
        }
        int idx = Math.min(Math.max(mode - 2, 0), FIXED_LEVELS.length - 1);
        return Math.max(1, Math.min(FIXED_LEVELS[idx], cap));
    }

    /**
     * Spreads {@code total} crafts as evenly as possible over machines that take at most {@code caps[i]} crafts each.
     *
     * @return the crafts per machine, or {@code null} if the machines together cannot take all of them
     */
    public static int[] distribute(int total, int[] caps) {
        long sum = 0;
        for (int c : caps) {
            sum += Math.max(0, c);
        }
        if (total <= 0 || sum < total) {
            return null;
        }
        int[] result = new int[caps.length];
        int remaining = total;
        // Water filling: hand out one craft at a time to the machine with the least share that still has room
        while (remaining > 0) {
            boolean progressed = false;
            for (int i = 0; i < caps.length && remaining > 0; i++) {
                if (result[i] < caps[i]) {
                    result[i]++;
                    remaining--;
                    progressed = true;
                }
            }
            if (!progressed) {
                return null;
            }
        }
        return result;
    }
}
