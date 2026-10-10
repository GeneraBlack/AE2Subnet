package com.ae2subnet.api;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

public interface IMasterPatternProvider {
    /**
     * Accept a returned item/fluid from a sub-provider and route it back to the network.
     */
    void acceptReturn(GenericStack stack);

    default void acceptReturn(AEKey key, long amount) {
        if (key != null && amount > 0) {
            acceptReturn(new GenericStack(key, amount));
        }
    }

    /**
     * True if this master runs its subnet in mass mode (Loadbalancer + Collector) instead of individual mode
     * (Sub-Pattern Providers).
     */
    default boolean isMassMode() {
        return false;
    }
}