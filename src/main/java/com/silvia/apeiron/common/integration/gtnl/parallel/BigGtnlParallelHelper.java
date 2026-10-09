package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase;
import com.science.gtnl.config.MainConfig;
import com.science.gtnl.utils.enums.ModList;
import com.science.gtnl.utils.recipes.ChanceBonusManager;
import com.science.gtnl.utils.recipes.GTNLParallelHelper;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.energy.BigWirelessEnergySource;
import com.silvia.apeiron.api.machine.gtnl.BigGtnlWirelessMachine;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.api.machine.parallel.PreparedWirelessRecipe;
import com.silvia.apeiron.common.machine.energy.DirectWirelessEnergySource;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.common.machine.output.NativeOutputBatches;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInputs;
import com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.ExactOverclock;
import com.silvia.apeiron.math.RecipeOutputCounts;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** GTNL retains lookup, validation and startup hooks; the common ledgers own counts, inputs and outputs. */
public final class BigGtnlParallelHelper extends GTNLParallelHelper implements PreparedWirelessRecipe {

    private static final GTNLParallelHelper DEFAULTS = new GTNLParallelHelper();
    private final MTEMultiBlockBase controller;
    private final GTRecipe originalRecipe;
    private final WirelessRecipeState state;
    private final GtnlRecipeBatch batch;
    private final MTEInfiniteEnergyHatch hatch;
    private BigRecipeInputs inputs;
    private BigInteger parallels = BigInteger.ZERO;
    private BigInteger eut = BigInteger.ZERO;
    private BigInteger totalEnergy = BigInteger.ZERO;
    private BigInteger normalMaximum;
    private int duration;
    private boolean committed;
    private java.util.function.ToDoubleFunction<BigGtnlParallelHelper> durationAdjustment;
    private BigInteger batchMaximum;

    public static GTNLParallelHelper adapt(GTNLParallelHelper nativeHelper) {
        if (!(nativeHelper.machine instanceof MTEMultiBlockBase)
            || !BigRecipeParallelHelper.supports(nativeHelper.recipe)
            || nativeHelper.getClass() != GTNLParallelHelper.class && !nativeHelper.getClass()
                .getName()
                .equals("com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram$TreeDiagramParallelHelper")
            || nativeHelper.customItemOutputCalculation != null
            || nativeHelper.customFluidOutputCalculation != null
            || nativeHelper.maxParallelCalculator != DEFAULTS.maxParallelCalculator
            || nativeHelper.inputConsumer != DEFAULTS.inputConsumer) return nativeHelper;
        MTEMultiBlockBase machine = (MTEMultiBlockBase) nativeHelper.machine;
        boolean wireless = machine instanceof WirelessEnergyMultiMachineBase
            && ((WirelessEnergyMultiMachineBase<?>) machine).wirelessMode;
        if (!(machine instanceof WirelessEnergyMultiMachineBase) && !wireless
            && InfiniteEnergyHatches.find(machine) == null
            && !NativeOutputBatches.hasExactItems(machine)
            && !NativeOutputBatches.hasExactFluids(machine)) return nativeHelper;
        return new BigGtnlParallelHelper(machine, nativeHelper);
    }

    private BigGtnlParallelHelper(MTEMultiBlockBase controller, GTNLParallelHelper source) {
        this.controller = controller;
        state = ((BigWirelessController) controller).getWirelessRecipeState();
        batch = controller instanceof BigGtnlWirelessMachine
            && ((BigGtnlWirelessMachine) controller).getGtnlRecipeBatch()
                .isActive() ? ((BigGtnlWirelessMachine) controller).getGtnlRecipeBatch() : null;
        hatch = InfiniteEnergyHatches.find(controller);
        machine = source.machine;
        recipe = originalRecipe = source.recipe;
        itemInputs = source.itemInputs == null ? new ItemStack[0] : source.itemInputs;
        fluidInputs = source.fluidInputs == null ? new FluidStack[0] : source.fluidInputs;
        maxParallel = source.maxParallel;
        availableEUt = source.availableEUt;
        eutModifier = source.eutModifier;
        chanceMultiplier = source.chanceMultiplier;
        batchMode = source.batchMode;
        batchModifier = source.batchModifier;
        consume = source.consume;
        calculateOutputs = source.calculateOutputs;
        protectExcessItem = source.protectExcessItem;
        protectExcessFluid = source.protectExcessFluid;
        singleRecipeMachine = source.singleRecipeMachine;
        isRecipeLocked = source.isRecipeLocked;
    }

    @Override
    public void determineParallel() {
        if (!ModList.Overpowered.isModLoaded() && MainConfig.machine.enableRecipeOutputChance) {
            OptionalDouble bonus = ChanceBonusManager
                .getChanceBonusOptional(machine, GTUtility.getTier(recipe.mEUt), chanceMultiplier, recipe);
            if (bonus.isPresent()) recipe = ChanceBonusManager.copyAndBonusChance(recipe, bonus.getAsDouble());
        }
        if (hatch == null && !com.silvia.apeiron.compat.OverclockPolicies.allows(calculator, recipe.mEUt)) {
            result = CheckRecipeResultRegistry.insufficientVoltage(recipe.mEUt);
            return;
        }
        if (maxParallel < 1 && hatch == null) {
            result = CheckRecipeResultRegistry.NO_RECIPE;
            return;
        }
        inputs = new BigRecipeInputs(controller, recipe, itemInputs, fluidInputs);
        ParallelLimit limit;
        if (hatch != null) {
            limit = batch == null ? state.getLimit() : batch.limit(state, originalRecipe, consumesResources());
        } else {
            // Native settings bound the base lanes; GTNL deliberately adds sub-tick and batch parallelism.
            normalMaximum = GtnlOverclocks.maximum(calculator, maxParallel)
                .min(BigInteger.valueOf(Integer.MAX_VALUE));
            BigInteger power = BigInteger.valueOf(calculator.machineVoltage)
                .multiply(BigInteger.valueOf(calculator.machineAmperage));
            // A specialized helper can supply its own power budget. Recover overflowing standard products exactly.
            if (availableEUt >= 0 && availableEUt != power.longValue()) power = BigInteger.valueOf(availableEUt);
            BigInteger perRecipe = ExactOverclock.ceil(
                ExactOverclock
                    .power(recipe.mEUt, BigInteger.ONE, eutModifier, calculator.calculateHeatDiscountMultiplier()));
            if (perRecipe.signum() > 0) normalMaximum = normalMaximum.min(power.divide(perRecipe));
            limit = ParallelLimit.bounded(
                normalMaximum.multiply(BigInteger.valueOf(batchMode ? Math.max(1, batchModifier) : 1))
                    .min(BigInteger.valueOf(Integer.MAX_VALUE)));
        }
        BigInteger available = availableEnergy();
        if (isUltimate() && recipe.mEUt > 0 && recipe.mDuration > 0) {
            BigInteger perRecipe = BigInteger.valueOf(recipe.mEUt)
                .multiply(BigInteger.valueOf(recipe.mDuration));
            limit = ParallelLimit.bounded(limit.applyTo(available.divide(perRecipe)));
        }
        parallels = inputs.allocation()
            .maximum(limit);
        if (parallels.signum() == 0) {
            result = CheckRecipeResultRegistry.NO_RECIPE;
            return;
        }
        if (!fits(parallels)) {
            BigInteger lower = BigInteger.ZERO, upper = parallels;
            while (lower.compareTo(upper) < 0) {
                BigInteger candidate = lower.add(upper)
                    .add(BigInteger.ONE)
                    .shiftRight(1);
                if (fits(candidate)) lower = candidate;
                else upper = candidate.subtract(BigInteger.ONE);
            }
            parallels = lower;
            if (parallels.signum() == 0) {
                result = check(BigInteger.ONE);
                return;
            }
        }
        calculateEnergy(parallels);
        currentParallel = parallels.min(BigInteger.valueOf(Integer.MAX_VALUE))
            .intValue();
        durationMultiplier = 1;
        itemOutputs = new ItemStack[0];
        fluidOutputs = new FluidStack[0];
        result = CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private boolean isUltimate() {
        return hatch != null && hatch.isUltimate();
    }

    private BigInteger availableEnergy() {
        if (hatch != null) return hatch.getAvailableEUBig()
            .subtract(batch == null ? BigInteger.ZERO : batch.getTotalEnergy())
            .max(BigInteger.ZERO);
        if (batch == null) return BigInteger.valueOf(Long.MAX_VALUE);
        return source().getAvailableEUBig();
    }

    private BigWirelessEnergySource source() {
        return hatch != null ? hatch
            : new DirectWirelessEnergySource(((WirelessEnergyMultiMachineBase<?>) controller).ownerUUID);
    }

    private void calculateEnergy(BigInteger count) {
        if (isUltimate()) {
            duration = state.getTargetDuration();
            totalEnergy = BigInteger.valueOf(recipe.mEUt)
                .multiply(BigInteger.valueOf(recipe.mDuration))
                .multiply(count);
            eut = totalEnergy.add(BigInteger.valueOf(duration - 1L))
                .divide(BigInteger.valueOf(duration));
            return;
        }
        if (hatch != null) {
            calculator.machineVoltage = state.getVoltageSetting();
            calculator.machineAmperage = 1;
            calculator.amperageOC = false;
        }
        BigInteger normal = hatch != null ? count : count.min(normalMaximum);
        BigInteger lanes = hatch != null ? BigInteger.ONE : normal.min(BigInteger.valueOf(maxParallel));
        GtnlOverclocks oc = GtnlOverclocks.calculate(calculator, lanes, hatch != null);
        duration = oc.duration;
        eut = hatch != null ? oc.eut.multiply(count) : oc.eut;
        batchMaximum = normal;
        if (hatch == null && batchMode && oc.duration < 128) {
            BigDecimal multiplier = BigDecimal.valueOf(128)
                .divide(BigDecimal.valueOf(oc.duration), java.math.MathContext.DECIMAL128)
                .min(BigDecimal.valueOf(Math.max(1, batchModifier)));
            batchMaximum = new BigDecimal(normal).multiply(multiplier)
                .toBigInteger();
        }
        if (hatch == null && count.compareTo(normal) > 0) {
            duration = new BigDecimal(count).multiply(BigDecimal.valueOf(duration))
                .divide(new BigDecimal(normal), 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(Integer.MAX_VALUE))
                .intValueExact();
        }
        calculator.calculated = true;
        calculator.calculatedDuration = duration;
        calculator.calculatedConsumption = eut.min(BigInteger.valueOf(Long.MAX_VALUE - 1))
            .longValue();
        durationMultiplier = 1;
        if (durationAdjustment != null) {
            double adjusted = durationAdjustment.applyAsDouble(this);
            duration = !Double.isFinite(adjusted) || adjusted >= Integer.MAX_VALUE ? Integer.MAX_VALUE
                : Math.max(1, (int) adjusted);
        }
        totalEnergy = eut.multiply(BigInteger.valueOf(duration));
    }

    private boolean fits(BigInteger count) {
        return check(count).wasSuccessful();
    }

    private CheckRecipeResult check(BigInteger count) {
        calculateEnergy(count);
        if (hatch == null && count.compareTo(batchMaximum) > 0) return CheckRecipeResultRegistry.NO_RECIPE;
        if (duration >= Integer.MAX_VALUE || batch != null && !batch.canAppend(duration, isUltimate()))
            return CheckRecipeResultRegistry.DURATION_OVERFLOW;
        if (hatch == null && batch == null && eut.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0)
            return CheckRecipeResultRegistry.POWER_OVERFLOW;
        if ((hatch != null || batch != null) && totalEnergy.compareTo(availableEnergy()) > 0)
            return CheckRecipeResultRegistry.insufficientPower(Long.MAX_VALUE);
        List<IAEStack<?>> expected = new ArrayList<>();
        if (batch != null) expected.addAll(
            batch.outputs()
                .snapshotOutputsUnsorted());
        expected.addAll(outputs(count, true));
        return BigRecipeOutputCapacity.check(controller, expected);
    }

    private int chance(int value) {
        return BigDecimal.valueOf(value)
            .multiply(ExactOverclock.decimal(chanceMultiplier))
            .min(BigDecimal.valueOf(Integer.MAX_VALUE))
            .intValue();
    }

    private List<IAEStack<?>> outputs(BigInteger count, boolean capacity) {
        List<IAEStack<?>> values = new ArrayList<>();
        GtnlOutputEffects effects = new GtnlOutputEffects(controller, count, capacity);
        for (int i = 0; i < Math.min(recipe.mOutputs.length, controller.getItemOutputLimit()); i++) {
            ItemStack type = recipe.mOutputs[i];
            if (type == null) continue;
            int chance = chance(recipe.getOutputChance(i));
            BigInteger amount = (capacity ? RecipeOutputCounts.maximum(count, chance)
                : RecipeOutputCounts.roll(count, chance, XSTR.XSTR_INSTANCE))
                    .multiply(BigInteger.valueOf(type.stackSize));
            amount = effects.apply(amount);
            if (amount.signum() > 0) values.add(BigAEStackValues.copyWithSize(AEItemStack.create(type), amount));
        }
        for (int i = 0; i < Math.min(recipe.mFluidOutputs.length, controller.getFluidOutputLimit()); i++) {
            FluidStack type = recipe.mFluidOutputs[i];
            if (type == null) continue;
            int chance = chance(recipe.getFluidOutputChance(i));
            BigInteger amount = (capacity ? RecipeOutputCounts.maximum(count, chance)
                : RecipeOutputCounts.roll(count, chance, XSTR.XSTR_INSTANCE)).multiply(BigInteger.valueOf(type.amount));
            amount = effects.applyFluid(amount);
            if (amount.signum() > 0) values.add(BigAEStackValues.copyWithSize(AEFluidStack.create(type), amount));
        }
        return values;
    }

    public boolean commitWirelessStep() {
        if (batch == null) throw new IllegalStateException("No GTNL wireless batch");
        if (hatch == null && !source().consumeEUBig(totalEnergy)) return false;
        batch.append(this, consumeOutputs());
        return true;
    }

    private BigMachineOutputQueue consumeOutputs() {
        if (committed) throw new IllegalStateException("GTNL recipe committed twice");
        BigMachineOutputQueue products = new BigMachineOutputQueue();
        for (IAEStack<?> output : outputs(parallels, false)) {
            if (output instanceof IAEItemStack)
                products.addItem(((IAEItemStack) output).getItemStack(), BigAEStackValues.get(output));
            else products.addFluid(((IAEFluidStack) output).getFluidStack(), BigAEStackValues.get(output));
        }
        inputs.consume(parallels);
        committed = true;
        return products;
    }

    @Override
    public void commit(int ignoredDuration) {
        BigMachineOutputQueue products = consumeOutputs();
        if (hatch != null) state.startExact(parallels, totalEnergy, duration, products, isUltimate());
        else state.startNativePowered(parallels, totalEnergy, duration, products);
    }

    public boolean isWirelessBatch() {
        return batch != null;
    }

    public BigGtnlParallelHelper setDurationAdjustment(
        java.util.function.ToDoubleFunction<BigGtnlParallelHelper> adjust) {
        durationAdjustment = adjust;
        return this;
    }

    public BigInteger getParallelsBig() {
        return parallels;
    }

    public BigInteger getEutBig() {
        return eut;
    }

    public BigInteger getTotalEnergyBig() {
        return totalEnergy;
    }

    public int getDuration() {
        return duration;
    }

    public GTRecipe getOriginalRecipe() {
        return originalRecipe;
    }

    public boolean consumesResources() {
        for (GTRecipe.RecipeItemInput input : recipe.getCachedCombinedItemInputs())
            if (input.inputAmount > 0) return true;
        for (FluidStack input : recipe.mFluidInputs) if (input != null && input.amount > 0) return true;
        return false;
    }
}
