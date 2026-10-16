package com.silvia.apeiron.mixin.gregtech.output;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.output.NativeRecipeOutputHelper;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.ExactProcessingContext;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

/** Stage native-powered products only after successful startup; the controller commits them with its energy setup. */
@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class NativeProcessingOutputMixin {

    @Shadow
    protected IVoidable machine;
    @Shadow
    protected long calculatedEut;
    @Shadow
    protected int duration;
    @Unique
    private long apeiron$nativeOutputEUt;
    @Unique
    private BigInteger apeiron$nativeOutputEnergyMultiplier;

    @Inject(
        method = "applyRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/logic/ProcessingLogic;onRecipeStart(Lgregtech/api/util/GTRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private void apeiron$power(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult previous, CallbackInfoReturnable<CheckRecipeResult> cir) {
        // Prepaid modules zero calculatedEut in their startup hook.
        apeiron$nativeOutputEUt = calculatedEut;
        apeiron$nativeOutputEnergyMultiplier = this instanceof com.silvia.apeiron.api.machine.parallel.BigRecipeEnergyProvider
            ? ((com.silvia.apeiron.api.machine.parallel.BigRecipeEnergyProvider) this).getRecipeEnergyMultiplier(recipe)
            : BigInteger.ONE;
    }

    @Inject(method = "applyRecipe", at = @At("RETURN"), require = 1)
    private void apeiron$prepare(GTRecipe recipe, ParallelHelper helper, OverclockCalculator calculator,
        CheckRecipeResult previous, CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!cir.getReturnValue()
            .wasSuccessful() || !(machine instanceof MTEMultiBlockBase)
            || !(machine instanceof BigWirelessController)
            || !(helper instanceof NativeRecipeOutputHelper)) return;
        BigMachineOutputQueue outputs = ((NativeRecipeOutputHelper) helper).getNativeRecipeOutputsBig();
        if (outputs == null || outputs.isEmpty()) return;
        ProcessingLogic logic = (ProcessingLogic) (Object) this;
        // Custom output callbacks and channels with ordinary output ports keep their native calculation.
        // Merge their already-calculated results into the same completion transaction exactly once.
        if (logic.getOutputItems() != null) for (ItemStack stack : logic.getOutputItems())
            if (stack != null && stack.stackSize > 0) outputs.addItem(stack, BigInteger.valueOf(stack.stackSize));
        if (logic.getOutputFluids() != null) for (FluidStack stack : logic.getOutputFluids())
            if (stack != null && stack.amount > 0) outputs.addFluid(stack, BigInteger.valueOf(stack.amount));
        BigInteger parallels = BigInteger.valueOf(helper.getCurrentParallel())
            .multiply(apeiron$nativeOutputEnergyMultiplier);
        BigInteger eut = BigInteger.valueOf(apeiron$nativeOutputEUt)
            .max(BigInteger.ZERO)
            .multiply(apeiron$nativeOutputEnergyMultiplier);
        WirelessRecipeState state = ExactProcessingContext.state((MTEMultiBlockBase) machine);
        state.prepare(
            ticks -> state.startNativePowered(parallels, eut.multiply(BigInteger.valueOf(ticks)), ticks, outputs),
            duration);
        logic.overwriteOutputItems(new ItemStack[0])
            .overwriteOutputFluids(new FluidStack[0]);
    }
}
