package com.silvia.apeiron.mixin.gregtech.lanthanides;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.me.input.BigDualInputHatch;
import com.silvia.apeiron.common.integration.lanthanides.PatternParticleInputs;
import com.silvia.apeiron.common.integration.lanthanides.TargetChamberEnhancement;
import com.silvia.apeiron.common.machine.input.IsolatedRecipeInputs;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gtnhlanth.common.item.ItemPhotolithographicMask;
import gtnhlanth.common.tileentity.MTETargetChamber;

@Mixin(value = MTETargetChamber.class, remap = false)
public abstract class TargetChamberInputMixin {

    @Shadow
    @Final
    private ArrayList<MTEHatchInputBus> mMaskInputBusses;
    @Shadow
    private int lastTCRecipeInputParticle;

    @Unique
    private List<ItemStack> apeiron$items;
    @Unique
    private List<ItemStack> apeiron$masks;

    @Inject(method = "checkProcessing", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$isolatedInputs(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (apeiron$items != null) return;
        MTETargetChamber machine = (MTETargetChamber) (Object) this;
        boolean itemParticles = PatternParticleInputs.hasPatternInput(machine.mDualInputHatches)
            || PatternParticleInputs.hasPatternInput(mMaskInputBusses);
        List<List<ItemStack>> inputs = IsolatedRecipeInputs.patterns(machine.mDualInputHatches);
        boolean hasSpecialPatterns = mMaskInputBusses.stream()
            .anyMatch(bus -> bus instanceof BigDualInputHatch);
        if (!itemParticles && inputs.isEmpty() && !hasSpecialPatterns) return;
        List<ItemStack> ordinaryInputs = machine.getStoredInputs();
        inputs.add(ordinaryInputs);
        List<List<ItemStack>> masks = IsolatedRecipeInputs.specialBusses(mMaskInputBusses);
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;
        try {
            for (List<ItemStack> input : inputs) {
                List<ItemStack> materials = new ArrayList<>();
                List<ItemStack> programmedMasks = new ArrayList<>();
                // In item-particle mode a pattern buffer supplies its own masks, including consumable GT masks.
                // Ordinary busses retain the native mask position; never borrow from another material buffer.
                for (ItemStack stack : input) {
                    if (stack.getItem() instanceof ItemPhotolithographicMask
                        && (stack.stackSize == 0 || itemParticles && input != ordinaryInputs))
                        programmedMasks.add(stack);
                    else materials.add(stack);
                }
                for (List<ItemStack> mask : masks) {
                    apeiron$items = materials;
                    apeiron$masks = new ArrayList<>(mask);
                    apeiron$masks.addAll(programmedMasks);
                    CheckRecipeResult found;
                    if (itemParticles) {
                        if (mask.stream()
                            .anyMatch(stack -> !(stack.getItem() instanceof ItemPhotolithographicMask))
                            || materials.stream()
                                .anyMatch(stack -> stack.getItem() instanceof ItemPhotolithographicMask))
                            continue;
                        List<ItemStack> all = new ArrayList<>(apeiron$masks);
                        all.addAll(materials);
                        found = TargetChamberEnhancement.processParticleItems(machine, all.toArray(new ItemStack[0]));
                        if (found.wasSuccessful()) lastTCRecipeInputParticle = -1;
                    } else {
                        // Native particle checks, mask position, consumption and progression are retained.
                        found = machine.checkProcessing();
                    }
                    if (found.wasSuccessful()) {
                        cir.setReturnValue(found);
                        return;
                    }
                    if (found != CheckRecipeResultRegistry.NO_RECIPE) result = found;
                }
            }
            cir.setReturnValue(result);
        } finally {
            apeiron$items = null;
            apeiron$masks = null;
        }
    }

    @Redirect(
        method = "checkProcessing",
        at = @At(
            value = "INVOKE",
            target = "Lgtnhlanth/common/tileentity/MTETargetChamber;getStoredInputs()Ljava/util/ArrayList;"),
        require = 1)
    private ArrayList<ItemStack> apeiron$items(MTETargetChamber machine) {
        return apeiron$items == null ? machine.getStoredInputs() : new ArrayList<>(apeiron$items);
    }

    @Inject(method = "getMaskItemStack", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$masks(CallbackInfoReturnable<ArrayList<ItemStack>> cir) {
        if (apeiron$masks != null) cir.setReturnValue(new ArrayList<>(apeiron$masks));
    }

    @Inject(method = "startRecipeProcessing", at = @At("RETURN"), require = 1)
    private void apeiron$beginMaskPatterns(CallbackInfo ci) {
        apeiron$maskSources(true);
    }

    @Inject(method = "endRecipeProcessing", at = @At("HEAD"), require = 1)
    private void apeiron$endMaskPatterns(CallbackInfo ci) {
        // Commit both input roles together; the native per-bus callbacks are then idempotent.
        MTETargetChamber machine = (MTETargetChamber) (Object) this;
        java.util.Set<com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic> stocks = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<>());
        List<Object> hatches = new ArrayList<>(mMaskInputBusses);
        hatches.addAll(machine.mInputBusses);
        hatches.addAll(machine.mInputHatches);
        hatches.addAll(machine.mDualInputHatches);
        for (Object hatch : hatches)
            if (hatch instanceof com.silvia.apeiron.common.machine.me.stocking.StockingInputHost) stocks
                .add(((com.silvia.apeiron.common.machine.me.stocking.StockingInputHost) hatch).getStockingInput());
        CheckRecipeResult result = com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic.finishGroup(stocks);
        machine.setResultIfFailure(result);
        if (!result.wasSuccessful()) {
            ((com.silvia.apeiron.api.machine.parallel.BigWirelessController) machine).getWirelessRecipeState()
                .cancelRecipe();
            machine.mMaxProgresstime = 0;
            machine.lEUt = 0;
            machine.mOutputItems = null;
            machine.mOutputFluids = null;
        }
        apeiron$maskSources(false);
    }

    @Unique
    private void apeiron$maskSources(boolean begin) {
        for (MTEHatchInputBus bus : mMaskInputBusses) if (bus instanceof BigDualInputHatch) {
            MTEInfinitePatternInputAssembly source = ((BigDualInputHatch) bus).getInputSource();
            if (source != null) {
                if (begin) source.beginRecipeProcessing();
                else source.endRecipeProcessing();
            }
        }
    }
}
