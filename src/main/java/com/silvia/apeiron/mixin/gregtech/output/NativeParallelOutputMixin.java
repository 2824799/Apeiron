package com.silvia.apeiron.mixin.gregtech.output;

import java.math.BigInteger;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.output.NativeRecipeOutputHelper;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;

/** Avoid int chunk expansion while retaining native chance rolls and all parallel/power rules. */
@Mixin(value = ParallelHelper.class, remap = false)
public abstract class NativeParallelOutputMixin implements NativeRecipeOutputHelper {

    @Shadow
    protected GTRecipe recipe;
    @Shadow
    protected int currentParallel;
    @Shadow
    protected double chanceMultiplier;
    @Shadow
    protected ItemStack[] itemOutputs;
    @Shadow
    protected FluidStack[] fluidOutputs;
    @Shadow
    protected Function<Integer, ItemStack[]> customItemOutputCalculation;
    @Shadow
    protected Function<Integer, FluidStack[]> customFluidOutputCalculation;
    @Unique
    private boolean apeiron$exactItems;
    @Unique
    private boolean apeiron$exactFluids;
    @Unique
    private BigMachineOutputQueue apeiron$nativeOutputs;
    @Unique
    private com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider apeiron$fluidProvider;

    @Override
    public void enableExactNativeOutputs(boolean items, boolean fluids) {
        apeiron$exactItems = items;
        apeiron$exactFluids = fluids;
        if (items || fluids) apeiron$nativeOutputs = new BigMachineOutputQueue();
    }

    @Override
    public void setNativeFluidOutputProvider(com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider provider) {
        apeiron$fluidProvider = provider;
    }

    @Override
    public BigMachineOutputQueue getNativeRecipeOutputsBig() {
        return apeiron$nativeOutputs;
    }

    @Inject(method = "calculateItemOutputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$items(ItemStack[] outputs, CallbackInfo ci) {
        if (!apeiron$exactItems || customItemOutputCalculation != null) return;
        for (int i = 0; i < outputs.length; i++) {
            ItemStack type = recipe.getOutput(i);
            if (type == null || type.stackSize <= 0) continue;
            long count = ParallelHelper.calculateIntegralChancedOutputMultiplier(
                (int) (recipe.getOutputChance(i) * chanceMultiplier),
                currentParallel);
            if (count > 0) apeiron$nativeOutputs.addItem(
                type,
                BigInteger.valueOf(count)
                    .multiply(BigInteger.valueOf(type.stackSize)));
        }
        itemOutputs = GTValues.emptyItemStackArray;
        ci.cancel();
    }

    @Inject(method = "calculateFluidOutputs", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$fluids(FluidStack[] outputs, CallbackInfo ci) {
        if (!apeiron$exactFluids || customFluidOutputCalculation != null) return;
        if (apeiron$fluidProvider != null) {
            for (appeng.api.storage.data.IAEStack<?> output : apeiron$fluidProvider
                .calculateRecipeOutputsBig(recipe, BigInteger.valueOf(currentParallel)))
                if (output instanceof appeng.api.storage.data.IAEFluidStack) apeiron$nativeOutputs.addFluid(
                    ((appeng.api.storage.data.IAEFluidStack) output).getFluidStack(),
                    com.silvia.apeiron.ae.stack.BigAEStackValues.get(output));
            fluidOutputs = GTValues.emptyFluidStackArray;
            ci.cancel();
            return;
        }
        for (int i = 0; i < outputs.length; i++) {
            FluidStack type = recipe.getFluidOutput(i);
            if (type == null || type.amount <= 0) continue;
            long count = ParallelHelper.calculateIntegralChancedOutputMultiplier(
                (int) (recipe.getFluidOutputChance(i) * chanceMultiplier),
                currentParallel);
            if (count > 0) apeiron$nativeOutputs.addFluid(
                type,
                BigInteger.valueOf(count)
                    .multiply(BigInteger.valueOf(type.amount)));
        }
        fluidOutputs = GTValues.emptyFluidStackArray;
        ci.cancel();
    }
}
