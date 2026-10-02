package com.silvia.apeiron.mixin.tectech;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.tectech.BigEyeOfHarmonyOutput;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.BigOutputScaling;
import com.silvia.apeiron.math.BigValueCodec;

import gregtech.api.interfaces.tileentity.IGregTechDeviceInformation;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;
import tectech.util.FluidStackLong;
import tectech.util.ItemStackLong;

/** Changes output quantities and transport while keeping recipe completion and other mods' lifecycle hooks intact. */
@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyBigOutputMixin implements BigEyeOfHarmonyOutput {

    @Shadow
    private List<ItemStackLong> outputItems;
    @Shadow
    private List<FluidStackLong> outputFluids;
    @Shadow
    private double yield;
    @Shadow
    private long successfulParallelAmount;
    @Shadow
    private long parallelAmount;
    @Shadow
    private long currentRecipeRocketTier;
    @Shadow
    private double successChance;
    @Unique
    private final Map<ItemStackLong, BigInteger> apeiron$itemAmounts = new IdentityHashMap<>();
    @Unique
    private final Map<FluidStackLong, BigInteger> apeiron$fluidAmounts = new IdentityHashMap<>();
    @Unique
    private final BigMachineOutputQueue apeiron$outputs = new BigMachineOutputQueue();
    @Unique
    private BigInteger apeiron$currentItemAmount;
    @Unique
    private BigInteger apeiron$currentFluidAmount;

    @Inject(method = "processRecipe", at = @At("HEAD"), require = 1)
    private void apeiron$beginRecipe(final EyeOfHarmonyRecipe recipe,
        final CallbackInfoReturnable<CheckRecipeResult> cir) {
        apeiron$itemAmounts.clear();
        apeiron$fluidAmounts.clear();
    }

    @Redirect(
        method = "processRecipe(Ltectech/recipe/EyeOfHarmonyRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;",
        remap = false,
        at = @At(value = "INVOKE", target = "Ltectech/recipe/EyeOfHarmonyRecipe;getOutputItems()Ljava/util/ArrayList;"),
        require = 1)
    private ArrayList<ItemStackLong> apeiron$captureItemBases(final EyeOfHarmonyRecipe recipe) {
        final ArrayList<ItemStackLong> items = recipe.getOutputItems();
        for (final ItemStackLong stack : items) apeiron$itemAmounts.put(stack, BigInteger.valueOf(stack.stackSize));
        return items;
    }

    @Redirect(
        method = "processRecipe(Ltectech/recipe/EyeOfHarmonyRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Ltectech/recipe/EyeOfHarmonyRecipe;getOutputFluids()Ljava/util/ArrayList;"),
        require = 1)
    private ArrayList<FluidStackLong> apeiron$captureFluidBases(final EyeOfHarmonyRecipe recipe) {
        final ArrayList<FluidStackLong> fluids = recipe.getOutputFluids();
        for (final FluidStackLong stack : fluids) apeiron$fluidAmounts.put(stack, BigInteger.valueOf(stack.amount));
        return fluids;
    }

    @Inject(
        method = "processRecipe(Ltectech/recipe/EyeOfHarmonyRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;",
        at = @At("RETURN"),
        remap = false,
        require = 1)
    private void apeiron$scaleRecipeOutputs(final EyeOfHarmonyRecipe recipe,
        final CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!cir.getReturnValue()
            .wasSuccessful()) return;
        for (final ItemStackLong stack : outputItems) apeiron$scaleItem(stack, stack.stackSize);
        for (final FluidStackLong stack : outputFluids) apeiron$scaleFluid(stack, stack.amount);
    }

    @Unique
    private void apeiron$scaleItem(final ItemStackLong stack, final long legacyScaled) {
        final BigInteger amount = BigOutputScaling.scale(
            apeiron$itemAmounts.getOrDefault(stack, BigInteger.valueOf(stack.stackSize)),
            yield,
            successfulParallelAmount);
        apeiron$itemAmounts.put(stack, amount);
        stack.stackSize = new AdaptiveInteger(amount).longValueSaturated();
    }

    @Unique
    private void apeiron$scaleFluid(final FluidStackLong stack, final long legacyScaled) {
        final BigInteger amount = BigOutputScaling.scale(
            apeiron$fluidAmounts.getOrDefault(stack, BigInteger.valueOf(stack.amount)),
            yield,
            successfulParallelAmount);
        apeiron$fluidAmounts.put(stack, amount);
        stack.amount = new AdaptiveInteger(amount).longValueSaturated();
    }

    @Inject(method = "outputAfterRecipe_EM", at = @At("HEAD"), require = 1)
    private void apeiron$beginOutput(final CallbackInfo ci) {
        apeiron$currentItemAmount = null;
        apeiron$currentFluidAmount = null;
    }

    @Redirect(
        method = "outputAfterRecipe_EM",
        at = @At(value = "FIELD", target = "Ltectech/util/ItemStackLong;stackSize:J", opcode = Opcodes.GETFIELD),
        require = 1)
    private long apeiron$readItemOutput(final ItemStackLong stack) {
        apeiron$currentItemAmount = apeiron$itemAmounts.getOrDefault(stack, BigInteger.valueOf(stack.stackSize));
        return stack.stackSize;
    }

    @Redirect(
        method = "outputAfterRecipe_EM",
        at = @At(value = "FIELD", target = "Ltectech/util/FluidStackLong;amount:J", opcode = Opcodes.GETFIELD),
        require = 1)
    private long apeiron$readFluidOutput(final FluidStackLong stack) {
        apeiron$currentFluidAmount = apeiron$fluidAmounts.getOrDefault(stack, BigInteger.valueOf(stack.amount));
        return stack.amount;
    }

    @Inject(method = "outputItemToAENetwork", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$routeItem(final ItemStack type, final long amount, final CallbackInfo ci) {
        final BigInteger exact = apeiron$currentItemAmount == null ? BigInteger.valueOf(amount)
            : apeiron$currentItemAmount;
        apeiron$currentItemAmount = null;
        if (exact.signum() > 0) outputItemToAENetworkBig(type, exact);
        ci.cancel();
    }

    @Inject(method = "outputFluidToAENetwork", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$routeFluid(final FluidStack type, final long amount, final CallbackInfo ci) {
        final BigInteger exact = apeiron$currentFluidAmount == null ? BigInteger.valueOf(amount)
            : apeiron$currentFluidAmount;
        apeiron$currentFluidAmount = null;
        if (exact.signum() > 0) outputFluidToAENetworkBig(type, exact);
        ci.cancel();
    }

    @Redirect(
        method = "outputFailedChance",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;outputFluidToAENetwork(Lnet/minecraftforge/fluids/FluidStack;J)V"),
        require = 1)
    private void apeiron$failedSpacetime(final MTEEyeOfHarmony owner, final FluidStack type, final long legacyAmount) {
        final long failed = Math.subtractExact(parallelAmount, successfulParallelAmount);
        final int exponent = Math.toIntExact(Math.addExact(currentRecipeRocketTier, 1L));
        final BigInteger base = BigInteger.valueOf(14400L)
            .shiftLeft(exponent);
        outputFluidToAENetworkBig(type, BigOutputScaling.scale(base, successChance, failed));
    }

    @Inject(method = "outputAfterRecipe_EM", at = @At("RETURN"), require = 1)
    private void apeiron$finishOutput(final CallbackInfo ci) {
        apeiron$itemAmounts.clear();
        apeiron$fluidAmounts.clear();
        flushOutputsBig();
    }

    @Inject(method = "onPreTick", at = @At("RETURN"), require = 1)
    private void apeiron$retryOutputs(final IGregTechTileEntity tile, final long tick, final CallbackInfo ci) {
        if (tile.isServerSide() && tick % 20L == 0 && !apeiron$outputs.isEmpty()) flushOutputsBig();
    }

    @Override
    public void outputItemToAENetworkBig(final ItemStack type, final BigInteger amount) {
        apeiron$outputs.addItem(type, amount);
        apeiron$markDirty();
    }

    @Override
    public void outputFluidToAENetworkBig(final FluidStack type, final BigInteger amount) {
        apeiron$outputs.addFluid(type, amount);
        apeiron$markDirty();
    }

    @Override
    public void flushOutputsBig() {
        if (apeiron$outputs.isEmpty()) return;
        final MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
        if (apeiron$outputs.flush(
            controller.getOutputBusses(),
            controller.getOutputHatches(),
            controller.protectsExcessItem(),
            controller.protectsExcessFluid())) apeiron$markDirty();
    }

    @Unique
    private void apeiron$markDirty() {
        final MTEMultiBlockBase controller = (MTEMultiBlockBase) (Object) this;
        if (controller.getBaseMetaTileEntity() != null) controller.markDirty();
    }

    @Override
    public BigInteger getPendingItemOutputBig() {
        return apeiron$outputs.getItemAmountBig();
    }

    @Override
    public BigInteger getPendingFluidOutputBig() {
        return apeiron$outputs.getFluidAmountBig();
    }

    @Override
    public BigInteger getRecipeItemOutputBig(final int index) {
        final ItemStackLong stack = outputItems.get(index);
        return apeiron$itemAmounts.getOrDefault(stack, BigInteger.valueOf(stack.stackSize));
    }

    @Override
    public BigInteger getRecipeFluidOutputBig(final int index) {
        final FluidStackLong stack = outputFluids.get(index);
        return apeiron$fluidAmounts.getOrDefault(stack, BigInteger.valueOf(stack.amount));
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$saveOutputs(final NBTTagCompound tag, final CallbackInfo ci) {
        apeiron$outputs.save(tag);
        final NBTTagList items = new NBTTagList();
        for (final ItemStackLong stack : outputItems) {
            final NBTTagCompound amount = new NBTTagCompound();
            BigValueCodec.writeNBT(
                amount,
                "Count",
                "ExactCount",
                new AdaptiveInteger(apeiron$itemAmounts.getOrDefault(stack, BigInteger.valueOf(stack.stackSize))));
            items.appendTag(amount);
        }
        tag.setTag("ApeironRecipeItemAmounts", items);
        final NBTTagList fluids = new NBTTagList();
        for (final FluidStackLong stack : outputFluids) {
            final NBTTagCompound amount = new NBTTagCompound();
            BigValueCodec.writeNBT(
                amount,
                "Count",
                "ExactCount",
                new AdaptiveInteger(apeiron$fluidAmounts.getOrDefault(stack, BigInteger.valueOf(stack.amount))));
            fluids.appendTag(amount);
        }
        tag.setTag("ApeironRecipeFluidAmounts", fluids);
    }

    @Inject(method = "setItemNBT", at = @At("RETURN"), require = 1)
    private void apeiron$savePendingInDrop(final NBTTagCompound tag, final CallbackInfo ci) {
        apeiron$outputs.save(tag);
    }

    @Inject(method = "loadNBTData", at = @At("HEAD"), require = 1)
    private void apeiron$clearLoadedOutputs(final NBTTagCompound tag, final CallbackInfo ci) {
        outputItems = new ArrayList<>();
        outputFluids = new ArrayList<>();
        apeiron$itemAmounts.clear();
        apeiron$fluidAmounts.clear();
        apeiron$currentItemAmount = null;
        apeiron$currentFluidAmount = null;
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$loadOutputs(final NBTTagCompound tag, final CallbackInfo ci) {
        apeiron$outputs.load(tag);
        final NBTTagList items = tag.getTagList("ApeironRecipeItemAmounts", 10);
        for (int i = 0; i < Math.min(items.tagCount(), outputItems.size()); i++) {
            apeiron$itemAmounts.put(
                outputItems.get(i),
                BigValueCodec.readNBT(items.getCompoundTagAt(i), "Count", "ExactCount")
                    .toBigInteger());
        }
        final NBTTagList fluids = tag.getTagList("ApeironRecipeFluidAmounts", 10);
        for (int i = 0; i < Math.min(fluids.tagCount(), outputFluids.size()); i++) {
            apeiron$fluidAmounts.put(
                outputFluids.get(i),
                BigValueCodec.readNBT(fluids.getCompoundTagAt(i), "Count", "ExactCount")
                    .toBigInteger());
        }
    }

    @Inject(method = "getInfoData", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$pendingInfo(final CallbackInfoReturnable<String[]> cir) {
        final String[] nativeInfo = cir.getReturnValue();
        final String[] info = Arrays.copyOf(nativeInfo, nativeInfo.length + 2);
        info[nativeInfo.length] = IGregTechDeviceInformation
            .encode("apeiron.machine.eye.pending_items", BigNumberFormatter.formatExact(getPendingItemOutputBig()));
        info[nativeInfo.length + 1] = IGregTechDeviceInformation
            .encode("apeiron.machine.eye.pending_fluids", BigNumberFormatter.formatExact(getPendingFluidOutputBig()));
        cir.setReturnValue(info);
    }
}
