package com.silvia.apeiron.mixin.gregtech.lanthanides;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.input.IsolatedRecipeInputs;
import com.silvia.apeiron.common.machine.input.IsolatedRecipeInputs.Candidate;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gtnhlanth.common.tileentity.MTESourceChamber;

/** Native source-chamber recipes also bypass ProcessingLogic's isolated-input iteration. */
@Mixin(value = MTESourceChamber.class, remap = false)
public abstract class SourceChamberInputMixin {

    @Unique
    private Candidate apeiron$input;

    @Inject(method = "checkProcessing", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$patterns(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (apeiron$input != null) return;
        MTESourceChamber machine = (MTESourceChamber) (Object) this;
        List<Candidate> inputs = IsolatedRecipeInputs.candidates(machine.mDualInputHatches);
        if (inputs.isEmpty()) return;
        Candidate ordinary = new Candidate();
        ordinary.items.addAll(machine.getStoredInputs());
        ordinary.fluids.addAll(machine.getStoredFluids());
        inputs.add(ordinary);
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;
        try {
            for (Candidate candidate : inputs) {
                apeiron$input = candidate;
                CheckRecipeResult found = machine.checkProcessing();
                if (found.wasSuccessful()) {
                    cir.setReturnValue(found);
                    return;
                }
                if (found != CheckRecipeResultRegistry.NO_RECIPE) result = found;
            }
            cir.setReturnValue(result);
        } finally {
            apeiron$input = null;
        }
    }

    @Redirect(
        method = "checkProcessing",
        at = @At(
            value = "INVOKE",
            target = "Lgtnhlanth/common/tileentity/MTESourceChamber;getStoredInputs()Ljava/util/ArrayList;"),
        require = 1)
    private ArrayList<ItemStack> apeiron$items(MTESourceChamber machine) {
        return apeiron$input == null ? machine.getStoredInputs() : new ArrayList<>(apeiron$input.items);
    }

    @Redirect(
        method = "checkProcessing",
        at = @At(
            value = "INVOKE",
            target = "Lgtnhlanth/common/tileentity/MTESourceChamber;getStoredFluids()Ljava/util/ArrayList;"),
        require = 1)
    private ArrayList<FluidStack> apeiron$fluids(MTESourceChamber machine) {
        return apeiron$input == null ? machine.getStoredFluids() : new ArrayList<>(apeiron$input.fluids);
    }
}
