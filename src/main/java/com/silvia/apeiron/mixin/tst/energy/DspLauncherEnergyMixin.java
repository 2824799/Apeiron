package com.silvia.apeiron.mixin.tst.energy;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.Nxer.TwistSpaceTechnology.common.GTCMItemList;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.logic.DSP_DataCell;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.logic.DSP_Values;
import com.Nxer.TwistSpaceTechnology.system.DysonSphereProgram.machines.TST_DSPLauncher;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInventory;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.MiningOutputCounts;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

/** The launcher has no ProcessingLogic: retain its DSP rules around the shared exact input/energy/output ledger. */
@Mixin(value = TST_DSPLauncher.class, remap = false)
public abstract class DspLauncherEnergyMixin extends GTCM_MultiMachineBase<TST_DSPLauncher> {

    @Shadow
    private DSP_DataCell dspDataCell;
    @Shadow
    private long overloadTime;

    @Shadow
    protected abstract double calculateOverloadSpeedUp();

    protected DspLauncherEnergyMixin(String name) {
        super(name);
    }

    @Inject(method = "checkProcessing", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$launchBatch(CallbackInfoReturnable<CheckRecipeResult> cir) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(this);
        if (hatch == null) return;
        WirelessRecipeState state = ((BigWirelessController) this).getWirelessRecipeState();
        if (state.isRunning()) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;
        Set<IDualInputInventory> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IDualInputHatch input : mDualInputHatches) {
            for (java.util.Iterator<? extends IDualInputInventory> it = input.inventories(); it.hasNext();) {
                IDualInputInventory inventory = it.next();
                if (!seen.add(inventory) || inventory.isEmpty()) continue;
                CheckRecipeResult found = apeiron$process(hatch, inventory.getItemInputs(), inventory.getFluidInputs());
                if (found.wasSuccessful()) {
                    cir.setReturnValue(found);
                    return;
                }
                if (found != CheckRecipeResultRegistry.NO_RECIPE) result = found;
            }
        }
        CheckRecipeResult found = apeiron$process(
            hatch,
            getStoredInputs().toArray(new ItemStack[0]),
            new FluidStack[0]);
        updateSlots();
        cir.setReturnValue(found == CheckRecipeResultRegistry.NO_RECIPE ? result : found);
    }

    @Unique
    private CheckRecipeResult apeiron$process(MTEInfiniteEnergyHatch hatch, ItemStack[] items, FluidStack[] fluids) {
        BigRecipeInventory inventory = new BigRecipeInventory(this, items, fluids);
        List<IAEStack<?>> stocks = inventory.getStacksBig();
        BigInteger[] debit = new BigInteger[stocks.size()];
        Arrays.fill(debit, BigInteger.ZERO);
        BigInteger remainingTime = BigInteger.valueOf(Long.MAX_VALUE - Math.max(0, overloadTime));
        BigInteger perWarper = BigInteger.valueOf(DSP_Values.secondsOfEverySpaceWarperProvideToOverloadTime)
            .multiply(BigInteger.valueOf(20));
        for (int i = 0; i < stocks.size() && perWarper.signum() > 0 && remainingTime.signum() > 0; i++) {
            if (!(stocks.get(i) instanceof IAEItemStack)) continue;
            ItemStack type = ((IAEItemStack) stocks.get(i)).getItemStack();
            if (!TST_DSPLauncher.SPACE_WARPER.equalItemStack(type)) continue;
            BigInteger count = remainingTime.add(perWarper)
                .subtract(BigInteger.ONE)
                .divide(perWarper);
            if (!BigAEStackValues.isInfinite(stocks.get(i))) count = count.min(BigAEStackValues.get(stocks.get(i)));
            debit[i] = count;
            remainingTime = remainingTime.subtract(count.multiply(perWarper))
                .max(BigInteger.ZERO);
        }
        boolean hasFuel = Arrays.stream(debit)
            .anyMatch(value -> value.signum() > 0);
        long nextOverload = hasFuel ? Long.MAX_VALUE - remainingTime.longValueExact() : overloadTime;
        double speed = apeiron$speedWithFuel(nextOverload);
        WirelessRecipeState state = ((BigWirelessController) this).getWirelessRecipeState();
        CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;
        for (int i = 0; i < stocks.size(); i++) {
            if (!(stocks.get(i) instanceof IAEItemStack)) continue;
            ItemStack type = ((IAEItemStack) stocks.get(i)).getItemStack();
            boolean sail = TST_DSPLauncher.SOLAR_SAIL.equalItemStack(type);
            if (!sail && !TST_DSPLauncher.LAUNCH_VEHICLE.equalItemStack(type)) continue;
            long eut = sail ? DSP_Values.EUTOfLaunchingSolarSail : DSP_Values.EUTOfLaunchingNode;
            int baseTicks = sail ? DSP_Values.ticksOfLaunchingSolarSail : DSP_Values.ticksOfLaunchingNode;
            int nativeTicks = Math.max(1, (int) (baseTicks / Math.max(1, speed)));
            BigInteger perLaunch = BigInteger.valueOf(eut)
                .multiply(BigInteger.valueOf(nativeTicks));
            if (!hatch.isUltimate() && InfiniteEnergyHatches.processingVoltage(this) < eut) {
                result = CheckRecipeResultRegistry.insufficientPower(eut);
                continue;
            }
            // DSP itself stores counts in longs; never let a valid exact batch wrap its native counters.
            BigInteger room = BigInteger.valueOf(Long.MAX_VALUE - (sail ? 0 : 1))
                .subtract(BigInteger.valueOf(sail ? dspDataCell.getDSPSolarSail() : dspDataCell.getDSPNode()))
                .max(BigInteger.ZERO);
            BigInteger count = state.getLimit()
                .applyTo(room)
                .min(
                    hatch.getAvailableEUBig()
                        .divide(perLaunch));
            if (!hatch.isUltimate())
                count = count.min(BigInteger.valueOf(InfiniteEnergyHatches.processingVoltage(this) / eut));
            // Merge identical inputs across slots while keeping separate pattern buffers isolated.
            BigInteger available = BigInteger.ZERO;
            boolean infinite = false;
            for (IAEStack<?> stock : stocks) if (stock instanceof IAEItemStack
                && (sail ? TST_DSPLauncher.SOLAR_SAIL : TST_DSPLauncher.LAUNCH_VEHICLE)
                    .equalItemStack(((IAEItemStack) stock).getItemStack())) {
                        available = available.add(BigAEStackValues.get(stock));
                        infinite |= BigAEStackValues.isInfinite(stock);
                    }
            if (!infinite) count = count.min(available);
            if (count.signum() == 0) {
                if (available.signum() > 0 && hatch.getAvailableEUBig()
                    .compareTo(perLaunch) < 0)
                    result = CheckRecipeResultRegistry.insufficientPower(
                        perLaunch.min(BigInteger.valueOf(Long.MAX_VALUE))
                            .longValue());
                continue;
            }
            BigMachineOutputQueue outputs = new BigMachineOutputQueue();
            if (!sail) {
                if (protectsExcessItem()) {
                    BigMachineOutputQueue maximum = new BigMachineOutputQueue();
                    maximum.addItem(GTCMItemList.EmptySmallLaunchVehicle.get(1), count);
                    CheckRecipeResult capacity = BigRecipeOutputCapacity.check(this, maximum.snapshotOutputsUnsorted());
                    if (!capacity.wasSuccessful()) {
                        result = capacity;
                        continue;
                    }
                }
                outputs.addItem(
                    GTCMItemList.EmptySmallLaunchVehicle.get(1),
                    MiningOutputCounts.binomial(count, 99, 100, XSTR.XSTR_INSTANCE));
            }
            BigInteger remaining = count;
            for (int slot = 0; slot < stocks.size() && remaining.signum() > 0; slot++) {
                IAEStack<?> stock = stocks.get(slot);
                if (!(stock instanceof IAEItemStack)
                    || !(sail ? TST_DSPLauncher.SOLAR_SAIL : TST_DSPLauncher.LAUNCH_VEHICLE)
                        .equalItemStack(((IAEItemStack) stock).getItemStack()))
                    continue;
                debit[slot] = BigAEStackValues.isInfinite(stock) ? remaining
                    : remaining.min(BigAEStackValues.get(stock));
                remaining = remaining.subtract(debit[slot]);
            }
            inventory.consume(debit);
            overloadTime = nextOverload;
            if (sail) dspDataCell.addDSPSolarSail(count.longValueExact());
            else dspDataCell.addDSPNode(count.longValueExact());
            int duration = hatch.isUltimate() ? InfiniteEnergyHatches.targetDuration(this) : nativeTicks;
            state.startExact(count, perLaunch.multiply(count), duration, outputs, hatch.isUltimate());
            mMaxProgresstime = duration;
            mEfficiency = mEfficiencyIncrease = 10000;
            mEUt = 0;
            lEUt = 0;
            mOutputItems = null;
            mOutputFluids = null;
            updateSlots();
            markDirty();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        if (hasFuel) {
            inventory.consume(debit);
            overloadTime = nextOverload;
            updateSlots();
            markDirty();
        }
        return result;
    }

    @Unique
    private double apeiron$speedWithFuel(long nextOverload) {
        // Evaluate the native bonus before committing the single fuel + launch input transaction.
        long previous = overloadTime;
        try {
            overloadTime = nextOverload;
            return calculateOverloadSpeedUp();
        } finally {
            overloadTime = previous;
        }
    }
}
