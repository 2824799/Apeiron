package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInputs;
import com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.Loader;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.tileentities.machines.multi.MTELargeChemicalReactor;
import gregtech.common.tileentities.machines.multi.MTEMegaChemicalReactor;

/** Real substitute-fluid recipes must use exact parallels without changing native fluid-choice semantics. */
public final class ChemicalReactorEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(17));

    private ChemicalReactorEnergySmoke() {}

    static void verify() {
        verifyFluidChoice();
        if (!Loader.isModLoaded("cropsnh")) return;
        GTRecipe recipe = RecipeMaps.multiblockChemicalReactorRecipes.getAllRecipes()
            .stream()
            .filter(value -> value.mDuration == 300 && value.mEUt == 30 && value.mAltFluidInputs != null)
            .filter(
                value -> value.mOutputs.length == 1 && value.mOutputs[0] != null
                    && value.mOutputs[0].stackSize == 3
                    && value.mOutputs[0].getUnlocalizedName()
                        .toLowerCase(Locale.ROOT)
                        .contains("fertiliz"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Native substitute-water fertilizer recipe missing"));
        check(
            BigRecipeParallelHelper.supports(recipe),
            "fertilizer still falls back to single-parallel native processing");
        for (boolean mega : new boolean[] { false, true }) for (boolean ultimate : new boolean[] { false, true })
            for (FluidStack fluid : recipe.mAltFluidInputs[0]) verifyFertilizer(recipe, fluid, ultimate, mega);
        verifyNative(recipe);
        Apeiron.LOG.info(
            "Chemical reactor verification passed: real fertilizer, water/distilled water, large/mega reactors, both hatches, exact input/output/energy, time, reload and native fallback");
    }

    private static MTEMultiBlockBase reactor(boolean mega) {
        MTEMultiBlockBase machine = mega ? new MTEMegaChemicalReactor("apeiron.verify.mega_chemical")
            : new MTELargeChemicalReactor("apeiron.verify.large_chemical");
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        return machine;
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static WirelessRecipeState state(MTEMultiBlockBase machine) {
        return ((BigWirelessController) machine).getWirelessRecipeState();
    }

    private static void verifyFertilizer(GTRecipe recipe, FluidStack fluid, boolean ultimate, boolean mega) {
        MTEMultiBlockBase machine = reactor(mega);
        UUID owner = UUID.randomUUID();
        ApeironMachineTile energy = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        energy.setOwnerUuid(owner);
        check(machine.addToMachineList(energy, 0), "chemical energy hatch rejected");
        WirelessRecipeState state = state(machine);
        state.setParallelSettingBig(HUGE);
        state.setVoltageSetting(recipe.mEUt);
        state.setTargetDuration(1);
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        check(machine.addToMachineList(output.getBaseMetaTileEntity(), 0), "chemical output assembly rejected");
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        List<IAEStack<?>> stocks = new ArrayList<>();
        for (ItemStack item : recipe.mInputs) if (item != null && item.stackSize > 0) stocks.add(
            BigAEStackValues.copyWithSize(
                AEItemStack.create(item),
                HUGE.add(BigInteger.ONE)
                    .multiply(BigInteger.valueOf(item.stackSize))));
        stocks.add(
            BigAEStackValues.copyWithSize(
                AEFluidStack.create(fluid),
                HUGE.add(BigInteger.ONE)
                    .multiply(BigInteger.valueOf(fluid.amount))));
        check(input.addToBufferBig(0, stocks), "chemical exact input setup failed");
        machine.mDualInputHatches.add(input);
        BigInteger cost = HUGE.multiply(BigInteger.valueOf((long) recipe.mEUt * recipe.mDuration));
        WirelessNetworkManager.setUserEU(owner, cost.multiply(BigInteger.TEN));
        input.beginRecipeProcessing();
        try {
            CheckRecipeResult result = machine.checkProcessing();
            check(result.wasSuccessful(), "fertilizer recipe failed: " + result.getDisplayString());
        } finally {
            input.endRecipeProcessing();
        }
        check(
            state.isRunning() && state.getParallelsBig()
                .equals(HUGE),
            "fertilizer parallel setting ignored");
        check(machine.mMaxProgresstime == (ultimate ? 1 : recipe.mDuration), "chemical native/ultimate time ignored");
        check(
            state.getTotalEUBig()
                .equals(cost),
            "chemical base energy changed by ultimate time");
        for (IAEStack<?> stock : stocks) {
            BigInteger left = input.getBuffers()
                .get(0)
                .getAmountBig(stock);
            check(
                left.equals(
                    BigAEStackValues.get(stock)
                        .divide(HUGE.add(BigInteger.ONE))),
                "chemical alternative input debit truncated");
        }
        BigInteger produced = HUGE.multiply(BigInteger.valueOf(3));
        check(
            state.getOutputDisplay()
                .stream()
                .map(BigAEStackValues::get)
                .reduce(BigInteger.ZERO, BigInteger::add)
                .equals(produced),
            "fertilizer output truncated");
        NBTTagCompound saved = state.save();
        WirelessRecipeState restored = new WirelessRecipeState();
        restored.load(saved);
        check(
            restored.getParallelsBig()
                .equals(HUGE)
                && restored.getTotalEUBig()
                    .equals(cost),
            "chemical running state reload lost exact values");
        BigInteger balance = WirelessNetworkManager.getUserEU(owner);
        complete(machine);
        complete(machine);
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(produced),
            "fertilizer completion truncated or duplicated output");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(balance),
            "chemical completion charged energy again");
    }

    private static void complete(MTEMultiBlockBase machine) {
        try {
            java.lang.reflect.Method method = MTEMultiBlockBase.class.getDeclaredMethod("outputAfterRecipe");
            method.setAccessible(true);
            method.invoke(machine);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot complete chemical reactor", error);
        }
    }

    private static void verifyNative(GTRecipe recipe) {
        MTEMultiBlockBase machine = reactor(false);
        ItemStack[] items = new ItemStack[recipe.mInputs.length];
        for (int i = 0; i < items.length; i++) items[i] = recipe.mInputs[i].copy();
        FluidStack fluid = recipe.mAltFluidInputs[0][1].copy();
        ProcessingLogic logic = new ProcessingLogic().enablePerfectOverclock()
            .setMachine(machine)
            .setRecipeMapSupplier(() -> RecipeMaps.multiblockChemicalReactorRecipes)
            .setAvailableVoltage(recipe.mEUt)
            .setAvailableAmperage(1)
            .setAmperageOC(false)
            .setInputItems(items)
            .setInputFluids(fluid);
        check(
            logic.process()
                .wasSuccessful(),
            "native distilled-water fertilizer failed");
        check(
            logic.getDuration() == recipe.mDuration && logic.getCurrentParallels() == 1 && !state(machine).isRunning(),
            "reactor without Apeiron hatch changed native behavior");
    }

    private static void verifyFluidChoice() {
        MTEMultiBlockBase machine = reactor(false);
        FluidStack water = new FluidStack(FluidRegistry.WATER, 1000);
        FluidStack distilled = GTModHandler.getDistilledWater(500);
        GTRecipe recipe = new GTRecipe(
            false,
            new ItemStack[0],
            new ItemStack[] { new ItemStack(Items.emerald) },
            null,
            null,
            null,
            null,
            null,
            new FluidStack[] { water },
            null,
            20,
            8,
            0);
        recipe.mAltFluidInputs = new FluidStack[][] { { water, distilled } };
        FluidStack a = water.copy();
        a.amount = 4000;
        FluidStack b = distilled.copy();
        b.amount = 3000;
        BigRecipeInputs inputs = new BigRecipeInputs(machine, recipe, new ItemStack[0], new FluidStack[] { a, b });
        check(
            inputs.allocation()
                .maximum(ParallelLimit.unlimited())
                .equals(BigInteger.valueOf(6)),
            "substitute amount ratio not preserved");
        inputs.consume(BigInteger.valueOf(6));
        check(
            a.amount == 4000 && b.amount == 0,
            "mixed substitute fluids or consumed the representative instead of selected alternative");
        a.amount = 1000;
        b.amount = 500;
        inputs = new BigRecipeInputs(machine, recipe, new ItemStack[0], new FluidStack[] { a, b });
        check(
            inputs.allocation()
                .maximum(ParallelLimit.unlimited())
                .equals(BigInteger.ONE),
            "mixed water types increased parallels");
        inputs.consume(BigInteger.ONE);
        check(a.amount == 0 && b.amount == 500, "substitute tie did not preserve recipe order");
        inputs = new BigRecipeInputs(machine, recipe, new ItemStack[0], new FluidStack[0]);
        check(
            inputs.allocation()
                .maximum(ParallelLimit.unlimited())
                .signum() == 0,
            "missing substitute removed input requirement");
        verifyExactFluidChoice(machine, recipe, water, distilled);
        recipe.mFluidInputs = new FluidStack[] { water, water };
        check(
            !BigRecipeParallelHelper.supportsInputs(recipe),
            "overlapping substitute slots replaced native sequential selection");
    }

    private static void verifyExactFluidChoice(MTEMultiBlockBase machine, GTRecipe recipe, FluidStack water,
        FluidStack distilled) {
        MTEInfinitePatternInputAssembly source = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        IAEStack<?> a = BigAEStackValues
            .copyWithSize(AEFluidStack.create(water), HUGE.multiply(BigInteger.valueOf(1000)));
        IAEStack<?> b = BigAEStackValues
            .copyWithSize(AEFluidStack.create(distilled), HUGE.multiply(BigInteger.valueOf(1000)));
        check(source.addToBufferBig(0, java.util.Arrays.asList(a, b)), "exact alternative stock setup failed");
        machine.mDualInputHatches.add(source);
        source.beginRecipeProcessing();
        try {
            com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer buffer = source.getBuffers()
                .get(0);
            BigRecipeInputs inputs = new BigRecipeInputs(
                machine,
                recipe,
                buffer.getItemInputs(),
                buffer.getFluidInputs());
            check(
                inputs.allocation()
                    .maximum(ParallelLimit.unlimited())
                    .equals(HUGE.multiply(BigInteger.valueOf(2))),
                "substitute selection used saturated int views instead of exact stocks");
            inputs.consume(HUGE.multiply(BigInteger.valueOf(2)));
            check(
                buffer.getAmountBig(a)
                    .equals(BigAEStackValues.get(a))
                    && buffer.getAmountBig(b)
                        .signum() == 0,
                "exact substitute debit mixed or truncated alternative stocks");
        } finally {
            source.endRecipeProcessing();
            machine.mDualInputHatches.clear();
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
