package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.science.gtnl.config.MainConfig;
import com.science.gtnl.utils.recipes.ChanceBonusManager;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.parallel.RecipeChanceEffects;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import appeng.util.item.AEItemStack;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

/** End-to-end shared probability and discounted energy checks with the actual optional provider registry. */
public final class RecipeModifierSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(30);
    private static int maps;

    private RecipeModifierSmoke() {}

    private static class Machine extends MTEElectricBlastFurnace {

        private final RecipeMap<?> recipes;
        private long voltage;

        private Machine(long voltage) {
            super("apeiron.verify.recipe_modifiers");
            this.voltage = voltage;
            recipes = RecipeMapBuilder.of("apeiron.verify.modifiers." + maps++)
                .maxIO(1, 1, 0, 1)
                .build();
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            mWrench = mScrewdriver = mSoftMallet = mHardHammer = mSolderingTool = mCrowbar = true;
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }

        @Override
        public long getMaxInputVoltage() {
            return voltage;
        }

        @Override
        protected ProcessingLogic createProcessingLogic() {
            return new ProcessingLogic() {

                @Override
                protected OverclockCalculator createOverclockCalculator(GTRecipe recipe) {
                    return super.createOverclockCalculator(recipe).setNoOverclock(true);
                }

                @Override
                protected ParallelHelper createParallelHelper(GTRecipe recipe) {
                    return super.createParallelHelper(recipe).setChanceMultiplier(2);
                }
            }.setEuModifier(0.8)
                .setSpeedBonus(0.5);
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        private void complete() {
            outputAfterRecipe();
        }
    }

    private static ApeironMachineTile tile(int offset, UUID owner) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        tile.setOwnerUuid(owner);
        return tile;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Recipe modifier verification: " + message);
    }

    public static void verify() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        boolean oldEnabled = MainConfig.machine.enableRecipeOutputChance;
        LinkedList<ChanceBonusManager.ChanceBonusProvider> providers = new LinkedList<>(
            ChanceBonusManager.bonusProviders);
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            ChanceBonusManager.bonusProviders.clear();
            ChanceBonusManager.addFirstBonusProvider(
                (machine, tier, multiplier,
                    recipe) -> machine instanceof Machine && ((Machine) machine).voltage > 32 ? 0.75 : 0.0);
            for (boolean ultimate : new boolean[] { false, true })
                for (int mode = 0; mode < 3; mode++) verifyShared(ultimate, mode);
        } finally {
            MainConfig.machine.enableRecipeOutputChance = oldEnabled;
            ChanceBonusManager.bonusProviders.clear();
            ChanceBonusManager.bonusProviders.addAll(providers);
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "Shared recipe modifier verification passed: voltage bonus, disabled bonus, item/fluid chance multipliers, 80% energy, speed, exact debit and output lifecycle");
    }

    private static void verifyShared(boolean ultimate, int mode) {
        MainConfig.machine.enableRecipeOutputChance = mode != 2;
        UUID owner = UUID.randomUUID();
        BigInteger initial = HUGE.multiply(BigInteger.valueOf(8000));
        WirelessNetworkManager.setUserEU(owner, initial);
        Machine machine = new Machine(mode == 0 ? 128 : mode == 1 ? 32 : 128);
        MTEInfiniteEnergyHatch hatch = (MTEInfiniteEnergyHatch) tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET,
            owner).getMetaTileEntity();
        machine.mEnergyHatches.add(hatch);
        MTEInfiniteMEOutputAssembly output = (MTEInfiniteMEOutputAssembly) tile(2, owner).getMetaTileEntity();
        machine.addOutputBusToMachineList(output.getBaseMetaTileEntity(), 0);
        MTEInfinitePatternInputAssembly source = (MTEInfinitePatternInputAssembly) tile(4, owner).getMetaTileEntity();
        source.addToBufferBig(
            0,
            Arrays.asList(BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), HUGE)));
        machine.mDualInputHatches.add(source);
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond))
            .itemOutputs(new ItemStack(Items.emerald))
            .outputChances(2500)
            .fluidOutputs(new FluidStack(FluidRegistry.WATER, 1))
            .fluidOutputChances(2500)
            .eut(100)
            .duration(20)
            .build()
            .get();
        machine.recipes.addRecipe(recipe);
        WirelessRecipeState state = ((BigWirelessController) machine).getWirelessRecipeState();
        state.setParallelSettingBig(HUGE);
        state.setVoltageSetting(128);
        state.setTargetDuration(7);
        GTRecipe adjusted = RecipeChanceEffects.apply(machine, recipe, 2);
        check(adjusted.getOutputChance(0) == (mode == 0 ? 10000 : 2500), "provider or disabled setting changed");
        boolean started = ((MultiBlockProcessingAccessor) machine).apeiron$checkRecipe();
        net.minecraft.nbt.NBTTagCompound diagnostic = new net.minecraft.nbt.NBTTagCompound();
        machine.saveNBTData(diagnostic);
        check(
            started,
            "shared modified recipe rejected: ultimate=" + ultimate
                + " mode="
                + mode
                + " result="
                + diagnostic.getCompoundTag("checkRecipeResult"));
        check(
            state.getTotalEUBig()
                .equals(HUGE.multiply(BigInteger.valueOf(800))),
            "80% EU / 50% duration modifiers lost");
        check(machine.mMaxProgresstime == (ultimate ? 7 : 10), "native speed or configured duration lost");
        for (int tick = 0; tick < machine.mMaxProgresstime; tick++)
            check(machine.onRunningTick(null), "discounted tick failed");
        check(
            initial.subtract(hatch.getAvailableEUBig())
                .equals(state.getTotalEUBig()),
            "discounted debit differed from plan");
        machine.complete();
        machine.complete();
        BigInteger itemCount = output.getProvider()
            .getCachedAmountBig();
        BigInteger fluidCount = output.getFluidProvider()
            .getCachedAmountBig();
        if (mode == 0) check(
            itemCount.equals(HUGE.multiply(BigInteger.valueOf(2))) && itemCount.equals(fluidCount),
            "voltage bonus or multiplier lost/doubled");
        else {
            BigInteger expected = HUGE.divide(BigInteger.valueOf(2));
            BigInteger tolerance = HUGE.divide(BigInteger.valueOf(1000));
            check(
                itemCount.subtract(expected)
                    .abs()
                    .compareTo(tolerance) < 0
                    && fluidCount.subtract(expected)
                        .abs()
                        .compareTo(tolerance) < 0,
                "unboosted chance became guaranteed");
        }
        check(
            recipe.getOutputChance(0) == 2500 && recipe.getFluidOutputChance(0) == 2500,
            "registered probabilities mutated");
        check(
            source.getBuffers()
                .get(0)
                .isEmpty(),
            "modified recipe input debit failed");
    }
}
