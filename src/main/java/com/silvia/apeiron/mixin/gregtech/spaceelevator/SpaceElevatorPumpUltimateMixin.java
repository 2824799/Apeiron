package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import java.math.BigInteger;

import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.common.machine.spaceelevator.ModuleParallelParameter;
import com.silvia.apeiron.common.machine.spaceelevator.SpaceElevatorRecipeSupport;
import com.silvia.apeiron.math.RecipeEnergyBudget;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gtnhintergalactic.recipe.SpacePumpingRecipes;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModulePump;
import tectech.thing.metaTileEntity.multi.base.parameter.IntegerParameter;

/** Uses the exact wireless output ledger only for the ultimate elevator pump path. */
@Mixin(value = TileEntityModulePump.class, remap = false)
public abstract class SpaceElevatorPumpUltimateMixin {

    @Shadow(remap = false)
    private IntegerParameter batchParameter;
    @Shadow(remap = false)
    private IntegerParameter[] planetTypeParameters;
    @Shadow(remap = false)
    private IntegerParameter[] gasTypeParameters;
    @Shadow(remap = false)
    private IntegerParameter[] parallelParameters;

    @Inject(method = "checkProcessing_EM", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$ultimate(CallbackInfoReturnable<CheckRecipeResult> cir) {
        TileEntityModulePump module = (TileEntityModulePump) (Object) this;
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(module);
        if (hatch == null || !hatch.isUltimate()) return;

        int batch = Math.max(1, Math.min(128, batchParameter.getValue()));
        int nativeDuration = 20 * batch;
        BigInteger unitEUt = BigInteger.valueOf(TileEntityModulePump.ENERGY_CONSUMPTION);
        BigInteger energyPerParallel = unitEUt.multiply(BigInteger.valueOf(nativeDuration));
        BigInteger remaining = RecipeEnergyBudget.affordable(hatch.getAvailableEUBig(), energyPerParallel);
        BigInteger total = BigInteger.ZERO;
        BigMachineOutputQueue outputs = new BigMachineOutputQueue();

        for (int i = 0; i < module.getParallelRecipes(); i++) {
            FluidStack fluid = SpacePumpingRecipes.RECIPES
                .get(Pair.of(planetTypeParameters[i].getValue(), gasTypeParameters[i].getValue()));
            if (fluid == null || remaining.signum() == 0) continue;
            BigInteger count = ((ModuleParallelParameter) parallelParameters[i]).getBig()
                .min(remaining);
            BigInteger perParallel = BigInteger.valueOf(fluid.amount)
                .multiply(BigInteger.valueOf(batch));
            count = fit(module, outputs, fluid, perParallel, count);
            if (count.signum() == 0) continue;
            outputs.addFluid(fluid, perParallel.multiply(count));
            total = total.add(count);
            remaining = remaining.subtract(count);
        }

        if (total.signum() == 0) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }
        SpaceElevatorRecipeSupport.start(
            module,
            hatch,
            total,
            energyPerParallel.multiply(total),
            InfiniteEnergyHatches.targetDuration(module),
            outputs);
        cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }

    private static BigInteger fit(MTEMultiBlockBase module, BigMachineOutputQueue current, FluidStack fluid,
        BigInteger perParallel, BigInteger upper) {
        if (fits(module, current, fluid, perParallel, upper)) return upper;
        BigInteger lower = BigInteger.ZERO;
        while (lower.compareTo(upper) < 0) {
            BigInteger middle = lower.add(upper)
                .add(BigInteger.ONE)
                .shiftRight(1);
            if (fits(module, current, fluid, perParallel, middle)) lower = middle;
            else upper = middle.subtract(BigInteger.ONE);
        }
        return lower;
    }

    private static boolean fits(MTEMultiBlockBase module, BigMachineOutputQueue current, FluidStack fluid,
        BigInteger perParallel, BigInteger count) {
        BigMachineOutputQueue candidate = new BigMachineOutputQueue();
        for (appeng.api.storage.data.IAEStack<?> output : current.snapshotOutputs()) {
            if (output instanceof appeng.api.storage.data.IAEFluidStack) candidate.addFluid(
                ((appeng.api.storage.data.IAEFluidStack) output).getFluidStack(),
                BigAEStackValues.get(output));
            else candidate
                .addItem(((appeng.api.storage.data.IAEItemStack) output).getItemStack(), BigAEStackValues.get(output));
        }
        candidate.addFluid(fluid, perParallel.multiply(count));
        return BigRecipeOutputCapacity.fits(module, candidate.snapshotOutputs());
    }
}
