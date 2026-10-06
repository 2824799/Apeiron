package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.TierEU;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEMassFabricator;

/** Runs the real native UU lookup and recycler probability recipe through shared exact processing. */
public final class MassFabricatorEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(40)
        .add(BigInteger.valueOf(17));

    private MassFabricatorEnergySmoke() {}

    private static final class Controller extends MTEMassFabricator {

        Controller(int mode) {
            super("apeiron.verify.mass_fabricator_energy");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            setMachineMode(mode);
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
        }

        GTRecipe activeRecipe() {
            try {
                Field field = gregtech.api.logic.ProcessingLogic.class.getDeclaredField("lastRecipe");
                field.setAccessible(true);
                return (GTRecipe) field.get(processingLogic);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot inspect native recycler probability recipe", error);
            }
        }

        void complete() {
            outputAfterRecipe();
        }
    }

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData saved = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            Controller nativeMachine = new Controller(1);
            check(nativeMachine.getTrueParallel() == 64, "unmodified recycler lost its native parallel cap");
            for (boolean ultimate : new boolean[] { false, true }) {
                verifyUU(ultimate);
                verifyRecycler(ultimate);
            }
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = saved;
        }
        Apeiron.LOG.info(
            "Mass Fabricator real recipe verification passed: catalyst-only UU, recycler chance, exact parallels, voltage/time, budget, debit, save and outputs");
    }

    private static UUID energy(Controller controller, boolean ultimate) {
        UUID owner = UUID.randomUUID();
        ApeironMachineTile tile = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        tile.setOwnerUuid(owner);
        check(controller.addToMachineList(tile, 0), "energy structure registration");
        state(controller).setParallelSettingBig(BigInteger.ZERO);
        state(controller).setTargetDuration(7);
        return owner;
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static WirelessRecipeState state(Controller controller) {
        return ((BigWirelessController) (Object) controller).getWirelessRecipeState();
    }

    private static MTEInfiniteMEOutputAssembly output(Controller controller) {
        MTEInfiniteMEOutputAssembly result = InfiniteMEOutputAssemblySmoke.assembly();
        for (int repeat = 0; repeat < 3; repeat++) check(
            controller.addToMachineList(result.getBaseMetaTileEntity(), 0),
            "generic output structure registration");
        check(
            controller.mOutputBusses.size() == 1 && controller.mOutputHatches.size() == 1,
            "GT++ generic scanner lost or duplicated an output channel");
        return result;
    }

    private static MTEInfinitePatternInputAssembly input(Controller controller, ItemStack item, BigInteger count) {
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Collections.singletonList(BigAEStackValues.copyWithSize(AEItemStack.create(item), count))),
            "exact input setup");
        controller.mDualInputHatches.add(input);
        return input;
    }

    private static void start(Controller controller, MTEInfinitePatternInputAssembly input) {
        input.beginRecipeProcessing();
        try {
            gregtech.api.recipe.check.CheckRecipeResult result = controller.checkProcessing();
            check(
                result.wasSuccessful(),
                "native recipe rejected: " + result.getID() + " / " + result.getDisplayString());
        } finally {
            input.endRecipeProcessing();
        }
    }

    private static BigInteger total(List<IAEStack<?>> outputs) {
        return outputs.stream()
            .map(BigAEStackValues::get)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static GTRecipe basicUU(Controller controller) {
        return controller.getRecipeMap()
            .getAllRecipes()
            .stream()
            .filter(
                recipe -> recipe.mInputs.length == 1 && recipe.mInputs[0].stackSize == 0
                    && recipe.mInputs[0].getItemDamage() == 1
                    && recipe.mFluidInputs.length == 0)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Native catalyst-only UU recipe missing"));
    }

    private static void verifyUU(boolean ultimate) {
        Controller controller = new Controller(0);
        GTRecipe recipe = basicUU(controller);
        UUID owner = energy(controller, ultimate);
        state(controller).setVoltageSetting(recipe.mEUt);
        state(controller).setParallelSettingBig(HUGE);
        MTEInfiniteMEOutputAssembly output = output(controller);
        MTEInfinitePatternInputAssembly input = input(
            controller,
            GTUtility.copyAmount(1, recipe.mInputs[0]),
            BigInteger.ONE);
        WirelessNetworkManager.setUserEU(
            owner,
            HUGE.multiply(BigInteger.valueOf(recipe.mEUt))
                .multiply(BigInteger.valueOf(recipe.mDuration))
                .multiply(BigInteger.TEN));
        start(controller, input);
        check(
            state(controller).getParallelsBig()
                .equals(HUGE),
            "circuit-only recipe ignored manual bound");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.ONE),
            "non-consumable circuit was spent");
        check(controller.mMaxProgresstime == (ultimate ? 7 : recipe.mDuration), "native/ultimate UU time ignored");
        BigInteger expected = HUGE.multiply(BigInteger.valueOf(recipe.mFluidOutputs[0].amount));
        check(total(state(controller).getOutputDisplay()).equals(expected), "UU output truncated");
        BigInteger balance = WirelessNetworkManager.getUserEU(owner);
        controller.complete();
        controller.complete();
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .equals(expected),
            "UU output duplicated/truncated");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(balance),
            "completion double charged energy");

        Controller budget = new Controller(0);
        UUID budgetOwner = energy(budget, ultimate);
        state(budget).setVoltageSetting(recipe.mEUt);
        output(budget);
        MTEInfinitePatternInputAssembly budgetInput = input(
            budget,
            GTUtility.copyAmount(1, recipe.mInputs[0]),
            BigInteger.ONE);
        BigInteger per = state(controller).getTotalEUBig()
            .divide(HUGE);
        WirelessNetworkManager.setUserEU(budgetOwner, per.multiply(BigInteger.valueOf(3)));
        start(budget, budgetInput);
        check(
            state(budget).getParallelsBig()
                .equals(BigInteger.valueOf(3)),
            "catalyst-only energy budget did not limit batch");
        check(
            budgetInput.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.ONE),
            "budget batch spent circuit");
    }

    private static void verifyRecycler(boolean ultimate) {
        Controller controller = new Controller(1);
        UUID owner = energy(controller, ultimate);
        state(controller).setVoltageSetting(TierEU.RECIPE_LV);
        MTEInfiniteMEOutputAssembly output = output(controller);
        ItemStack recyclerInput = recyclerInput();
        MTEInfinitePatternInputAssembly input = input(controller, recyclerInput, HUGE);
        WirelessNetworkManager.setUserEU(owner, HUGE.multiply(BigInteger.valueOf(32 * 40L)));
        start(controller, input);
        check(
            state(controller).getParallelsBig()
                .equals(HUGE),
            "probability recipe bypassed exact parallel helper");
        check(
            input.getBuffers()
                .get(0)
                .isEmpty(),
            "recycler input debit truncated");
        check(controller.mMaxProgresstime == (ultimate ? 7 : 40), "recycler time ignored");
        List<IAEStack<?>> rolled = new ArrayList<>(state(controller).getOutputDisplay());
        BigInteger count = total(rolled);
        GTRecipe recipe = controller.activeRecipe();
        check(recipe != null, "native recycler did not retain its recipe");
        BigInteger guaranteed = BigInteger.ZERO, maximum = BigInteger.ZERO;
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            BigInteger per = BigInteger.valueOf(recipe.mOutputs[i].stackSize);
            guaranteed = guaranteed.add(
                HUGE.multiply(per)
                    .multiply(BigInteger.valueOf(recipe.getOutputChance(i) / 10000)));
            maximum = maximum.add(
                com.silvia.apeiron.math.RecipeOutputCounts.maximum(HUGE, recipe.getOutputChance(i))
                    .multiply(per));
        }
        check(count.compareTo(guaranteed) >= 0 && count.compareTo(maximum) <= 0, "native output chance bounds changed");
        check(count.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0, "recycler huge probabilities truncated");
        check(!count.equals(maximum), "probability outputs became guaranteed outputs");
        NBTTagCompound saved = new NBTTagCompound();
        controller.saveNBTData(saved);
        Controller restored = new Controller(1);
        restored.loadNBTData(saved);
        check(total(state(restored).getOutputDisplay()).equals(count), "reload rerolled outputs");
        BigInteger before = WirelessNetworkManager.getUserEU(owner);
        BigInteger cost = state(controller).getTotalEUBig();
        for (int i = 0; i < controller.mMaxProgresstime; i++)
            check(controller.onRunningTick(null), "recycler tick debit failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(before.subtract(cost)),
            "recycler energy not conserved");
        controller.complete();
        controller.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(count),
            "recycler completion duplicated or lost outputs");
    }

    private static ItemStack recyclerInput() {
        ItemStack[] candidates = { new ItemStack(Blocks.cobblestone), new ItemStack(Blocks.dirt),
            new ItemStack(Items.stick), new ItemStack(Items.iron_ingot), new ItemStack(Items.gold_ingot) };
        for (ItemStack candidate : candidates)
            if (GTModHandler.getRecyclerOutput(candidate, 0) != null) return candidate;
        throw new IllegalStateException("Native recycler has no verification input");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Mass Fabricator energy: " + message);
    }
}
