package com.silvia.apeiron.common.machine.tst.verification;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.Nxer.TwistSpaceTechnology.common.GTCMItemList;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.logic.DSP_DataCell;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.logic.DSP_Values;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.logic.DSP_WorldSavedData;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.machines.TST_DSPLauncher;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.machines.TST_DSPReceiver;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Current native DSP controllers: exact launch transactions and photon conversion/completion. */
public final class DysonSphereMachineSmoke {

    private DysonSphereMachineSmoke() {}

    private static final class Launcher extends TST_DSPLauncher {

        final DSP_DataCell cell = new DSP_DataCell();

        Launcher() {
            super("apeiron.verify.dsp_launcher");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            set(this, "dspDataCell", cell);
            set(this, "motorTier", 4);
            set(this, "wirelessMode", false);
        }

        @Override
        protected void sendStartMultiBlockSoundLoop() {}

        int nativeTicks(boolean sail) {
            return Math.max(
                1,
                (int) ((sail ? DSP_Values.ticksOfLaunchingSolarSail : DSP_Values.ticksOfLaunchingNode)
                    / Math.max(1, calculateOverloadSpeedUp())));
        }

        void complete() {
            outputAfterRecipe();
        }
    }

    private static final class Receiver extends TST_DSPReceiver {

        Receiver(int mode, UUID owner, long high, long low) {
            super("apeiron.verify.dsp_receiver");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
            machineMode = mode;
            ownerUUID = owner;
            storageEUMAX = high;
            storageEU = low;
        }

        @Override
        protected void checkGravitationalLensInput() {}

        @Override
        protected void syncDSPData() {}

        BigInteger stored() {
            return BigInteger.valueOf(storageEUMAX)
                .multiply(BigInteger.valueOf(Integer.MAX_VALUE))
                .add(BigInteger.valueOf(storageEU));
        }

        void complete() {
            outputAfterRecipe();
        }
    }

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        DSP_WorldSavedData dspSave = DSP_WorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            DSP_WorldSavedData.INSTANCE = new DSP_WorldSavedData();
            TST_DSPLauncher.initStatics();
            TST_DSPReceiver.initStatics();
            for (boolean ultimate : new boolean[] { false, true }) for (boolean sail : new boolean[] { false, true })
                for (boolean boosted : new boolean[] { false, true }) verifyLaunch(ultimate, sail, boosted);
            verifyLaunchLimits();
            verifyNativeLaunch();
            verifyPhotons();
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
            DSP_WorldSavedData.INSTANCE = dspSave;
        }
        Apeiron.LOG.info(
            "Dyson sphere machines verification passed: native fallback, ordinary/ultimate parallel/time/EU, sails/nodes/warpers, budget/voltage/count limits, exact photon conversion/reload/disconnection/completion");
    }

    private static WirelessRecipeState state(MTEMultiBlockBase machine) {
        return ((BigWirelessController) machine).getWirelessRecipeState();
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static UUID energy(Launcher machine, boolean ultimate, BigInteger limit, BigInteger balance, long voltage) {
        ApeironMachineTile hatch = tile(
            ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET);
        UUID owner = UUID.randomUUID();
        hatch.setOwnerUuid(owner);
        check(machine.addToMachineList(hatch, 0), "energy registration");
        state(machine).setParallelSettingBig(limit);
        state(machine).setTargetDuration(3);
        state(machine).setVoltageSetting(voltage);
        WirelessNetworkManager.setUserEU(owner, balance);
        return owner;
    }

    private static MTEInfinitePatternInputAssembly input(Launcher machine, IAEStack<?>... stacks) {
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(
            ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET).getMetaTileEntity();
        check(input.addToBufferBig(0, Arrays.asList(stacks)), "seed pattern buffer");
        machine.mDualInputHatches.add(input);
        return input;
    }

    private static MTEInfiniteMEOutputAssembly output(MTEMultiBlockBase machine) {
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        output.getProvider()
            .setCheckMode(true);
        check(machine.addToMachineList(output.getBaseMetaTileEntity(), 0), "output registration");
        return output;
    }

    private static void verifyLaunch(boolean ultimate, boolean sail, boolean boosted) {
        Launcher machine = new Launcher();
        BigInteger count = ultimate ? BigInteger.valueOf(Integer.MAX_VALUE)
            .multiply(BigInteger.valueOf(4))
            .add(BigInteger.valueOf(17)) : BigInteger.valueOf(7);
        long eut = sail ? DSP_Values.EUTOfLaunchingSolarSail : DSP_Values.EUTOfLaunchingNode;
        BigInteger budget = BigInteger.TEN.pow(35);
        UUID owner = energy(machine, ultimate, count, budget, eut * 7);
        IAEStack<?> material = BigAEStackValues.copyWithSize(
            AEItemStack.create((sail ? GTCMItemList.SolarSail : GTCMItemList.SmallLaunchVehicle).get(1)),
            count.add(BigInteger.ONE));
        MTEInfinitePatternInputAssembly input = boosted
            ? input(machine, material, AEItemStack.create(GTCMItemList.SpaceWarper.get(2)))
            : input(machine, material);
        MTEInfiniteMEOutputAssembly output = output(machine);
        check(((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe(), "launch rejected");
        check(
            state(machine).getParallelsBig()
                .equals(count),
            "parallel mismatch");
        check(
            (sail ? machine.cell.getDSPSolarSail() : machine.cell.getDSPNode()) == count.longValueExact(),
            "DSP increment mismatch");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(BigInteger.ONE),
            "launch/fuel debit mismatch");
        int nativeTicks = machine.nativeTicks(sail);
        check(machine.mMaxProgresstime == (ultimate ? 3 : nativeTicks), "duration mismatch");
        BigInteger total = BigInteger.valueOf(eut)
            .multiply(BigInteger.valueOf(nativeTicks))
            .multiply(count);
        check(
            state(machine).getTotalEUBig()
                .equals(total),
            "native motor/overload energy mismatch");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(budget),
            "upfront debit");
        BigInteger returned = state(machine).getOutputDisplay()
            .stream()
            .map(BigAEStackValues::get)
            .reduce(BigInteger.ZERO, BigInteger::add);
        check(
            sail ? returned.signum() == 0 : returned.signum() >= 0 && returned.compareTo(count) <= 0,
            "invalid vehicle return");
        if (ultimate && !sail) check(
            returned.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0,
            "large vehicle returns did not reach the exact output channel");
        for (int tick = 0; tick < machine.mMaxProgresstime; tick++)
            check(machine.onRunningTick(null), "wireless tick failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(budget.subtract(total)),
            "total energy mismatch");
        machine.complete();
        machine.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(returned),
            "return output lost or duplicated");
        check(
            ((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe() && state(machine).getParallelsBig()
                .equals(BigInteger.ONE),
            "remaining launch rejected");
        for (int tick = 0; tick < machine.mMaxProgresstime; tick++)
            check(machine.onRunningTick(null), "last launch energy");
        machine.complete();
        check(
            !((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe() && machine.getCheckRecipeResult()
                .equals(CheckRecipeResultRegistry.NO_RECIPE),
            "empty launcher did not stop cleanly");
    }

    private static void verifyLaunchLimits() {
        Launcher machine = new Launcher();
        BigInteger per = BigInteger.valueOf(DSP_Values.EUTOfLaunchingSolarSail)
            .multiply(BigInteger.valueOf(machine.nativeTicks(true)));
        energy(machine, true, BigInteger.ZERO, per.multiply(BigInteger.valueOf(2)), 1);
        input(machine, AEItemStack.create(GTCMItemList.SolarSail.get(10)));
        check(
            ((MultiBlockProcessingAccessor) (Object) machine).apeiron$checkRecipe() && state(machine).getParallelsBig()
                .intValueExact() == 2,
            "budget cap");
        Launcher lowVoltage = new Launcher();
        energy(
            lowVoltage,
            false,
            BigInteger.TEN,
            per.multiply(BigInteger.TEN),
            DSP_Values.EUTOfLaunchingSolarSail - 1L);
        MTEInfinitePatternInputAssembly untouched = input(
            lowVoltage,
            AEItemStack.create(GTCMItemList.SolarSail.get(10)));
        check(
            !((MultiBlockProcessingAccessor) (Object) lowVoltage).apeiron$checkRecipe() && untouched.getBuffers()
                .get(0)
                .getItemAmountBig()
                .intValueExact() == 10 && lowVoltage.cell.getDSPSolarSail() == 0,
            "voltage rejection mutated inputs");
        Launcher nearFull = new Launcher();
        nearFull.cell.setDSPSolarSail(Long.MAX_VALUE - 2);
        energy(nearFull, true, BigInteger.TEN.pow(30), BigInteger.TEN.pow(40), 1);
        input(nearFull, AEItemStack.create(GTCMItemList.SolarSail.get(10)));
        check(
            ((MultiBlockProcessingAccessor) (Object) nearFull).apeiron$checkRecipe()
                && state(nearFull).getParallelsBig()
                    .intValueExact() == 2
                && nearFull.cell.getDSPSolarSail() == Long.MAX_VALUE,
            "DSP counter overflow");
    }

    private static void verifyNativeLaunch() {
        Launcher machine = new Launcher();
        ItemStack sail = GTCMItemList.SolarSail.get(2);
        gregtech.api.metatileentity.implementations.MTEHatchInputBus bus = new gregtech.api.metatileentity.implementations.MTEHatchInputBus(
            "apeiron.verify.sails",
            1,
            new String[0],
            null);
        bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        bus.setInventorySlotContents(0, sail);
        machine.addToMachineList(bus.getBaseMetaTileEntity(), 0);
        state(machine).setParallelSettingBig(BigInteger.valueOf(100));
        state(machine).setTargetDuration(1);
        check(
            machine.checkProcessing()
                .wasSuccessful() && machine.cell.getDSPSolarSail() == 1
                && !state(machine).isRunning()
                && machine.mMaxProgresstime == machine.nativeTicks(true),
            "native fallback changed");
    }

    private static void verifyPhotons() {
        UUID owner = UUID.randomUUID();
        Receiver receiver = new Receiver(1, owner, Long.MAX_VALUE, Long.MAX_VALUE - 17);
        MTEInfiniteMEOutputAssembly output = output(receiver);
        BigInteger original = receiver.stored();
        BigInteger[] expected = original.divideAndRemainder(BigInteger.valueOf(DSP_Values.EUPerCriticalPhoton));
        check(
            receiver.checkProcessing()
                .wasSuccessful(),
            "photon cycle rejected");
        check(
            ((BigTstOutputController) (Object) receiver).getRecipeItemOutputBig()
                .equals(expected[0])
                && receiver.stored()
                    .equals(expected[1])
                && receiver.mOutputItems == null,
            "photon count/remainder mismatch");
        check(
            output.getProvider()
                .getCachedAmountBig()
                .signum() == 0,
            "early photon output");
        NBTTagCompound tag = new NBTTagCompound();
        receiver.saveNBTData(tag);
        Receiver loaded = new Receiver(1, owner, 0, 0);
        loaded.loadNBTData(tag);
        loaded.complete();
        check(
            ((BigTstOutputController) (Object) loaded).getPendingItemOutputBig()
                .equals(expected[0]),
            "disconnected completion lost photons");
        loaded.addToMachineList(output.getBaseMetaTileEntity(), 0);
        ((BigTstOutputController) (Object) loaded).flushOutputsBig();
        loaded.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(expected[0]),
            "reload/retry photon duplication");
        check(
            loaded.stored()
                .equals(expected[1]),
            "reload energy remainder");
        Receiver electric = new Receiver(0, owner, 123, 456);
        BigInteger energy = electric.stored();
        WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
        output(electric);
        check(
            electric.checkProcessing()
                .equals(CheckRecipeResultRegistry.GENERATING)
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(energy)
                && ((BigTstOutputController) (Object) electric).getRecipeItemOutputBig()
                    .signum() == 0,
            "power generation changed");
    }

    private static void set(Launcher machine, String field, Object value) {
        try {
            Field member = TST_DSPLauncher.class.getDeclaredField(field);
            member.setAccessible(true);
            member.set(machine, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Dyson sphere verification: " + message);
    }
}
