package com.silvia.apeiron.common.machine.tst.verification;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver;
import com.Nxer.TwistSpaceTechnology.common.machine.GTCM_CrystallineInfinitier;
import com.Nxer.TwistSpaceTechnology.common.machine.MiscHelper;
import com.Nxer.TwistSpaceTechnology.common.machine.TST_StarcoreMiner;
import com.Nxer.TwistSpaceTechnology.common.machine.ValueEnum;
import com.Nxer.TwistSpaceTechnology.config.Config;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Logic;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.logic.OP_Values;
import com.Nxer.TwistSpaceTechnology.system.OreProcess.machines.TST_OreProcessingFactory;
import com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MachineRecipeDisplay;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.StarcoreYield;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Real custom generators, native accounts and transformed completion paths, with no Apeiron energy hatch. */
public final class TstNativeOutputSmoke {

    private TstNativeOutputSmoke() {}

    private static final class Miner extends TST_StarcoreMiner {

        Miner() {
            super("apeiron.verify.starcore_native");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        void complete() {
            super.outputAfterRecipe();
        }
    }

    private static final class Factory extends TST_OreProcessingFactory {

        final ArrayList<ItemStack> inputs = new ArrayList<>();

        Factory() {
            super("apeiron.verify.ore_native");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public ArrayList<ItemStack> getStoredInputs() {
            return inputs;
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        void complete() {
            super.outputAfterRecipe();
        }
    }

    private static final class MutableNativeRecipe extends GTCM_CrystallineInfinitier {

        MutableNativeRecipe() {
            super("apeiron.verify.native_output_mutation");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        @Override
        public CheckRecipeResult checkProcessing() {
            mOutputItems = new ItemStack[] { new ItemStack(Items.diamond, 20) };
            mOutputFluids = new FluidStack[] { new FluidStack(FluidRegistry.WATER, 100) };
            mMaxProgresstime = 20;
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        void complete() {
            // Native machine callbacks may reduce or cancel products before GT emits the final arrays.
            if (mOutputItems != null) addItemOutputs(mOutputItems);
            if (mOutputFluids != null) addFluidOutputs(mOutputFluids);
            mOutputItems = null;
            mOutputFluids = null;
            super.outputAfterRecipe();
        }
    }

    public static void verify() throws ReflectiveOperationException {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        int base = Config.StackSizeOfEveryOreItemStackWhenMining_StarcoreMiner;
        ItemStack astral = MiscHelper.ASTRAL_ARRAY_FABRICATOR;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            Config.StackSizeOfEveryOreItemStackWhenMining_StarcoreMiner = Integer.MAX_VALUE;
            // TST initializes this in load-complete, after the isolated post-init self-check.
            MiscHelper.ASTRAL_ARRAY_FABRICATOR = tectech.thing.CustomItemList.astralArrayFabricator.get(1);
            verifyMiner(false);
            verifyMiner(true);
            verifyFactory(false);
            verifyFactory(true);
            verifyMutableNativeRecipe();
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
            Config.StackSizeOfEveryOreItemStackWhenMining_StarcoreMiner = base;
            MiscHelper.ASTRAL_ARRAY_FABRICATOR = astral;
        }
        Apeiron.LOG.info(
            "TST native generators verification passed: starcore/factory, native wireless/physical power, exact counts above long, display/save/completion and energy conservation");
    }

    private static void verifyMutableNativeRecipe() {
        MutableNativeRecipe machine = new MutableNativeRecipe();
        MTEInfiniteMEOutputAssembly output = attach(machine);
        check(((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(), "mutable native recipe failed");
        check(
            machine.mOutputItems != null && machine.mOutputFluids != null,
            "generic batch hook took ownership before native running callbacks");
        machine.mOutputItems[0].stackSize = 3;
        machine.mOutputFluids = null;
        machine.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(BigInteger.valueOf(3)),
            "native running item adjustment was ignored");
        check(
            output.getFluidProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "cancelled native fluid products were still emitted");
    }

    private static MTEInfiniteMEOutputAssembly attach(MTEMultiBlockBase machine) {
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        output.getProvider()
            .setCheckMode(true);
        machine.addOutputBusToMachineList(output.getBaseMetaTileEntity(), 0);
        check(InfiniteEnergyHatches.find(machine) == null, "fixture has an Apeiron energy hatch");
        return output;
    }

    private static void verifyMiner(boolean wireless) throws ReflectiveOperationException {
        Miner machine = new Miner();
        MTEInfiniteMEOutputAssembly output = attach(machine);
        VoidMinerUtility.DropMap drops = new VoidMinerUtility.DropMap();
        drops.addDrop(new ItemStack(Items.diamond), 1);
        set(machine, "dropMap", drops);
        set(machine, "extraDropMap", new VoidMinerUtility.DropMap());
        set(machine, "totalWeight", drops.getTotalWeight());
        set(machine, "isWirelessMode", wireless);
        UUID owner = UUID.randomUUID();
        set(machine, "ownerUUID", owner);
        BigInteger before = BigInteger.TEN.pow(40);
        WirelessNetworkManager.setUserEU(owner, before);
        machine.mInventory[1] = MiscHelper.ASTRAL_ARRAY_FABRICATOR.copy();
        machine.mInventory[1].stackSize = 64;
        check(((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(), "native miner failed");
        BigInteger expected = StarcoreYield.amount(Integer.MAX_VALUE, 64)
            .multiply(BigInteger.valueOf(ValueEnum.AmountOfOreStackPerMining_StarcoreMiner));
        checkRecipe(machine, expected);
        BigInteger debit = wireless ? BigInteger.valueOf(ValueEnum.Eut_StarcoreMiner)
            .multiply(BigInteger.valueOf(ValueEnum.DurationPerMining_StarcoreMiner)) : BigInteger.ZERO;
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(before.subtract(debit)),
            "miner native debit changed");
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        Miner restored = new Miner();
        restored.loadNBTData(saved);
        checkRecipe(restored, expected);
        restored.complete();
        BigTstOutputController pending = (BigTstOutputController) (Object) restored;
        check(
            pending.getPendingItemOutputBig()
                .equals(expected),
            "detached miner dropped its produced batch");
        restored.addOutputBusToMachineList(output.getBaseMetaTileEntity(), 0);
        pending.flushOutputsBig();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(expected),
            "miner exact output lost yield");
        check(
            pending.getPendingItemOutputBig()
                .signum() == 0,
            "miner left int chunks queued");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(before.subtract(debit)),
            "miner completion charged again");
    }

    private static void verifyFactory(boolean wireless) throws ReflectiveOperationException {
        Factory machine = new Factory();
        MTEInfiniteMEOutputAssembly output = attach(machine);
        UUID owner = UUID.randomUUID();
        set(machine, "ownerUUID", owner);
        set(machine, "isWirelessMode", wireless);
        set(machine, "EUtCanUse", 20L * OP_Values.OreProcessRecipeEUt);
        ItemStack input = new ItemStack(Items.diamond, 11);
        machine.inputs.add(input);
        TST_ItemID type = TST_ItemID.create(input);
        ItemStacksGiver recipe = new ItemStacksGiver();
        BigInteger each = BigInteger.valueOf(Long.MAX_VALUE - 17);
        recipe.cache.put(TST_ItemID.create(new ItemStack(Items.emerald)), each.longValueExact());
        ItemStacksGiver previous = OP_Logic.OP_GIVER_MAP.put(type, recipe);
        BigInteger before = BigInteger.TEN.pow(40);
        WirelessNetworkManager.setUserEU(owner, before);
        try {
            check(((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(), "native factory failed");
            BigInteger expected = each.multiply(BigInteger.valueOf(11));
            checkRecipe(machine, expected);
            check(input.stackSize == 0, "native factory input not consumed");
            check(machine.mMaxProgresstime == OP_Values.OreProcessRecipeDuration, "native factory time changed");
            BigInteger debit = wireless ? BigInteger.valueOf(OP_Values.OreProcessWirelessEUConsumption)
                .multiply(BigInteger.valueOf(11)) : BigInteger.ZERO;
            check(
                WirelessNetworkManager.getUserEU(owner)
                    .equals(before.subtract(debit)),
                "factory native debit changed");
            machine.complete();
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(expected),
                "factory output clipped or duplicated");
            check(
                WirelessNetworkManager.getUserEU(owner)
                    .equals(before.subtract(debit)),
                "factory completion charged again");
        } finally {
            if (previous == null) OP_Logic.OP_GIVER_MAP.remove(type);
            else OP_Logic.OP_GIVER_MAP.put(type, previous);
        }
    }

    private static void checkRecipe(MTEMultiBlockBase machine, BigInteger expected) {
        BigTstOutputController big = (BigTstOutputController) machine;
        check(
            big.getRecipeItemOutputBig()
                .equals(expected),
            "native recipe count differs from exact ledger");
        check(
            machine.mOutputItems == null || machine.mOutputItems.length == 0,
            "native recipe still expanded int chunks");
        check(
            !((BigWirelessController) machine).getWirelessRecipeState()
                .isRunning(),
            "native recipe became an Apeiron energy recipe");
        BigMachineOutputQueue display = new BigMachineOutputQueue();
        NBTTagCompound snapshot = MachineRecipeDisplay.snapshot(machine);
        display.load(snapshot);
        check(
            display.getItemAmountBig()
                .equals(expected),
            "UI lost native exact outputs: expected=" + expected
                + " actual="
                + display.getItemAmountBig()
                + " snapshot="
                + snapshot);
        check(display.outputTypes() == 1, "UI repeated a native output type");
        NBTTagCompound hover = new NBTTagCompound();
        machine.getWailaNBTData(
            com.silvia.apeiron.common.integration.waila.verification.MachineWailaCompatibilitySmoke.viewer(),
            (net.minecraft.tileentity.TileEntity) machine.getBaseMetaTileEntity(),
            hover,
            null,
            0,
            0,
            0);
        check(
            hover.getString("ApeironTstItemCount0")
                .equals(BigNumberFormatter.formatExact(expected)),
            "native TST HUD lost exact quantity");
    }

    private static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (NoSuchFieldException missing) {
                // Find the native controller field rather than the fixture subclass.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("TST native outputs: " + message);
    }
}
