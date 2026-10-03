package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.tst.output.BigTstOutputLists;
import com.silvia.apeiron.common.machine.tst.output.TstOutputCapacity;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Adapts the shared ME path without replacing recipe completion or subclass callbacks. */
@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase",
    remap = false)
public abstract class MultiMachineOutputBigMixin implements BigTstOutputController {

    @Shadow
    @Final
    protected List<ItemStackLong> meOutputQueue;
    @Shadow
    @Final
    protected List<FluidStackLong> meFluidOutputQueue;
    @Unique
    private final BigMachineOutputQueue apeiron$pending = new BigMachineOutputQueue();
    @Unique
    private static final String ITEM_COUNTS = "ApeironTstRecipeItemCounts";
    @Unique
    private static final String FLUID_COUNTS = "ApeironTstRecipeFluidCounts";

    @Unique
    private MTEMultiBlockBase apeiron$machine() {
        return (MTEMultiBlockBase) (Object) this;
    }

    @Inject(method = "isMEOutputEnabled", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$recognizeOutputs(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        final MTEMultiBlockBase machine = apeiron$machine();
        boolean any = false;
        for (Object bus : machine.getOutputBusses()) {
            if (!(bus instanceof MTEHatchOutputBusME) && !(bus instanceof MTEBoundlessMEOutputBus)) return;
            any = true;
        }
        for (Object hatch : machine.getOutputHatches()) {
            if (!(hatch instanceof MTEHatchOutputME) && !(hatch instanceof MTEBoundlessMEOutputHatch)) return;
            any = true;
        }
        if (any) cir.setReturnValue(true);
    }

    @Inject(
        method = "mergeItemIntoMEOutputQueue(Lnet/minecraft/item/ItemStack;J)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$mergeItem(ItemStack type, long amount, CallbackInfo ci) {
        if (amount > 0) mergeItemIntoMEOutputQueueBig(type, BigInteger.valueOf(amount));
        ci.cancel();
    }

    @Inject(
        method = "mergeFluidIntoMEOutputQueue(Lnet/minecraftforge/fluids/FluidStack;J)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$mergeFluid(FluidStack type, long amount, CallbackInfo ci) {
        if (amount > 0) mergeFluidIntoMEOutputQueueBig(type, BigInteger.valueOf(amount));
        ci.cancel();
    }

    @Inject(
        method = "replaceMEOutputQueues(Ljava/util/List;Ljava/util/List;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$replace(List<ItemStackLong> items, List<FluidStackLong> fluids, CallbackInfo ci) {
        meOutputQueue.clear();
        meFluidOutputQueue.clear();
        for (ItemStackLong entry : items)
            mergeItemIntoMEOutputQueueBig(entry.itemStack(), BigTstOutputLists.amount(entry));
        for (FluidStackLong entry : fluids)
            mergeFluidIntoMEOutputQueueBig(entry.fluidStack(), BigTstOutputLists.amount(entry));
        ci.cancel();
    }

    @Inject(method = "checkMEOutputCapacity", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$checkCapacity(List<ItemStackLong> items, List<FluidStackLong> fluids,
        CallbackInfoReturnable<CheckRecipeResult> cir) {
        final MTEMultiBlockBase machine = apeiron$machine();
        if (machine.protectsExcessItem() && !TstOutputCapacity.itemsFit(items, machine.getOutputBusses()))
            cir.setReturnValue(CheckRecipeResultRegistry.ITEM_OUTPUT_FULL);
        else if (machine.protectsExcessFluid() && !TstOutputCapacity.fluidsFit(fluids, machine.getOutputHatches()))
            cir.setReturnValue(CheckRecipeResultRegistry.FLUID_OUTPUT_FULL);
        else cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }

    @Inject(method = "outputMEItemQueue", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$completeItems(List<ItemStackLong> items, CallbackInfo ci) {
        for (ItemStackLong entry : items) apeiron$pending.addItem(entry.itemStack(), BigTstOutputLists.amount(entry));
        apeiron$dirty();
        ci.cancel();
    }

    @Inject(method = "outputMEFluidQueue", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$completeFluids(List<FluidStackLong> fluids, CallbackInfo ci) {
        for (FluidStackLong entry : fluids)
            apeiron$pending.addFluid(entry.fluidStack(), BigTstOutputLists.amount(entry));
        apeiron$dirty();
        ci.cancel();
    }

    @Inject(method = "outputAfterRecipe", at = @At("RETURN"), require = 1)
    private void apeiron$flushCompleted(CallbackInfo ci) {
        flushOutputsBig();
        if (this instanceof com.silvia.apeiron.api.machine.parallel.BigWirelessController)
            WirelessControllerEnergy.complete(apeiron$machine());
    }

    @Inject(method = "outputAfterRecipe", at = @At("HEAD"), require = 1)
    private void apeiron$retainDisconnectedOutputs(CallbackInfo ci) {
        if (((com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase<?>) (Object) this)
            .isMEOutputEnabled()) return;
        for (ItemStackLong entry : meOutputQueue)
            apeiron$pending.addItem(entry.itemStack(), BigTstOutputLists.amount(entry));
        for (FluidStackLong entry : meFluidOutputQueue)
            apeiron$pending.addFluid(entry.fluidStack(), BigTstOutputLists.amount(entry));
        meOutputQueue.clear();
        meFluidOutputQueue.clear();
        if (!apeiron$pending.isEmpty()) apeiron$dirty();
    }

    @Inject(method = "outputItemToMENetwork", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$legacyItems(ItemStack type, long amount, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(
            amount <= 0 ? amount
                : BigAEStackValues.saturatedLong(outputItemToMENetworkBig(type, BigInteger.valueOf(amount))));
    }

    @Inject(method = "outputFluidToMENetwork", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$legacyFluids(FluidStack type, long amount, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(
            amount <= 0 ? amount
                : BigAEStackValues.saturatedLong(outputFluidToMENetworkBig(type, BigInteger.valueOf(amount))));
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$save(NBTTagCompound tag, CallbackInfo ci) {
        tag.setTag(ITEM_COUNTS, BigTstOutputLists.saveItems(meOutputQueue));
        tag.setTag(FLUID_COUNTS, BigTstOutputLists.saveFluids(meFluidOutputQueue));
        saveProducedOutputsBig(tag);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$load(NBTTagCompound tag, CallbackInfo ci) {
        if (tag.hasKey(ITEM_COUNTS)) BigTstOutputLists.restoreItems(meOutputQueue, tag.getTagList(ITEM_COUNTS, 10));
        if (tag.hasKey(FLUID_COUNTS))
            BigTstOutputLists.restoreFluids(meFluidOutputQueue, tag.getTagList(FLUID_COUNTS, 10));
        apeiron$pending.load(tag);
    }

    @Override
    public void mergeItemIntoMEOutputQueueBig(ItemStack type, BigInteger amount) {
        BigTstOutputLists.mergeItem(meOutputQueue, type, amount);
    }

    @Override
    public void mergeFluidIntoMEOutputQueueBig(FluidStack type, BigInteger amount) {
        BigTstOutputLists.mergeFluid(meFluidOutputQueue, type, amount);
    }

    @Override
    public BigInteger outputItemToMENetworkBig(ItemStack type, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative item output");
        if (type == null || amount.signum() == 0) return amount;
        final BigMachineOutputQueue queue = new BigMachineOutputQueue();
        queue.addItem(type, amount);
        apeiron$flush(queue);
        return queue.getItemAmountBig();
    }

    @Override
    public BigInteger outputFluidToMENetworkBig(FluidStack type, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative fluid output");
        if (type == null || amount.signum() == 0) return amount;
        final BigMachineOutputQueue queue = new BigMachineOutputQueue();
        queue.addFluid(type, amount);
        apeiron$flush(queue);
        return queue.getFluidAmountBig();
    }

    @Override
    public BigInteger getRecipeItemOutputBig() {
        return meOutputQueue.stream()
            .map(BigTstOutputLists::amount)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    @Override
    public BigInteger getRecipeFluidOutputBig() {
        return meFluidOutputQueue.stream()
            .map(BigTstOutputLists::amount)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    @Override
    public BigInteger getPendingItemOutputBig() {
        return apeiron$pending.getItemAmountBig();
    }

    @Override
    public BigInteger getPendingFluidOutputBig() {
        return apeiron$pending.getFluidAmountBig();
    }

    @Override
    public void saveProducedOutputsBig(NBTTagCompound tag) {
        apeiron$pending.save(tag);
    }

    @Override
    public void flushOutputsBig() {
        apeiron$flush(apeiron$pending);
    }

    @Unique
    private void apeiron$flush(BigMachineOutputQueue queue) {
        final MTEMultiBlockBase machine = apeiron$machine();
        if (!queue.isEmpty() && queue.flush(
            machine.getOutputBusses(),
            machine.getOutputHatches(),
            machine.protectsExcessItem(),
            machine.protectsExcessFluid())) apeiron$dirty();
    }

    @Unique
    private void apeiron$dirty() {
        apeiron$machine().markDirty();
    }
}
