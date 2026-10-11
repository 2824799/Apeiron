package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigGodforgeExoticModule;
import com.silvia.apeiron.api.machine.parallel.BigRecipeEnergyProvider;
import com.silvia.apeiron.api.machine.parallel.BigRecipeOutputProvider;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import gregtech.api.enums.Materials;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.util.GTRecipe;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

/** Preserve randomized requests and calculate absolute parallel output without int-sized intermediate stacks. */
@Pseudo
@Mixin(targets = "tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule$1", remap = false)
public abstract class GodforgeExoticRecipeMixin extends ProcessingLogic
    implements BigRecipeOutputProvider, BigRecipeEnergyProvider {

    @Redirect(
        method = "validateRecipe",
        at = @At(value = "FIELD", target = "Lnet/minecraftforge/fluids/FluidStack;amount:I", ordinal = 0),
        require = 1)
    private int apeiron$availableInput(FluidStack input, GTRecipe recipe) {
        if (hasExactRecipeOutputs()) {
            for (FluidStack required : recipe.mFluidInputs)
                if (required != null && input.isFluidEqual(required)) return Math.min(input.amount, required.amount);
        }
        return input.amount;
    }

    @Override
    public boolean hasExactRecipeOutputs() {
        return machine instanceof MTEExoticModule && InfiniteEnergyHatches.find((MTEExoticModule) machine) != null;
    }

    @Override
    public BigInteger getRecipeEnergyMultiplier(GTRecipe recipe) {
        return ((BigGodforgeExoticModule) machine).getExoticRecipeMultiplier();
    }

    @Override
    public List<IAEStack<?>> calculateRecipeOutputsBig(GTRecipe recipe, BigInteger parallels) {
        if (recipe.mFluidOutputs.length == 0 || recipe.mFluidOutputs[0] == null) return Collections.emptyList();
        FluidStack identity = recipe.mFluidOutputs[0].copy();
        identity.amount = 1;
        // A waiting request keeps the output of its original mode even if the GUI mode changes.
        FluidStack magmatter = Materials.MagMatter.getMolten(1);
        BigInteger perNativeParallel = BigInteger
            .valueOf(magmatter != null && identity.isFluidEqual(magmatter) ? 576 : 1000);
        return Collections.singletonList(
            BigAEStackValues.copyWithSize(
                AEFluidStack.create(identity),
                parallels.multiply(getRecipeEnergyMultiplier(recipe))
                    .multiply(perNativeParallel)));
    }
}
