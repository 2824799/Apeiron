package com.silvia.apeiron.common.machine.tst.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.Nxer.TwistSpaceTechnology.common.machine.GTCM_CrystallineInfinitier;
import com.Nxer.TwistSpaceTechnology.common.machine.GT_TileEntity_Silksong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ProcessingLogic;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.output.BigOutputAmount;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.tst.output.BigTstOutputLists;
import com.silvia.apeiron.common.machine.tst.output.TstOutputCapacity;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.enums.HatchElement;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;

/** Exercises the actual transformed TST helper and controller without consuming items in a player world. */
public final class TstOutputSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private TstOutputSmoke() {}

    private static class Machine extends GTCM_CrystallineInfinitier {

        Machine() {
            super("apeiron_tst_verification");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        void complete() {
            super.outputAfterRecipe();
        }
    }

    public static void verify() {
        if (!ApeironConfig.isTstBigOutputEnabled() || !ApeironConfig.areAeMixinsEnabled()) {
            check(
                !BigTstOutputController.class.isAssignableFrom(GTCM_MultiMachineBase.class),
                "disabled Mixin applied");
            Apeiron.LOG.info("TST output integration disabled as configured");
            return;
        }
        try {
            verifyHelper();
            verifyController();
            verifySeparateOutputs();
            verifyWirelessAccumulator();
            verifyZeroEntryPersistence();
            Apeiron.LOG.info(
                "TST shared output calculation, three-device recognition, capacity and persistence verification passed");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("TST verification reflection failed", e);
        }
    }

    private static void verifyHelper() throws ReflectiveOperationException {
        final GTCM_ParallelHelper helper = new GTCM_ParallelHelper();
        final ItemStack[] items = { new ItemStack(Items.diamond, Integer.MAX_VALUE),
            new ItemStack(Items.diamond, Integer.MAX_VALUE), new ItemStack(Items.diamond, Integer.MAX_VALUE) };
        final FluidStack[] fluids = { new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE),
            new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE),
            new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE) };
        final GTRecipe recipe = new GTRecipe(
            false,
            new ItemStack[0],
            items,
            null,
            new int[] { 10000, 10000, 10000 },
            null,
            null,
            null,
            new FluidStack[0],
            fluids,
            20,
            1,
            0);
        field(helper, "recipe").set(helper, recipe);
        field(helper, "currentParallel").setInt(helper, Integer.MAX_VALUE);
        method(GTCM_ParallelHelper.class, "calculateLongOutputs").invoke(helper);
        field(helper, "built").setBoolean(helper, true);
        field(helper, "calculateOutputsAsLong").setBoolean(helper, true);
        final List<ItemStackLong> itemOutputs = helper.getLongItemOutputs();
        final List<FluidStackLong> fluidOutputs = helper.getLongFluidOutputs();
        final BigInteger expected = BigInteger.valueOf(Integer.MAX_VALUE)
            .pow(2)
            .multiply(BigInteger.valueOf(3));
        check(expected.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0, "test count did not exceed long");
        check(
            itemOutputs.size() == 1 && BigTstOutputLists.amount(itemOutputs.get(0))
                .equals(expected),
            "helper exact item merge");
        check(
            fluidOutputs.size() == 1 && BigTstOutputLists.amount(fluidOutputs.get(0))
                .equals(expected),
            "helper exact fluid merge");
        check(
            itemOutputs.get(0)
                .stackSize() == Long.MAX_VALUE
                && fluidOutputs.get(0)
                    .amount() == Long.MAX_VALUE,
            "legacy long projections");
        final Machine machine = new Machine();
        method(GTCM_MultiMachineBase.class, "replaceMEOutputQueues", List.class, List.class)
            .invoke(machine, itemOutputs, fluidOutputs);
        final BigTstOutputController big = (BigTstOutputController) machine;
        check(
            big.getRecipeItemOutputBig()
                .equals(expected)
                && big.getRecipeFluidOutputBig()
                    .equals(expected),
            "helper-to-controller transfer truncated counts");
    }

    private static void verifyController() {
        final Machine machine = new Machine();
        final BigTstOutputController big = (BigTstOutputController) machine;
        final MTEInfiniteMEOutputAssembly assembly = InfiniteMEOutputAssemblySmoke.assembly();
        assembly.getProvider()
            .setCheckMode(true);
        assembly.getFluidProvider()
            .setCheckMode(true);
        check(machine.addOutputHatchToMachineList(assembly.getBaseMetaTileEntity(), 0), "hybrid in fluid position");
        check(machine.addOutputBusToMachineList(assembly.getBaseMetaTileEntity(), 0), "hybrid in bus position");
        check(machine.mOutputBusses.size() == 1 && machine.mOutputHatches.size() == 1, "duplicate hybrid registration");
        check(
            HatchElement.OutputHatch.mteClasses()
                .contains(MTEInfiniteMEOutputAssembly.class),
            "structure class recognition");
        check(
            HatchElement.OutputBus.count(machine) == 1 && HatchElement.OutputHatch.count(machine) == 1,
            "structure output counts");
        check(machine.isMEOutputEnabled(), "hybrid does not enable shared ME mode");
        final ItemStack diamond = new ItemStack(Items.diamond);
        final FluidStack water = new FluidStack(FluidRegistry.WATER, 1);
        check(
            big.outputItemToMENetworkBig(null, HUGE)
                .equals(HUGE),
            "null item output discarded");
        check(
            big.outputFluidToMENetworkBig(null, HUGE)
                .equals(HUGE),
            "null fluid output discarded");
        big.mergeItemIntoMEOutputQueueBig(diamond, HUGE);
        big.mergeItemIntoMEOutputQueueBig(diamond, HUGE);
        big.mergeFluidIntoMEOutputQueueBig(water, HUGE.multiply(BigInteger.valueOf(3)));
        check(
            TstOutputCapacity.itemsFit(machine.getMEItemOutputInfo(), machine.getOutputBusses()),
            "item capacity reservation");
        check(
            TstOutputCapacity.fluidsFit(machine.getMEFluidOutputInfo(), machine.getOutputHatches()),
            "fluid capacity reservation");
        check(
            assembly.getProvider()
                .getCachedAmountBig()
                .signum() == 0
                && assembly.getFluidProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
            "capacity check committed output");
        final NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        final Machine restored = new Machine();
        restored.loadNBTData(saved);
        restored.loadNBTData(saved);
        final BigTstOutputController loaded = (BigTstOutputController) restored;
        check(
            loaded.getRecipeItemOutputBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2)))
                && loaded.getRecipeFluidOutputBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(3))),
            "recipe NBT exact counts");
        restored.complete();
        check(
            loaded.getPendingItemOutputBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2)))
                && loaded.getPendingFluidOutputBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(3))),
            "missing outputs were discarded");
        final NBTTagCompound produced = new NBTTagCompound();
        restored.saveNBTData(produced);
        restored.loadNBTData(produced);
        restored.loadNBTData(produced);
        restored.addOutputBusToMachineList(assembly.getBaseMetaTileEntity(), 0);
        loaded.flushOutputsBig();
        check(
            loaded.getPendingItemOutputBig()
                .signum() == 0
                && loaded.getPendingFluidOutputBig()
                    .signum() == 0,
            "pending output retry");
        check(
            assembly.getProvider()
                .getCachedAmountBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2)))
                && assembly.getFluidProvider()
                    .getCachedAmountBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(3))),
            "committed exact quantities");
        final NBTTagCompound emptyDrop = new NBTTagCompound();
        restored.setItemNBT(emptyDrop);
        check(emptyDrop.hasNoTags(), "empty TST controller drop has Apeiron output NBT");
        final Machine noOutput = new Machine();
        final BigTstOutputController noOutputBig = (BigTstOutputController) noOutput;
        noOutputBig.mergeItemIntoMEOutputQueueBig(diamond, HUGE);
        noOutput.setMEOutput(true);
        noOutput.complete();
        final NBTTagCompound dropped = new NBTTagCompound();
        noOutput.setItemNBT(dropped);
        final Machine replaced = new Machine();
        replaced.loadNBTData(dropped);
        check(
            ((BigTstOutputController) replaced).getPendingItemOutputBig()
                .equals(HUGE),
            "controller drop lost produced output");
        check(
            TstOutputCapacity.itemsFit(machine.getMEItemOutputInfo(), machine.getOutputBusses()),
            "existing cache restricted unlimited output preflight");
    }

    private static void verifySeparateOutputs() {
        final ApeironMachineTile itemTile = new ApeironMachineTile();
        itemTile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(0));
        final MTEBoundlessMEOutputBus bus = (MTEBoundlessMEOutputBus) itemTile.getMetaTileEntity();
        final ApeironMachineTile fluidTile = new ApeironMachineTile();
        fluidTile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(1));
        final MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) fluidTile.getMetaTileEntity();
        final Machine machine = new Machine();
        check(machine.addOutputBusToMachineList(itemTile, 0), "item bus recognition");
        check(machine.addOutputHatchToMachineList(fluidTile, 0), "fluid hatch recognition");
        check(machine.isMEOutputEnabled(), "separate devices do not enable shared ME mode");
        final BigTstOutputController big = (BigTstOutputController) machine;
        big.mergeItemIntoMEOutputQueueBig(new ItemStack(Items.diamond), HUGE);
        big.mergeFluidIntoMEOutputQueueBig(new FluidStack(FluidRegistry.WATER, 1), HUGE);
        machine.complete();
        check(
            bus.getProvider()
                .getCachedAmountBig()
                .equals(HUGE),
            "separate bus exact output");
        check(
            hatch.getProvider()
                .getCachedAmountBig()
                .equals(HUGE),
            "separate hatch exact output");
    }

    private static void verifyWirelessAccumulator() throws ReflectiveOperationException {
        final GT_TileEntity_Silksong wireless = new GT_TileEntity_Silksong("apeiron_wireless_verification");
        final GTCM_ProcessingLogic logic = new GTCM_ProcessingLogic();
        final java.util.ArrayList<ItemStackLong> items = new java.util.ArrayList<>();
        final java.util.ArrayList<FluidStackLong> fluids = new java.util.ArrayList<>();
        BigTstOutputLists.mergeItem(items, new ItemStack(Items.diamond), HUGE);
        BigTstOutputLists.mergeFluid(fluids, new FluidStack(FluidRegistry.WATER, 1), HUGE);
        logic.setLongOutputs(items, fluids);
        final Field processing = MTEMultiBlockBase.class.getDeclaredField("processingLogic");
        processing.setAccessible(true);
        processing.set(wireless, logic);
        final Method merge = method(WirelessEnergyMultiMachineBase.class, "mergeWirelessOutputsIntoMEQueue");
        merge.invoke(wireless);
        merge.invoke(wireless);
        final BigTstOutputController big = (BigTstOutputController) wireless;
        check(
            big.getRecipeItemOutputBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "wireless item accumulator");
        check(
            big.getRecipeFluidOutputBig()
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "wireless fluid accumulator");
    }

    private static void verifyZeroEntryPersistence() {
        final Machine machine = new Machine();
        final BigTstOutputController big = (BigTstOutputController) machine;
        big.mergeItemIntoMEOutputQueueBig(new ItemStack(Items.diamond), HUGE);
        big.mergeItemIntoMEOutputQueueBig(new ItemStack(Items.emerald), HUGE);
        big.mergeFluidIntoMEOutputQueueBig(new FluidStack(FluidRegistry.WATER, 1), HUGE);
        big.mergeFluidIntoMEOutputQueueBig(new FluidStack(FluidRegistry.LAVA, 1), HUGE);
        ((BigOutputAmount) (Object) machine.getMEItemOutputInfo()
            .get(0)).setOutputAmountBig(BigInteger.ZERO);
        ((BigOutputAmount) (Object) machine.getMEFluidOutputInfo()
            .get(0)).setOutputAmountBig(BigInteger.ZERO);
        final NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        final Machine restored = new Machine();
        restored.loadNBTData(saved);
        final BigTstOutputController loaded = (BigTstOutputController) restored;
        check(
            loaded.getRecipeItemOutputBig()
                .equals(HUGE),
            "filtered zero item shifted exact saved counts");
        check(
            loaded.getRecipeFluidOutputBig()
                .equals(HUGE),
            "filtered zero fluid shifted exact saved counts");
    }

    private static Field field(Object instance, String name) throws ReflectiveOperationException {
        final Field field = instance.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
        throws ReflectiveOperationException {
        final Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("TST verification failed: " + message);
    }
}
