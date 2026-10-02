package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.automation.BigPoweredTransfers;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.GridAccessException;
import appeng.parts.reporting.PartConversionMonitor;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;

/** Routes conversion-monitor container transfers through exact AE quantities. */
@Mixin(value = PartConversionMonitor.class, remap = false)
public abstract class PartConversionMonitorBigMixin {

    @Inject(method = "injectExtraToMonitor", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$injectExtra(final EntityPlayer player, final CallbackInfo ci) {
        final ItemStack hand = player.getCurrentEquippedItem();
        if (hand == null) {
            ci.cancel();
            return;
        }

        final PartConversionMonitor monitorPart = (PartConversionMonitor) (Object) this;
        for (final IAEStackType type : AEStackTypeRegistry.getAllTypes()) {
            if (!type.isContainerItemForType(hand)) continue;
            final int handSize = hand.stackSize;
            final ItemStack one = hand.copy();
            one.stackSize = 1;
            final IAEStack<?> stack = type.getStackFromContainerItem(one);
            if (stack == null || BigAEStackValues.get(stack)
                .signum() <= 0
                || monitorPart.getDisplayed() != null && !monitorPart.getDisplayed()
                    .isSameType(stack)) {
                ci.cancel();
                return;
            }

            try {
                final IEnergySource energy = monitorPart.getProxy()
                    .getEnergy();
                final IMEMonitor monitor = monitorPart.getProxy()
                    .getStorage()
                    .getMEMonitor(type);
                if (monitor == null) break;
                final PlayerSource source = new PlayerSource(player, monitorPart);
                final IAEStack<?> simulatedLeftover = BigPoweredTransfers
                    .poweredInsertBig(energy, monitor, stack, source, Actionable.SIMULATE);
                final BigInteger accepted = BigAEStackValues.get(stack)
                    .subtract(simulatedLeftover == null ? BigInteger.ZERO : BigAEStackValues.get(simulatedLeftover));
                if (accepted.signum() <= 0) break;

                final ObjectLongPair<ItemStack> drained = type
                    .drainStackFromContainer(one, BigAEStackValues.set(stack.copy(), accepted));
                if (drained.left() == null || drained.rightLong() <= 0) break;
                ItemStack result = drained.left();

                final IAEStack<?> actualLeftover = BigPoweredTransfers.poweredInsertBig(
                    energy,
                    monitor,
                    BigAEStackValues.set(stack.copy(), BigInteger.valueOf(drained.rightLong())),
                    source,
                    Actionable.MODULATE);
                if (actualLeftover != null) {
                    final ObjectLongPair<ItemStack> filled = type.fillContainer(
                        result,
                        BigAEStackValues.set(stack.copy(), BigAEStackValues.get(actualLeftover)));
                    if (filled.left() != null) result = filled.left();
                    final BigInteger unfilled = BigAEStackValues.get(actualLeftover)
                        .subtract(BigInteger.valueOf(filled.rightLong()));
                    if (unfilled.signum() > 0) {
                        Platform.handleLeftover(player, BigAEStackValues.copyWithSize(stack, unfilled));
                    }
                }

                if (handSize == 1) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, result);
                } else {
                    player.getCurrentEquippedItem().stackSize--;
                    Platform.addToPlayerInvOrDrop(player, result);
                }
            } catch (final GridAccessException e) {
                break;
            }
            break;
        }
        ci.cancel();
    }

    @Inject(method = "extractExtraFromMonitor", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$extractExtra(final EntityPlayer player, final CallbackInfo ci) {
        final PartConversionMonitor monitorPart = (PartConversionMonitor) (Object) this;
        final IAEStack<?> displayed = monitorPart.getDisplayed();
        final ItemStack hand = player.getCurrentEquippedItem();
        if (displayed == null || hand == null) {
            ci.cancel();
            return;
        }

        for (final IAEStackType type : AEStackTypeRegistry.getAllTypes()) {
            if (!type.isContainerItemForType(hand)) continue;
            try {
                final IEnergySource energy = monitorPart.getProxy()
                    .getEnergy();
                final IMEMonitor monitor = monitorPart.getProxy()
                    .getStorage()
                    .getMEMonitor(type);
                if (monitor == null) break;
                final PlayerSource source = new PlayerSource(player, monitorPart);
                final IAEStack<?> stored = BigPoweredTransfers
                    .poweredExtractionBig(energy, monitor, displayed, source, Actionable.SIMULATE);
                if (stored == null || BigAEStackValues.get(stored)
                    .signum() <= 0) break;

                final ItemStack one = Platform.copyStackWithSizeOne(hand);
                final BigInteger fillProbeAmount = BigAEStackValues.get(stored)
                    .min(BigInteger.valueOf(Long.MAX_VALUE));
                final ObjectLongPair<ItemStack> probe = type
                    .fillContainer(one, BigAEStackValues.set(stored.copy(), fillProbeAmount));
                if (probe.rightLong() <= 0) break;

                final IAEStack<?> extracted = BigPoweredTransfers.poweredExtractionBig(
                    energy,
                    monitor,
                    BigAEStackValues.set(stored.copy(), BigInteger.valueOf(probe.rightLong())),
                    source,
                    Actionable.MODULATE);
                if (extracted == null) break;
                final ObjectLongPair<ItemStack> filled = type.fillContainer(one, extracted);
                if (filled.left() == null || filled.rightLong() <= 0) break;

                ItemStack result = filled.left();
                final BigInteger leftoverAmount = BigAEStackValues.get(extracted)
                    .subtract(BigInteger.valueOf(filled.rightLong()));
                if (leftoverAmount.signum() > 0) {
                    final IAEStack<?> rejected = BigPoweredTransfers.poweredInsertBig(
                        energy,
                        monitor,
                        BigAEStackValues.copyWithSize(extracted, leftoverAmount),
                        source,
                        Actionable.MODULATE);
                    if (rejected != null) Platform.handleLeftover(player, rejected);
                }

                if (hand.stackSize == 1) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, result);
                } else {
                    hand.stackSize--;
                    Platform.addToPlayerInvOrDrop(player, result);
                }
            } catch (final GridAccessException e) {
                break;
            }
            break;
        }
        ci.cancel();
    }
}
