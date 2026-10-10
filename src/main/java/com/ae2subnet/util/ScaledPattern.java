package com.ae2subnet.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A pattern that stands for {@code factor} crafts of the wrapped pattern. The crafting CPU plans and pushes it like any
 * other pattern, so a single push carries the inputs of {@code factor} crafts and its outputs are {@code factor} times
 * as large. Equality is defined by the wrapped pattern and the factor.
 */
public final class ScaledPattern implements IPatternDetails {

    private final IPatternDetails base;
    private final int factor;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    public ScaledPattern(IPatternDetails base, int factor) {
        if (factor < 1) {
            throw new IllegalArgumentException("factor must be >= 1");
        }
        this.base = base;
        this.factor = factor;

        var baseInputs = base.getInputs();
        this.inputs = new IInput[baseInputs.length];
        for (int i = 0; i < baseInputs.length; i++) {
            this.inputs[i] = new ScaledInput(baseInputs[i], factor);
        }

        var baseOutputs = base.getOutputs();
        this.outputs = new ArrayList<>(baseOutputs.size());
        for (var out : baseOutputs) {
            this.outputs.add(new GenericStack(out.what(), out.amount() * factor));
        }
    }

    public IPatternDetails getBase() {
        return base;
    }

    public int getFactor() {
        return factor;
    }

    /** The pattern behind {@code details}, which is the pattern itself if it is not scaled. */
    public static IPatternDetails unwrap(IPatternDetails details) {
        return details instanceof ScaledPattern scaled ? scaled.base : details;
    }

    /** How many crafts of the underlying pattern one push of {@code details} stands for. */
    public static int factorOf(IPatternDetails details) {
        return details instanceof ScaledPattern scaled ? scaled.factor : 1;
    }

    @Override
    public AEItemKey getDefinition() {
        return base.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return base.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        base.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return base.getTooltip(level, flags);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScaledPattern other && other.factor == factor && other.base.equals(base);
    }

    @Override
    public int hashCode() {
        return 31 * base.hashCode() + factor;
    }

    private record ScaledInput(IInput base, long factor) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return base.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return base.getMultiplier() * factor;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return base.isValid(input, level);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return base.getRemainingKey(template);
        }
    }
}
