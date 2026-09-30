/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.silvia.apeiron.ae.*;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.CraftUpdateListener;
import appeng.crafting.CraftingLink;
import appeng.crafting.MECraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPUState;
import com.silvia.apeiron.ae.crafting.core.BigFinalOutput;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

/** Exact production reception and step accounting, including small transfers against a large job. */
@SuppressWarnings({ "rawtypes", "unchecked" })
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCPUTransferMixin implements BigCraftingCPUState {
    @Shadow protected IItemList<IAEStack<?>> waitingFor;
    @Shadow protected IItemList<IAEStack<?>> waitingForMissing;
    @Shadow protected CraftingCPUCluster.finalOutput finalOutput;
    @Shadow protected ICraftingLink myLastLink;
    @Shadow protected boolean waiting;
    @Shadow protected MECraftingInventory inventory;
    @Shadow @Final protected List<CraftUpdateListener> craftUpdateListeners;
    @Shadow protected abstract void postChange(IAEStack<?> diff, BaseActionSource source);
    @Shadow protected abstract void postCraftingStatusChange(IAEStack<?> diff);
    @Shadow protected abstract void updateElapsedTime(IAEStack<?> stack);
    @Shadow protected abstract void recordReturnedOutputs(IAEStack<?> stack);
    @Shadow protected abstract void completeJob();
    @Shadow protected abstract void updateCPU();
    @Shadow public abstract void markDirty();
    @Shadow protected long lastTime;
    @Shadow protected long elapsedTime;
    @Shadow protected long startItemCount;
    @Shadow protected long remainingItemCount;
    @Unique private BigInteger apeiron$start;
    @Unique private BigInteger apeiron$remaining;

    /** @author Apeiron @reason Receive exact quantities using the legacy entry point. */
    @Overwrite
    public IAEStack<?> injectItems(final IAEStack<?> input, final Actionable mode, final BaseActionSource source) {
        return this.injectItemsBig(input, mode, source);
    }

    @Override
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final Actionable type, final BaseActionSource src) {
        if (input == null) return null;
        final IAEStack what = input.copy();
        final IAEStack<?> is = this.waitingFor.findPrecise(what);
        final IAEStack<?> ism = this.waitingForMissing.findPrecise(what);

        if (type == Actionable.SIMULATE) // causes crafting to lock up?
        {
            if (is != null && is.getStackSize() > 0) {
                if (BigAEStackValues.compare(is, what) >= 0) {
                    if (this.finalOutput.isFinalOutput(what)) {
                        final IAEStack<?> outputToSend = ((BigFinalOutput) this.finalOutput).splitOutputToIngredientBig(what, type);
                        if (outputToSend == null) {
                            return null;
                        }

                        if (this.myLastLink != null) {
                            return ((CraftingLink) this.myLastLink).injectItems(outputToSend.copy(), type);
                        }

                        return outputToSend; // ignore it.
                    }

                    return null;
                }

                final IAEStack leftOver = what.copy();
                BigAEStackValues.set(leftOver, BigAEStackValues.get(leftOver).subtract(BigAEStackValues.get(is)));

                final IAEStack<?> used = what.copy();
                BigAEStackValues.set(used, BigAEStackValues.get(is));

                if (this.finalOutput.isFinalOutput(used)) {
                    final IAEStack<?> outputToSend = ((BigFinalOutput) this.finalOutput).splitOutputToIngredientBig(used, type);

                    if (outputToSend == null) {
                        return leftOver;
                    }

                    if (this.myLastLink != null) {
                        final IAEStack<?> linkLeftOver = ((CraftingLink) this.myLastLink)
                                .injectItems(outputToSend.copy(), type);
                        if (linkLeftOver != null) {
                            leftOver.add(linkLeftOver);
                        }
                        return leftOver;
                    }

                    leftOver.add(outputToSend);
                    return leftOver; // ignore it.
                }

                return leftOver;
            }
        } else if (type == Actionable.MODULATE) {
            if (is != null && is.getStackSize() > 0) {
                this.waiting = false;
                this.postChange(is, src);

                if (BigAEStackValues.compare(is, what) >= 0) {
                    BigAEStackValues.set(is, BigAEStackValues.get(is).subtract(BigAEStackValues.get(what)));
                    if (ism != null) BigAEStackValues.set(ism, BigAEStackValues.get(ism).subtract(BigAEStackValues.get(what)));

                    this.updateElapsedTime(what);
                    this.recordReturnedOutputs(what);
                    this.markDirty();
                    this.postCraftingStatusChange(is);
                    for (CraftUpdateListener craftUpdateListener : craftUpdateListeners) {
                        // whatever it passes is not important, if it's not 0, it indicates the craft is active rather
                        // than stuck.
                        craftUpdateListener.accept(1);
                    }

                    if (this.finalOutput.isFinalOutput(what)) {
                        final IAEStack<?> outputToSend = ((BigFinalOutput) this.finalOutput).splitOutputToIngredientBig(what, type);
                        IAEStack<?> leftover = outputToSend;
                        IAEStack<?> finalOutput = this.finalOutput.findPrecise(what);

                        if (outputToSend != null) {
                            BigAEStackValues.set(finalOutput, BigAEStackValues.get(finalOutput).subtract(BigAEStackValues.get(outputToSend)));
                        }

                        if (outputToSend != null && this.myLastLink != null) {
                            leftover = ((CraftingLink) this.myLastLink).injectItems(outputToSend, type);
                        }

                        if (this.finalOutput.isEmpty()) {
                            this.completeJob();
                        }

                        this.updateCPU();

                        return leftover; // ignore it.
                    }

                    // 2000
                    this.inventory.injectItems(what, type);
                    return null;
                }

                final IAEStack insert = what.copy();
                BigAEStackValues.set(insert, BigAEStackValues.get(is));
                BigAEStackValues.set(what, BigAEStackValues.get(what).subtract(BigAEStackValues.get(is)));

                is.setStackSize(0);
                if (ism != null) ism.setStackSize(0);

                this.updateElapsedTime(insert);
                this.recordReturnedOutputs(insert);
                this.postCraftingStatusChange(is);

                if (this.finalOutput.isFinalOutput(insert)) {
                    final IAEStack<?> outputToSend = ((BigFinalOutput) this.finalOutput).splitOutputToIngredientBig(insert, type);
                    IAEStack<?> leftover = what;
                    IAEStack<?> finalOutput = this.finalOutput.findPrecise(insert);

                    if (outputToSend != null) {
                        BigAEStackValues.set(finalOutput, BigAEStackValues.get(finalOutput).subtract(BigAEStackValues.get(outputToSend)));
                    }

                    if (outputToSend != null) {
                        if (this.myLastLink != null) {
                            final IAEStack<?> linkLeftOver = ((CraftingLink) this.myLastLink)
                                    .injectItems(outputToSend.copy(), type);
                            if (linkLeftOver != null) {
                                what.add(linkLeftOver);
                            }
                        } else {
                            what.add(outputToSend);
                        }
                    }

                    if (this.finalOutput.isEmpty()) {
                        this.completeJob();
                    }

                    this.updateCPU();
                    this.markDirty();

                    return leftover; // ignore it.
                }

                this.inventory.injectItems(insert, type);
                this.markDirty();

                return what;
            }
        }

        return input;
    }
    @Override
    public BigInteger getStartItemCountBig() {
        return this.apeiron$start == null ? BigInteger.valueOf(this.startItemCount) : this.apeiron$start;
    }

    @Override
    public BigInteger getRemainingItemCountBig() {
        return this.apeiron$remaining == null ? BigInteger.valueOf(this.remainingItemCount) : this.apeiron$remaining;
    }

    @Inject(method = "prepareStepCount", at = @At("HEAD"), cancellable = true)
    private void apeiron$prepareExactSteps(final CallbackInfo ci) {
        final IItemList<IAEStack<?>> list = appeng.api.AEApi.instance().storage().createAEStackList();
        final CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        self.getModernListOfItem(list, CraftingItemList.ACTIVE);
        self.getModernListOfItem(list, CraftingItemList.PENDING);
        BigInteger total = BigInteger.ZERO;
        for (IAEStack<?> stack : list) total = total.add(BigAEStackValues.get(stack));
        final BigInteger oldStart = this.getStartItemCountBig();
        final BigInteger completed = oldStart.signum() > 0 ? oldStart.subtract(this.getRemainingItemCountBig())
            : BigInteger.ZERO;
        final BigInteger start = total.add(completed);
        this.startItemCount = BigAEStackValues.saturatedLong(start);
        this.remainingItemCount = BigAEStackValues.saturatedLong(total);
        this.apeiron$start = BigAEStackValues.fitsLong(start) ? null : start;
        this.apeiron$remaining = BigAEStackValues.fitsLong(total) ? null : total;
        ci.cancel();
    }

    @Inject(method = "updateElapsedTime", at = @At("HEAD"), cancellable = true)
    private void apeiron$updateExactSteps(final IAEStack<?> stack, final CallbackInfo ci) {
        final long now = System.nanoTime();
        this.elapsedTime += now - this.lastTime;
        this.lastTime = now;
        final BigInteger next = this.getRemainingItemCountBig().subtract(BigAEStackValues.get(stack));
        this.remainingItemCount = BigAEStackValues.saturatedLong(next);
        this.apeiron$remaining = BigAEStackValues.fitsLong(next) ? null : next;
        ci.cancel();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$writeSteps(final NBTTagCompound tag, final CallbackInfo ci) {
        BigValueCodec.writeNBT(tag, "startItemCount", "ApeironStartItemCount", new AdaptiveInteger(this.getStartItemCountBig()));
        BigValueCodec.writeNBT(tag, "remainingItemCount", "ApeironRemainingItemCount",
            new AdaptiveInteger(this.getRemainingItemCountBig()));
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void apeiron$readSteps(final NBTTagCompound tag, final CallbackInfo ci) {
        final AdaptiveInteger start = BigValueCodec.readNBT(tag, "startItemCount", "ApeironStartItemCount");
        final AdaptiveInteger remaining = BigValueCodec.readNBT(tag, "remainingItemCount", "ApeironRemainingItemCount");
        this.apeiron$start = start.isBig() ? start.toBigInteger() : null;
        this.apeiron$remaining = remaining.isBig() ? remaining.toBigInteger() : null;
    }
}
