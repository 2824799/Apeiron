package com.silvia.apeiron.mixin.tst.crafting;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.Nxer.TwistSpaceTechnology.config.Config;
import com.silvia.apeiron.ae.crafting.core.PatternBatchMedium;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;

/** Aggregate accepted crafts by output type, retaining TST's processing cycle and completion path. */
@Pseudo
@Mixin(targets = "com.Nxer.TwistSpaceTechnology.common.machine.TST_MegaCraftingCenter", remap = false)
public abstract class MegaCraftingCenterBatchMixin implements PatternBatchMedium {

    @Shadow
    protected boolean toReturnPatterns;
    @Unique
    private BigMachineOutputQueue apeiron$accepted = new BigMachineOutputQueue();
    @Unique
    private boolean apeiron$returningPatterns;

    @Override
    public boolean pushPatternBig(ICraftingPatternDetails pattern, List<IAEStack<?>> inputs, BigInteger batches,
        boolean simulate) {
        if (batches.signum() <= 0 || pattern.isCraftable() || pattern.isInputOnly()) return false;
        for (IAEStack<?> output : pattern.getCondensedAEOutputs())
            if (!(output instanceof IAEItemStack) || BigAEStackValues.get(output)
                .signum() <= 0) return false;
        if (simulate) return true;
        // Build separately so an invalid container callback cannot leave a partially accepted craft.
        BigMachineOutputQueue accepted = new BigMachineOutputQueue();
        for (IAEStack<?> output : pattern.getCondensedAEOutputs()) accepted.addItem(
            ((IAEItemStack) output).getItemStack(),
            BigAEStackValues.get(output)
                .multiply(batches));
        // Match the machine's native container-return rules for each declared input entry.
        for (IAEItemStack input : pattern.getInputs()) {
            if (input == null) continue;
            ItemStack stack = input.getItemStack();
            if (stack.getItem() == null || !stack.getItem()
                .hasContainerItem(stack)) continue;
            ItemStack container = stack.getItem()
                .getContainerItem(stack);
            if (container != null && container.stackSize > 0) accepted.addItem(
                container,
                BigInteger.valueOf(container.stackSize)
                    .multiply(batches));
        }
        accepted.moveTo(apeiron$accepted);
        ((MTEMultiBlockBase) (Object) this).markDirty();
        return true;
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$acceptSingle(ICraftingPatternDetails pattern, InventoryCrafting table,
        CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(pushPatternBig(pattern, java.util.Collections.emptyList(), BigInteger.ONE, false));
    }

    @Inject(method = "checkProcessing", at = @At("HEAD"), require = 1)
    private void apeiron$rememberPatternReturn(CallbackInfoReturnable<CheckRecipeResult> cir) {
        apeiron$returningPatterns = toReturnPatterns;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$prepareOutputs(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (apeiron$returningPatterns || apeiron$accepted.isEmpty()) return;
        if (!cir.getReturnValue()
            .wasSuccessful() && cir.getReturnValue() != CheckRecipeResultRegistry.NO_RECIPE) return;
        BigTstOutputController output = (BigTstOutputController) this;
        for (IAEStack<?> stack : apeiron$accepted.snapshotOutputsUnsorted())
            output.mergeItemIntoMEOutputQueueBig(((IAEItemStack) stack).getItemStack(), BigAEStackValues.get(stack));
        apeiron$accepted = new BigMachineOutputQueue();
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        machine.mMaxProgresstime = Config.TickEveryProcess_MegaCraftingCenter;
        machine.mProgresstime = 0;
        machine.markDirty();
        cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$save(NBTTagCompound tag, CallbackInfo ci) {
        NBTTagCompound accepted = new NBTTagCompound();
        apeiron$accepted.save(accepted);
        tag.setTag("ApeironAcceptedCraftingOutputs", accepted);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$load(NBTTagCompound tag, CallbackInfo ci) {
        apeiron$accepted.load(tag.getCompoundTag("ApeironAcceptedCraftingOutputs"));
    }
}
