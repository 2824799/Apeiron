package com.silvia.apeiron.common.machine.me.output.verification;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.compat.DependencyCapabilities;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import appeng.api.AEApi;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.VoidingMode;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.VoidProtectionHelper;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Exercises native void protection and real electrolyzer startup without Apeiron energy hatches. */
public final class IndustrialElectrolyzerOutputSmoke {

    private IndustrialElectrolyzerOutputSmoke() {}

    public static MTEMultiBlockBase controller() throws ReflectiveOperationException {
        String current = "gregtech.common.tileentities.machines.multi.MTEIndustrialElectrolyzer";
        String legacy = "gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing.MTEIndustrialElectrolyzer";
        MTEMultiBlockBase result = (MTEMultiBlockBase) Class
            .forName(DependencyCapabilities.hasClass(current) ? current : legacy)
            .getConstructor(String.class)
            .newInstance("apeiron.verify.electrolyzer_output");
        result.setBaseMetaTileEntity(new BaseMetaTileEntity());
        result.mWrench = result.mScrewdriver = result.mSoftMallet = result.mHardHammer = result.mSolderingTool = result.mCrowbar = true;
        result.setVoidingMode(VoidingMode.VOID_NONE);
        MTEHatchEnergy energy = new MTEHatchEnergy("apeiron.verify.electrolyzer_native_power", 1, new String[0], null);
        energy.setBaseMetaTileEntity(new BaseMetaTileEntity());
        result.mEnergyHatches.add(energy);
        return result;
    }

    public static void verify() {
        try {
            verifyCapacity();
            verifyRecipe();
            verifyEnergyOutputChannels();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Industrial electrolyzer output verification failed", error);
        }
        Apeiron.LOG.info(
            "Industrial electrolyzer real recipe and shared void-protection verification passed: native/ordinary/ultimate energy, mixed/separate outputs, correct missing-channel errors, finite capacity, filters, input debit and output conservation");
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static VoidProtectionHelper capacity(MTEMultiBlockBase controller, int count) {
        return new VoidProtectionHelper().setMachine(controller, true, true)
            .setItemOutputs(new ItemStack[] { new ItemStack(Items.diamond) })
            .setFluidOutputs(new FluidStack[] { new FluidStack(FluidRegistry.WATER, 1) })
            .setMaxParallel(count)
            .build();
    }

    private static void verifyCapacity() throws ReflectiveOperationException {
        MTEMultiBlockBase controller = controller();
        check(capacity(controller, 1).isItemFull(), "missing item receiver passed preflight");
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        check(controller.addToMachineList(output.getBaseMetaTileEntity(), 0), "assembly rejected by structure scanner");
        VoidProtectionHelper big = capacity(controller, Integer.MAX_VALUE);
        check(
            !big.isItemFull() && !big.isFluidFull() && big.getMaxParallel() == Integer.MAX_VALUE,
            "native void protection rejected unlimited assembly");
        check(
            output.getProvider()
                .getCachedAmountBig()
                .signum() == 0
                && output.getFluidProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
            "preflight inserted outputs");

        controller.mOutputBusses.clear();
        controller.mOutputHatches.clear();
        check(
            controller.addOutputBusToMachineList(tile(0), 0) && controller.addOutputHatchToMachineList(tile(1), 0),
            "separate unlimited outputs rejected");
        check(
            capacity(controller, Integer.MAX_VALUE).getMaxParallel() == Integer.MAX_VALUE,
            "separate outputs retained int capacity limit");

        controller.mOutputBusses.clear();
        controller.mOutputHatches.clear();
        MTEHatchOutputBus physicalBus = new MTEHatchOutputBus(
            "apeiron.verify.electrolyzer_physical_bus",
            1,
            1,
            new String[0],
            null);
        physicalBus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        physicalBus.mInventory[0] = new ItemStack(Items.diamond, 64);
        controller.mOutputBusses.add(physicalBus);
        MTEHatchOutput physicalHatch = new MTEHatchOutput(
            "apeiron.verify.electrolyzer_physical_hatch",
            1,
            new String[0],
            null);
        physicalHatch.setBaseMetaTileEntity(new BaseMetaTileEntity());
        controller.mOutputHatches.add(physicalHatch);
        check(capacity(controller, 1).isItemFull(), "full finite bus was treated as unlimited");
        physicalBus.mInventory[0] = null;
        VoidProtectionHelper finite = capacity(controller, 1000);
        check(finite.getMaxParallel() == 64, "finite item slot capacity ignored");
        physicalHatch.fill(new FluidStack(FluidRegistry.WATER, physicalHatch.getCapacity()), true);
        check(capacity(controller, 1).isFluidFull(), "full finite tank was treated as unlimited");

        controller.mOutputBusses.clear();
        controller.mOutputHatches.clear();
        ItemStack itemCell = AEApi.instance()
            .definitions()
            .items()
            .cell1k()
            .maybeStack(1)
            .get();
        ((IStorageCell) itemCell.getItem()).getConfigAEInventory(itemCell)
            .putAEStackInSlot(0, AEItemStack.create(new ItemStack(Items.emerald)));
        ItemStack fluidCell = new ItemStack(com.glodblock.github.loader.ItemAndBlockHolder.CELL1K);
        ((IStorageCell) fluidCell.getItem()).getConfigAEInventory(fluidCell)
            .putAEStackInSlot(0, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)));
        output.setInventorySlotContents(0, itemCell);
        output.setInventorySlotContents(1, fluidCell);
        // Detached inventories do not receive tile ticks; refresh the real cell partition handlers explicitly.
        output.onContentsChanged(0);
        output.onContentsChanged(1);
        output.getProvider()
            .updateState();
        output.getFluidProvider()
            .updateState();
        controller.addToMachineList(output.getBaseMetaTileEntity(), 0);
        check(
            output.isFiltered() && output.getFluidOutput()
                .isFiltered(),
            "fixture partition did not load: items=" + output.isFiltered()
                + ", fluids="
                + output.getFluidOutput()
                    .isFiltered());
        check(capacity(controller, 1).isItemFull(), "item partition was bypassed");
        controller.mOutputBusses.clear();
        controller.addOutputBusToMachineList(tile(0), 0);
        check(capacity(controller, 1).isFluidFull(), "fluid partition was bypassed");
    }

    private static void verifyRecipe() throws ReflectiveOperationException {
        MTEMultiBlockBase controller = controller();
        RecipeMap<?> map = controller.getRecipeMap();
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.paper, 2))
            .itemOutputs(new ItemStack(Items.diamond, 2), new ItemStack(Items.emerald, 3))
            .fluidOutputs(new FluidStack(FluidRegistry.WATER, 7), new FluidStack(FluidRegistry.LAVA, 5))
            .duration(56)
            .eut(8)
            .build()
            .get();
        map.addRecipe(recipe);
        try {
            MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
            check(controller.addToMachineList(output.getBaseMetaTileEntity(), 0), "mixed output registration failed");
            MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
                ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
            List<IAEStack<?>> stocks = Collections.singletonList(
                BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.paper)), BigInteger.valueOf(8)));
            check(input.addToBufferBig(0, stocks), "electrolyzer input fixture setup");
            controller.mDualInputHatches.add(input);
            input.beginRecipeProcessing();
            try {
                gregtech.api.recipe.check.CheckRecipeResult result = controller.checkProcessing();
                check(result.wasSuccessful(), "native electrolyzer recipe rejected: " + result.getID());
            } finally {
                input.endRecipeProcessing();
            }
            int parallel = ((MultiBlockProcessingAccessor) controller).apeiron$getProcessingLogic()
                .getCurrentParallels();
            check(parallel > 0 && parallel <= 4, "native electrolyzer parallel changed");
            check(
                !((BigWirelessController) controller).getWirelessRecipeState()
                    .isRunning(),
                "native recipe entered Apeiron energy processing");
            check(
                input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .equals(BigInteger.valueOf(8 - parallel * 2L)),
                "native input debit not conserved");
            ItemStack[] items = ((MultiBlockProcessingAccessor) controller).apeiron$getOutputItems();
            FluidStack[] fluids = ((MultiBlockProcessingAccessor) controller).apeiron$getOutputFluids();
            check(items.length == 2 && fluids.length == 2, "native recipe lost output types");
            check(controller.addItemOutputs(items), "native item output rejected by assembly");
            Method fluidOutput = MTEMultiBlockBase.class.getDeclaredMethod("addFluidOutputs", FluidStack[].class);
            fluidOutput.setAccessible(true);
            fluidOutput.invoke(controller, (Object) fluids);
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(BigInteger.valueOf(parallel * 5L))
                    && output.getFluidProvider()
                        .getCachedAmountBig()
                        .equals(BigInteger.valueOf(parallel * 12L)),
                "native mixed output quantities not conserved");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void verifyEnergyOutputChannels() throws ReflectiveOperationException {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            for (boolean ultimate : new boolean[] { false, true })
                for (int missing : new int[] { 0, 1, 2 }) verifyEnergyOutputChannels(ultimate, missing);
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
        }
    }

    private static void verifyEnergyOutputChannels(boolean ultimate, int missing) throws ReflectiveOperationException {
        MTEMultiBlockBase controller = controller();
        controller.mEnergyHatches.clear();
        UUID owner = UUID.randomUUID();
        ApeironMachineTile supply = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        supply.setOwnerUuid(owner);
        check(controller.addToMachineList(supply, 0), "energy registration failed");
        com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = ((BigWirelessController) controller)
            .getWirelessRecipeState();
        state.setParallelSettingBig(BigInteger.ONE);
        state.setVoltageSetting(8);
        state.setTargetDuration(1);
        BigInteger balance = BigInteger.valueOf(100000);
        WirelessNetworkManager.setUserEU(owner, balance);
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        if (missing == 0)
            check(controller.addToMachineList(output.getBaseMetaTileEntity(), 0), "dual output registration failed");
        else check(
            missing == 1 ? controller.addOutputHatchToMachineList(tile(1), 0)
                : controller.addOutputBusToMachineList(tile(0), 0),
            "single output registration failed");
        RecipeMap<?> map = controller.getRecipeMap();
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.paper, 2))
            .itemOutputs(new ItemStack(Items.diamond, 2), new ItemStack(Items.emerald, 3))
            .fluidOutputs(new FluidStack(FluidRegistry.WATER, 7), new FluidStack(FluidRegistry.LAVA, 5))
            .duration(56)
            .eut(8)
            .build()
            .get();
        map.addRecipe(recipe);
        try {
            MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
                ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
            check(
                input.addToBufferBig(
                    0,
                    Collections.singletonList(
                        BigAEStackValues
                            .copyWithSize(AEItemStack.create(new ItemStack(Items.paper)), BigInteger.valueOf(8)))),
                "energy input setup failed");
            controller.mDualInputHatches.add(input);
            CheckRecipeResult result;
            input.beginRecipeProcessing();
            try {
                result = controller.checkProcessing();
            } finally {
                input.endRecipeProcessing();
            }
            if (missing != 0) {
                CheckRecipeResult expected = missing == 1 ? CheckRecipeResultRegistry.ITEM_OUTPUT_FULL
                    : CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
                check(
                    result.equals(expected),
                    "wrong missing output channel: ultimate=" + ultimate
                        + ", missing="
                        + missing
                        + ", reported="
                        + result.getDisplayString());
                check(
                    !state.isRunning() && input.getBuffers()
                        .get(0)
                        .getItemAmountBig()
                        .equals(BigInteger.valueOf(8)),
                    "failed output check consumed inputs or started recipe");
                check(
                    WirelessNetworkManager.getUserEU(owner)
                        .equals(balance),
                    "failed output check charged energy");
                return;
            }
            check(
                result.wasSuccessful() && state.getParallelsBig()
                    .equals(BigInteger.ONE),
                "dual output recipe rejected");
            check(
                input.getBuffers()
                    .get(0)
                    .getItemAmountBig()
                    .equals(BigInteger.valueOf(6)),
                "exact input debit changed");
            Method complete = MTEMultiBlockBase.class.getDeclaredMethod("outputAfterRecipe");
            complete.setAccessible(true);
            complete.invoke(controller);
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(BigInteger.valueOf(5))
                    && output.getFluidProvider()
                        .getCachedAmountBig()
                        .equals(BigInteger.valueOf(12)),
                "dual output completion lost a channel");
        } finally {
            map.getBackend()
                .removeRecipe(recipe);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Industrial electrolyzer output: " + message);
    }
}
