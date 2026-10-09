package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInputs;
import com.silvia.apeiron.common.machine.spaceelevator.ModuleParallelParameter;
import com.silvia.apeiron.common.machine.spaceelevator.SpaceElevatorRecipeSupport;
import com.silvia.apeiron.math.MiningOutputCounts;
import com.silvia.apeiron.math.RecipeEnergyBudget;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gtnhintergalactic.recipe.IGRecipeMaps;
import gtnhintergalactic.recipe.SpaceMiningData;
import gtnhintergalactic.recipe.SpaceMiningRecipes.WeightedAsteroidList;
import gtnhintergalactic.spaceprojects.ProjectAsteroidOutpost;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;
import tectech.thing.metaTileEntity.multi.base.parameter.BooleanParameter;
import tectech.thing.metaTileEntity.multi.base.parameter.IntegerParameter;

/** Replaces only the ultimate miner's int-bounded recipe loop. */
@Mixin(value = TileEntityModuleMiner.class, remap = false)
public abstract class SpaceElevatorMinerUltimateMixin extends TTMultiblockBase {

    protected SpaceElevatorMinerUltimateMixin(String name) {
        super(name);
    }

    @Shadow(remap = false)
    private IntegerParameter parallelParameter;
    @Shadow(remap = false)
    private IntegerParameter distanceParameter;
    @Shadow(remap = false)
    private IntegerParameter cycleDistanceParameter;
    @Shadow(remap = false)
    private BooleanParameter cycleParameter;
    @Shadow(remap = false)
    protected ProjectAsteroidOutpost asteroidOutpost;
    @Shadow(remap = false)
    protected WeightedAsteroidList prevRecipes;
    @Shadow(remap = false)
    protected int prevDistance;
    @Shadow(remap = false)
    protected int prevAvailDroneMask;
    @Shadow(remap = false)
    public int currentDroneMask;
    @Shadow(remap = false)
    public boolean isWhitelisted;
    @Shadow(remap = false)
    protected HashSet<String> configuredOres;

    @Shadow(remap = false)
    protected abstract int getAvailDroneMask(ItemStack[] inputs);

    @Shadow(remap = false)
    protected abstract int getPlasmaUsageFromTier(int tier);

    @Shadow(remap = false)
    protected abstract int getBonusStackChance(int tier);

    @Shadow(remap = false)
    protected abstract int getRecipeTime(int duration, int tier);

    @Shadow(remap = false)
    protected abstract String getOreString(ItemStack ore);

    @Shadow(remap = false)
    protected abstract long getAvailableData_EM();

    @Inject(method = "process", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$ultimateProcess(ItemStack[] items, FluidStack[] fluids, int plasmaTier, FluidStack plasma,
        int nativeCap, CallbackInfoReturnable<CheckRecipeResult> cir) {
        TileEntityModuleMiner module = (TileEntityModuleMiner) (Object) this;
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(module);
        if (hatch == null || !hatch.isUltimate()) return;
        if (plasma == null || plasmaTier <= 0) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }

        int distance = cycleParameter.getValue() ? cycleDistanceParameter.getValue() : distanceParameter.getValue();
        int droneMask = getAvailDroneMask(items);
        currentDroneMask = droneMask;
        if (prevRecipes == null || prevDistance != distance || prevAvailDroneMask != droneMask) {
            prevRecipes = new WeightedAsteroidList(
                IGRecipeMaps.spaceMiningRecipes.findRecipeQuery()
                    .items(items)
                    .fluids(fluids)
                    .voltage(module.getMaxInputVoltage())
                    .findAll()
                    .filter(recipe -> {
                        if (recipe.getMetadataOrDefault(IGRecipeMaps.MODULE_TIER, 1) > module.getModuleTier())
                            return false;
                        SpaceMiningData data = recipe.getMetadata(IGRecipeMaps.SPACE_MINING_DATA);
                        return data != null && data.minDistance <= distance && data.maxDistance >= distance;
                    })
                    .distinct());
            prevDistance = distance;
            prevAvailDroneMask = droneMask;
        }
        if (prevRecipes.totalWeight == 0) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }

        GTRecipe recipe = prevRecipes.getRandom();
        SpaceMiningData data = recipe == null ? null : recipe.getMetadata(IGRecipeMaps.SPACE_MINING_DATA);
        if (recipe == null || data == null) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }
        if (recipe.mEUt > module.getMaxInputVoltage()) {
            cir.setReturnValue(CheckRecipeResultRegistry.insufficientPower(recipe.mEUt));
            return;
        }

        BigDecimal computationMultiplier = BigDecimal
            .valueOf(asteroidOutpost == null ? 1D : 1D - asteroidOutpost.getComputationDiscount());
        BigDecimal plasmaMultiplier = BigDecimal
            .valueOf(asteroidOutpost == null ? 1D : 1D - asteroidOutpost.getPlasmaDiscount());
        BigInteger computationPerParallel = computationMultiplier.multiply(BigDecimal.valueOf(data.computation))
            .setScale(0, RoundingMode.CEILING)
            .toBigInteger();
        BigInteger plasmaPerParallel = plasmaMultiplier.multiply(BigDecimal.valueOf(getPlasmaUsageFromTier(plasmaTier)))
            .setScale(0, RoundingMode.CEILING)
            .toBigInteger();
        BigInteger parallelLimit = ((ModuleParallelParameter) parallelParameter).getBig();
        BigInteger energyPerParallel = BigInteger.valueOf(recipe.mEUt)
            .multiply(BigInteger.valueOf(recipe.mDuration));
        parallelLimit = parallelLimit.min(RecipeEnergyBudget.affordable(hatch.getAvailableEUBig(), energyPerParallel));
        if (parallelLimit.signum() == 0) {
            cir.setReturnValue(CheckRecipeResultRegistry.insufficientStartupPower(energyPerParallel));
            return;
        }
        if (computationPerParallel.signum() > 0) parallelLimit = parallelLimit.min(
            BigInteger.valueOf(Math.max(0, getAvailableData_EM()))
                .divide(computationPerParallel));

        FluidStack[] combinedFluids = Arrays.copyOf(recipe.mFluidInputs, recipe.mFluidInputs.length + 1);
        FluidStack plasmaInput = plasma.copy();
        plasmaInput.amount = plasmaPerParallel.max(BigInteger.ONE)
            .min(BigInteger.valueOf(Integer.MAX_VALUE))
            .intValueExact();
        combinedFluids[combinedFluids.length - 1] = plasmaInput;
        GTRecipe debitRecipe = new GTRecipe(
            false,
            recipe.mInputs,
            new ItemStack[0],
            null,
            null,
            null,
            null,
            null,
            combinedFluids,
            new FluidStack[0],
            recipe.mDuration,
            recipe.mEUt,
            0);
        MTEMultiBlockBase parent = ((com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule) module)
            .getEnergyParent();
        BigRecipeInputs inputs = new BigRecipeInputs(module, parent, debitRecipe, items, fluids);
        BigInteger parallels = inputs.allocation()
            .maximum(com.silvia.apeiron.api.machine.parallel.ParallelLimit.bounded(parallelLimit));
        if (parallels.signum() <= 0) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }

        BigMachineOutputQueue worstOutputs = new BigMachineOutputQueue();
        BigInteger worstRolls = parallels.multiply(BigInteger.valueOf(data.maxSize));
        for (ItemStack output : recipe.mOutputs) if (apeiron$accept(output))
            worstOutputs.addItem(output, worstRolls.multiply(BigInteger.valueOf(output.stackSize)));
        if (!BigRecipeOutputCapacity.fits(module, worstOutputs.snapshotOutputsUnsorted())) {
            BigInteger lower = BigInteger.ZERO;
            BigInteger upper = parallels;
            while (lower.compareTo(upper) < 0) {
                BigInteger middle = lower.add(upper)
                    .add(BigInteger.ONE)
                    .shiftRight(1);
                BigMachineOutputQueue candidate = new BigMachineOutputQueue();
                BigInteger rolls = middle.multiply(BigInteger.valueOf(data.maxSize));
                for (ItemStack output : recipe.mOutputs) if (apeiron$accept(output))
                    candidate.addItem(output, rolls.multiply(BigInteger.valueOf(output.stackSize)));
                if (BigRecipeOutputCapacity.fits(module, candidate.snapshotOutputsUnsorted())) lower = middle;
                else upper = middle.subtract(BigInteger.ONE);
            }
            parallels = lower;
            if (parallels.signum() <= 0) {
                cir.setReturnValue(CheckRecipeResultRegistry.ITEM_OUTPUT_FULL);
                return;
            }
        }

        BigMachineOutputQueue outputs = new BigMachineOutputQueue();
        BigInteger rolls = parallels.multiply(BigInteger.valueOf(data.minSize));
        BigInteger bonusTrials = parallels.multiply(BigInteger.valueOf(data.maxSize - data.minSize));
        rolls = rolls.add(
            MiningOutputCounts.binomial(
                bonusTrials,
                Math.max(0, Math.min(10000, getBonusStackChance(plasmaTier))),
                10000,
                XSTR.XSTR_INSTANCE));
        int remainingChance = 0;
        for (int i = 0; i < recipe.mOutputs.length; i++) remainingChance += recipe.getOutputChance(i);
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            int chance = recipe.getOutputChance(i);
            if (chance <= 0) continue;
            BigInteger amount = MiningOutputCounts.binomial(rolls, chance, remainingChance, XSTR.XSTR_INSTANCE);
            remainingChance -= chance;
            rolls = rolls.subtract(amount);
            if (apeiron$accept(recipe.mOutputs[i]))
                outputs.addItem(recipe.mOutputs[i], amount.multiply(BigInteger.valueOf(recipe.mOutputs[i].stackSize)));
        }
        inputs.consume(parallels);
        eRequiredData = computationPerParallel.multiply(parallels)
            .longValueExact();
        SpaceElevatorRecipeSupport.start(
            module,
            hatch,
            parallels,
            energyPerParallel.multiply(parallels),
            InfiniteEnergyHatches.targetDuration(module),
            outputs);
        cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }

    private boolean apeiron$accept(ItemStack output) {
        return output != null && (configuredOres == null || configuredOres.isEmpty()
            || isWhitelisted == configuredOres.contains(getOreString(output)));
    }
}
