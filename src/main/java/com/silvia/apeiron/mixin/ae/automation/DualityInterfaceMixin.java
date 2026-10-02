/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.crafting.core.BigCraftingTracker;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.InsertionMode;
import appeng.api.config.Upgrades;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.MultiCraftingTracker;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

/** Keeps interface inventory work and output forwarding exact at the physical inventory boundary. */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class DualityInterfaceMixin implements com.silvia.apeiron.ae.storage.BigStorageInterceptor {

    @Shadow
    @Final
    private IInterfaceHost iHost;

    @Shadow
    @Final
    protected AENetworkProxy gridProxy;

    @Shadow
    @Final
    private BaseActionSource interfaceRequestSource;

    @Shadow
    @Final
    private MultiCraftingTracker craftingTracker;

    @Shadow
    private List<IAEStack<?>> waitingToSend;

    @Shadow
    private boolean duringPushOut;

    @Shadow
    private IMEInventory<IAEItemStack> destination;

    @Shadow
    private boolean isWorking;

    @Shadow
    protected boolean hasItemsToSend() {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    private InventoryAdaptor getAdaptor(int slot) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    private void updatePlan(int slot) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    public abstract int getInstalledUpgrades(Upgrades upgrade);

    @Shadow
    public abstract IAEItemStack fuzzyPoweredExtraction(IEnergySource energy, IMEInventory<IAEItemStack> cell,
        IAEItemStack config, BaseActionSource source, int iteration);

    @Shadow
    private InsertionMode getInsertionMode() {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    private void updateStuckState(boolean stuck) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    @Final
    private IAEItemStack[] requireWork;

    @Shadow
    @Final
    private appeng.tile.inventory.AppEngInternalAEInventory config;

    @Shadow
    private appeng.tile.inventory.AppEngInternalInventory storage;

    @Inject(method = "updatePlan", at = @At("HEAD"), cancellable = true)
    private void apeiron$updatePlan(final int slot,
        final org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        final IAEItemStack configured = this.config.getAEStackInSlot(slot);
        if (!BigAEStackValues.isBig(configured)) return;
        final BigInteger desired = BigAEStackValues.get(configured);
        if (desired.signum() <= 0) {
            this.config.setInventorySlotContents(slot, null);
            this.requireWork[slot] = null;
            ci.cancel();
            return;
        }
        final ItemStack stored = this.storage.getStackInSlot(slot);
        final int fuzzy = this.getInstalledUpgrades(Upgrades.FUZZY);
        final boolean fuzzySlot = fuzzy == 1 && slot > 5 || fuzzy == 2 && slot > 2 || fuzzy == 3;
        if (stored == null) {
            this.requireWork[slot] = configured.copy();
        } else if (fuzzySlot || configured.isSameType(stored)) {
            final IAEItemStack work = fuzzySlot ? AEApi.instance()
                .storage()
                .createItemStack(stored) : configured.copy();
            final BigInteger delta = desired.subtract(BigInteger.valueOf(stored.stackSize));
            this.requireWork[slot] = delta.signum() == 0 ? null : BigAEItemStacks.copyWithSize(work, delta);
        } else {
            final IAEItemStack work = AEApi.instance()
                .storage()
                .createItemStack(stored);
            this.requireWork[slot] = BigAEItemStacks.copyWithSize(
                work,
                BigAEStackValues.get(work)
                    .negate());
        }
        ci.cancel();
    }

    @Unique
    private boolean apeiron$hasBigWaiting() {
        if (this.waitingToSend == null) return false;
        for (IAEStack<?> stack : this.waitingToSend) {
            if (BigAEStackValues.isBig(stack)) return true;
        }
        return false;
    }

    @Inject(method = "pushItemsOut", at = @At("HEAD"), cancellable = true)
    private void apeiron$pushItemsOutBig(final EnumSet<ForgeDirection> possibleDirections,
        final CallbackInfoReturnable<Boolean> cir) {
        if (!this.apeiron$hasBigWaiting()) return;

        if (!this.hasItemsToSend()) {
            cir.setReturnValue(false);
            return;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World world = tile.getWorldObj();
        boolean sentSomething = false;

        for (ForgeDirection side : possibleDirections) {
            final TileEntity target = world
                .getTileEntity(tile.xCoord + side.offsetX, tile.yCoord + side.offsetY, tile.zCoord + side.offsetZ);
            if (target == null || target.getClass()
                .getName()
                .equals("li.cil.oc.common.tileentity.Adapter")) continue;

            if (target instanceof IInterfaceHost host) {
                try {
                    final DualityInterface other = host.getInterfaceDuality();
                    if (!other.getProxy()
                        .isActive()
                        || other.getProxy()
                            .getGrid() == this.gridProxy.getGrid())
                        continue;
                } catch (GridAccessException ignored) {
                    continue;
                }
            }

            final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, side.getOpposite());
            if (adaptor == null) continue;

            this.duringPushOut = true;
            final Iterator<IAEStack<?>> iterator = this.waitingToSend.iterator();
            while (iterator.hasNext()) {
                final IAEStack<?> stack = iterator.next();
                if (stack == null) {
                    iterator.remove();
                    continue;
                }

                final BigInteger before = BigAEStackValues.get(stack);
                final IAEStack<?> leftover;
                leftover = BigInventoryAdaptors.addStackBig(adaptor, stack, this.getInsertionMode(), false);
                final BigInteger after = leftover == null ? BigInteger.ZERO : BigAEStackValues.get(leftover);
                if (after.compareTo(before) >= 0) continue;

                sentSomething = true;
                if (after.signum() > 0) {
                    BigAEStackValues.set(stack, after);
                } else {
                    BigAEStackValues.set(stack, BigInteger.ZERO);
                    iterator.remove();
                }
            }
            this.duringPushOut = false;
        }

        if (this.waitingToSend.isEmpty()) {
            this.waitingToSend = null;
            this.updateStuckState(false);
        } else {
            this.updateStuckState(true);
        }
        cir.setReturnValue(sentSomething);
    }

    @Unique
    private boolean apeiron$usePlanBig(final int slot, final IAEItemStack plan) {
        final InventoryAdaptor adaptor = this.getAdaptor(slot);
        if (adaptor == null) return false;

        this.isWorking = true;
        boolean changed = false;
        try {
            this.destination = this.gridProxy.getStorage()
                .getItemInventory();
            final IEnergySource energy = this.gridProxy.getEnergy();
            final BigInteger planAmount = BigAEItemStacks.stackSize(plan);

            if (planAmount.signum() < 0) {
                final BigInteger requested = planAmount.negate();
                final IAEItemStack toStore = BigAEItemStacks.copyWithSize(plan, requested);
                final IAEItemStack available = BigInventoryAdaptors.extractStackBig(adaptor, toStore, true);
                if (available == null || !BigAEItemStacks.stackSize(available)
                    .equals(requested)) {
                    changed = true;
                } else {
                    final IAEItemStack leftover = Platform
                        .poweredInsert(energy, this.destination, toStore, this.interfaceRequestSource);
                    final BigInteger notStored = leftover == null ? BigInteger.ZERO
                        : BigAEItemStacks.stackSize(leftover);
                    final BigInteger moved = requested.subtract(notStored)
                        .max(BigInteger.ZERO);
                    if (moved.signum() > 0) {
                        final IAEItemStack removed = BigInventoryAdaptors
                            .extractStackBig(adaptor, BigAEItemStacks.copyWithSize(toStore, moved), false);
                        if (removed == null || BigAEItemStacks.stackSize(removed)
                            .compareTo(moved) < 0) {
                            changed = true;
                        } else {
                            changed = true;
                        }
                    }
                }
            } else if (planAmount.signum() > 0) {
                final IAEItemStack requestedPlan = BigAEItemStacks.copyWithSize(plan, planAmount);
                final IAEItemStack simulatedLeftover = (IAEItemStack) BigInventoryAdaptors
                    .addStackBig(adaptor, requestedPlan, InsertionMode.DEFAULT, true);
                final BigInteger availableRoom = simulatedLeftover == null ? planAmount
                    : planAmount.subtract(BigAEItemStacks.stackSize(simulatedLeftover));
                if (availableRoom.signum() <= 0) {
                    changed = true;
                } else {
                    IAEItemStack acquired;
                    if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0
                        && this.getInstalledUpgrades(Upgrades.CRAFTING) == 0) {
                        acquired = this.fuzzyPoweredExtraction(
                            energy,
                            this.destination,
                            BigAEItemStacks.copyWithSize(plan, availableRoom),
                            this.interfaceRequestSource,
                            appeng.util.IterationCounter.fetchNewId());
                    } else {
                        acquired = Platform.poweredExtraction(
                            energy,
                            this.destination,
                            BigAEItemStacks.copyWithSize(plan, availableRoom),
                            this.interfaceRequestSource);
                    }

                    if (acquired != null) {
                        final IAEItemStack failed = (IAEItemStack) BigInventoryAdaptors
                            .addStackBig(adaptor, acquired, InsertionMode.DEFAULT, false);
                        if (failed != null) {
                            final IAEItemStack rejected = com.silvia.apeiron.ae.storage.BigMEInventories.injectItemsBig(
                                this.destination,
                                failed,
                                Actionable.MODULATE,
                                this.interfaceRequestSource);
                            if (rejected != null) this.addToSendList(rejected);
                        }
                        changed = true;
                    } else if (this.getInstalledUpgrades(Upgrades.CRAFTING) > 0
                        && this.craftingTracker instanceof BigCraftingTracker tracker) {
                            final ICraftingGrid craftingGrid = this.gridProxy.getCrafting();
                            changed = tracker.handleCraftingBig(
                                slot,
                                availableRoom,
                                plan,
                                adaptor,
                                this.iHost.getTileEntity()
                                    .getWorldObj(),
                                this.gridProxy.getGrid(),
                                craftingGrid,
                                this.interfaceRequestSource);
                        }
                }
            }
        } catch (GridAccessException ignored) {
            // AE will retry the interface plan on the next tick.
        } finally {
            this.isWorking = false;
        }

        if (changed) this.updatePlan(slot);
        return changed;
    }

    @Inject(method = "usePlan", at = @At("HEAD"), cancellable = true)
    private void apeiron$usePlan(final int slot, final IAEItemStack plan, final CallbackInfoReturnable<Boolean> cir) {
        if (BigAEItemStacks.isStackSizeBig(plan)) {
            cir.setReturnValue(this.apeiron$usePlanBig(slot, plan));
        }
    }

    @Shadow
    private List<IAEStack<?>> unlockStacks;

    @Shadow
    public abstract void saveChanges();

    @Override
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final Actionable mode, final BaseActionSource source) {
        if (mode == Actionable.SIMULATE || input == null || this.unlockStacks == null) return input;
        boolean changed = false;
        for (Iterator<IAEStack<?>> iterator = this.unlockStacks.iterator(); iterator.hasNext();) {
            final IAEStack<?> unlock = iterator.next();
            if (!unlock.equals(input)) continue;
            BigAEStackValues.set(
                unlock,
                BigAEStackValues.get(unlock)
                    .subtract(BigAEStackValues.get(input)));
            if (BigAEStackValues.get(unlock)
                .signum() <= 0) iterator.remove();
            changed = true;
            break;
        }
        if (changed) this.saveChanges();
        return input;
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$injectUnlockBig(final IAEStack<?> input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        cir.setReturnValue(this.injectItemsBig(input, mode, source));
    }

    @Shadow
    private void addToSendList(IAEStack<?> stack) {
        throw new AssertionError();
    }

    @Unique
    private BigInteger apeiron$patternInputAmount;

    @Unique
    private IAEStack<?> apeiron$patternRemainder;

    @Redirect(
        method = "pushPattern",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J", ordinal = 0))
    private long apeiron$capturePatternInput(final IAEStack<?> stack) {
        this.apeiron$patternInputAmount = BigAEStackValues.get(stack);
        this.apeiron$patternRemainder = null;
        return BigAEStackValues.saturatedLong(this.apeiron$patternInputAmount);
    }

    @Redirect(
        method = "pushPattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/InventoryAdaptor;addStack(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/InsertionMode;)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$pushPatternInput(final InventoryAdaptor adaptor, final IAEStack<?> stack,
        final InsertionMode mode) {
        this.apeiron$patternRemainder = BigInventoryAdaptors.addStackBig(adaptor, stack, mode, false);
        return this.apeiron$patternRemainder;
    }

    @Redirect(
        method = "pushPattern",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J", ordinal = 1))
    private long apeiron$comparePatternRemainder(final IAEStack<?> remainder) {
        final BigInteger exactRemainder = BigAEStackValues.get(remainder);
        final long legacyRemainder = BigAEStackValues.saturatedLong(exactRemainder);
        final long legacyInput = BigAEStackValues.saturatedLong(this.apeiron$patternInputAmount);
        // This read is used only by AE's equality check. Distinguish exact counts whose long projections coincide.
        if (legacyRemainder == legacyInput && !exactRemainder.equals(this.apeiron$patternInputAmount)) {
            return legacyRemainder == Long.MIN_VALUE ? Long.MAX_VALUE : legacyRemainder - 1;
        }
        return legacyRemainder;
    }

    @Redirect(
        method = "pushPattern",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            ordinal = 0))
    private IAEStack<?> apeiron$keepPatternRemainder(final IAEStack<?> stack, final long legacy) {
        return BigAEStackValues.set(stack, BigAEStackValues.get(this.apeiron$patternRemainder));
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void apeiron$clearPatternCapture(final CallbackInfoReturnable<Boolean> cir) {
        this.apeiron$patternInputAmount = null;
        this.apeiron$patternRemainder = null;
    }
}
