package com.ae2subnet.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the crafts a provider has pushed into its machine and has not received an output for yet (FIFO).
 * <p>
 * Every craft registers the primary output it is expected to produce. Returned outputs are matched against these
 * expectations; once an entry has received its full amount the craft counts as completed. Entries without a key
 * (wildcards, e.g. restored after a world reload) complete on any returned output.
 * <p>
 * Accounting is deliberately forgiving: an output that matches no expectation still completes the oldest craft, so a
 * mismatching output key can never block a provider forever. Over-dispatching is bounded anyway because a push is only
 * accepted if the machine can take all inputs.
 *
 * @param <K> output key type (AEKey in the mod, anything in tests)
 */
public final class BatchTracker<K> {

    private static final class Entry<K> {
        final K key;
        long remaining;

        Entry(K key, long remaining) {
            this.key = key;
            this.remaining = remaining;
        }
    }

    private final List<Entry<K>> entries = new ArrayList<>();

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    /** Registers one craft expecting {@code amount} of {@code key}. A null key or non-positive amount makes a wildcard. */
    public void add(K key, long amount) {
        if (key == null || amount <= 0) {
            entries.add(new Entry<>(null, 0));
        } else {
            entries.add(new Entry<>(key, amount));
        }
    }

    public void addWildcards(int count) {
        for (int i = 0; i < count; i++) {
            entries.add(new Entry<>(null, 0));
        }
    }

    /** True if some pending craft expects exactly this output. */
    public boolean expects(K key) {
        for (var entry : entries) {
            if (entry.key != null && Objects.equals(entry.key, key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Books an output delivered by someone else (e.g. a collector) strictly against matching expectations. Unlike
     * {@link #returned} nothing happens if no pending craft expects this key.
     *
     * @return the part of {@code amount} that was booked here
     */
    public long creditExpected(K key, long amount) {
        if (amount <= 0 || key == null) {
            return 0;
        }
        long left = amount;
        for (int i = 0; i < entries.size() && left > 0; ) {
            var entry = entries.get(i);
            if (entry.key != null && Objects.equals(entry.key, key)) {
                long take = Math.min(entry.remaining, left);
                entry.remaining -= take;
                left -= take;
                if (entry.remaining <= 0) {
                    entries.remove(i);
                    continue;
                }
            }
            i++;
        }
        long booked = amount - left;
        if (booked == 0) {
            // Crafts restored from disk have no expectation: any delivery completes one of them
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).key == null) {
                    entries.remove(i);
                    return amount;
                }
            }
        }
        return booked;
    }

    /**
     * Books a returned output.
     *
     * @return number of crafts that are completed by it
     */
    public int returned(K key, long amount) {
        if (amount <= 0 || entries.isEmpty()) {
            return 0;
        }

        int completed = 0;
        boolean matched = false;
        long left = amount;

        for (int i = 0; i < entries.size() && left > 0; ) {
            var entry = entries.get(i);
            if (entry.key != null && Objects.equals(entry.key, key)) {
                matched = true;
                long take = Math.min(entry.remaining, left);
                entry.remaining -= take;
                left -= take;
                if (entry.remaining <= 0) {
                    entries.remove(i);
                    completed++;
                    continue;
                }
            }
            i++;
        }

        if (!matched) {
            // Unknown output (wildcard restore, byproduct or key mismatch): complete the oldest craft
            entries.remove(0);
            completed++;
        }
        return completed;
    }
}
