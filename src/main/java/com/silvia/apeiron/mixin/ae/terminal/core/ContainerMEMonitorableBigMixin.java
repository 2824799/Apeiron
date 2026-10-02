package com.silvia.apeiron.mixin.ae.terminal.core;

import java.math.BigInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.automation.BigPoweredTransfers;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.terminal.BigContainerAccess;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.AppEngSlot;
import appeng.helpers.MonitorableAction;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;

/** Handles terminal container actions whose per-container amount multiplied by the held stack exceeds long. */
@Mixin(value = ContainerMEMonitorable.class, remap = false)
public abstract class ContainerMEMonitorableBigMixin {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    @Shadow
    abstract <T extends IAEStack<T>> IMEMonitor<T> getMonitorWithFilter(IAEStackType<T> type);

    private ContainerMEMonitorable apeiron$self() {
        return (ContainerMEMonitorable) (Object) this;
    }

    private IEnergySource apeiron$powerSource() {
        return this.apeiron$self()
            .getPowerSource();
    }

    private BaseActionSource apeiron$actionSource() {
        return this.apeiron$self()
            .getActionSource();
    }

    private IAEStack<?> apeiron$targetStack() {
        return this.apeiron$self()
            .getTargetStack();
    }

    private java.util.List<Slot> apeiron$slots() {
        return ((net.minecraft.inventory.Container) (Object) this).inventorySlots;
    }

    private void apeiron$updateHeld(final EntityPlayerMP player) {
        ((BigContainerAccess) (Object) this).apeiron$updateHeld(player);
    }

    @Inject(method = "doMonitorableAction", at = @At("HEAD"), cancellable = true)
    private void apeiron$largeContainerAction(final MonitorableAction action, final int custom,
        final EntityPlayerMP player, final CallbackInfo ci) {
        if (this.apeiron$powerSource() == null) return;

        if (action == MonitorableAction.DRAIN_CONTAINERS && this.apeiron$needsLargeDrain(player)) {
            this.apeiron$drainContainers(player);
            ci.cancel();
        } else if (action == MonitorableAction.FILL_CONTAINERS && this.apeiron$needsLargeFill(player)) {
            this.apeiron$fillContainers(player);
            ci.cancel();
        } else
            if (action == MonitorableAction.CONTAINER_QUICK_TRANSFER && this.apeiron$needsLargeQuickTransfer(custom)) {
                this.apeiron$quickTransfer(custom, player);
                ci.cancel();
            }
    }

    private boolean apeiron$needsLargeDrain(final EntityPlayerMP player) {
        final ItemStack hand = player.inventory.getItemStack();
        if (hand == null || hand.stackSize <= 1) return false;
        final IAEStackType<?> type = this.apeiron$typeFor(hand);
        if (type == null) return false;
        final IAEStack<?> stack = type.getStackFromContainerItem(hand);
        return stack != null && BigAEStackValues.get(stack)
            .multiply(BigInteger.valueOf(hand.stackSize))
            .compareTo(LONG_MAX) > 0;
    }

    private boolean apeiron$needsLargeFill(final EntityPlayerMP player) {
        final ItemStack hand = player.inventory.getItemStack();
        final IAEStack<?> target = this.apeiron$targetStack();
        if (hand == null || hand.stackSize <= 1 || target == null) return false;
        final IAEStackType<?> type = target.getStackType();
        if (!type.isContainerItemForType(hand)) return false;
        final ObjectLongPair<ItemStack> filled = this.apeiron$probeFill(type, hand, target);
        return filled != null && filled.rightLong() > 0
            && BigInteger.valueOf(filled.rightLong())
                .multiply(BigInteger.valueOf(hand.stackSize))
                .compareTo(LONG_MAX) > 0;
    }

    private boolean apeiron$needsLargeQuickTransfer(final int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.apeiron$slots()
            .size()) return false;
        final Slot slot = this.apeiron$slots()
            .get(slotIndex);
        if (!(slot instanceof AppEngSlot) || !((AppEngSlot) slot).isPlayerSide()) return false;
        final ItemStack stack = slot.getStack();
        if (stack == null || stack.stackSize <= 1) return false;
        final IAEStackType<?> type = this.apeiron$typeFor(stack);
        final IAEStack<?> contained = type == null ? null : type.getStackFromContainerItem(stack);
        return contained != null && BigAEStackValues.get(contained)
            .multiply(BigInteger.valueOf(stack.stackSize))
            .compareTo(LONG_MAX) > 0;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$drainContainers(final EntityPlayerMP player) {
        ItemStack hand = player.inventory.getItemStack();
        final IAEStackType type = this.apeiron$typeFor(hand);
        if (type == null) return;
        final IMEMonitor monitor = this.getMonitorWithFilter(type);
        if (monitor == null) return;

        final int count = hand.stackSize;
        for (int index = 0; index < count && hand.stackSize > 0; index++) {
            final ItemStack one = hand.copy();
            one.stackSize = 1;
            final IAEStack<?> contained = type.getStackFromContainerItem(one);
            if (contained == null) break;

            final BigInteger perContainer = BigAEStackValues.get(contained);
            final IAEStack<?> simulatedLeftover = BigPoweredTransfers.poweredInsertBig(
                this.apeiron$powerSource(),
                monitor,
                BigAEStackValues.copyWithSize(contained, perContainer),
                this.apeiron$actionSource(),
                Actionable.SIMULATE);
            final BigInteger accepted = perContainer
                .subtract(simulatedLeftover == null ? BigInteger.ZERO : BigAEStackValues.get(simulatedLeftover));
            if (accepted.signum() <= 0) break;

            final ObjectLongPair<ItemStack> drained = type
                .drainStackFromContainer(one, BigAEStackValues.set(contained.copy(), accepted));
            if (drained.left() == null || drained.rightLong() <= 0) break;

            final IAEStack<?> drainedStack = BigAEStackValues
                .set(contained.copy(), BigInteger.valueOf(drained.rightLong()));
            final IAEStack<?> actualLeftover = BigPoweredTransfers.poweredInsertBig(
                this.apeiron$powerSource(),
                monitor,
                drainedStack,
                this.apeiron$actionSource(),
                Actionable.MODULATE);
            final BigInteger left = actualLeftover == null ? BigInteger.ZERO : BigAEStackValues.get(actualLeftover);
            final BigInteger inserted = BigInteger.valueOf(drained.rightLong())
                .subtract(left);
            if (inserted.signum() <= 0) break;

            ItemStack result = drained.left();
            if (left.signum() > 0) {
                final ObjectLongPair<ItemStack> refilled = type
                    .fillContainer(result.copy(), BigAEStackValues.set(contained.copy(), left));
                if (refilled.left() != null) result = refilled.left();
                final BigInteger refilledAmount = BigInteger.valueOf(refilled.rightLong());
                final BigInteger unfilled = left.subtract(refilledAmount);
                if (unfilled.signum() > 0) {
                    Platform.handleLeftover(player, BigAEStackValues.copyWithSize(contained, unfilled));
                }
            }

            Platform.addToPlayerInvOrDrop(player, result);
            hand.stackSize--;
            if (hand.stackSize <= 0) {
                player.inventory.setItemStack(null);
                break;
            }
        }
        this.apeiron$updateHeld(player);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$fillContainers(final EntityPlayerMP player) {
        ItemStack hand = player.inventory.getItemStack();
        final IAEStack<?> target = this.apeiron$targetStack();
        if (hand == null || target == null) return;
        final IAEStackType type = target.getStackType();
        if (!type.isContainerItemForType(hand)) return;
        final IMEMonitor monitor = this.getMonitorWithFilter(type);
        if (monitor == null) return;

        final ObjectLongPair<ItemStack> probe = this.apeiron$probeFill(type, hand, target);
        if (probe == null || probe.rightLong() <= 0) return;
        final long perContainer = probe.rightLong();
        final int count = hand.stackSize;
        for (int index = 0; index < count && hand.stackSize > 0; index++) {
            final ItemStack one = hand.copy();
            one.stackSize = 1;
            final IAEStack<?> request = BigAEStackValues.set(target.copy(), BigInteger.valueOf(perContainer));
            final IAEStack<?> extracted = BigPoweredTransfers.poweredExtractionBig(
                this.apeiron$powerSource(),
                monitor,
                request,
                this.apeiron$actionSource(),
                Actionable.MODULATE);
            if (extracted == null || BigAEStackValues.get(extracted)
                .signum() <= 0) break;

            final ObjectLongPair<ItemStack> filled = type.fillContainer(one, extracted);
            if (filled.left() == null || filled.rightLong() <= 0) {
                BigPoweredTransfers.poweredInsertBig(
                    this.apeiron$powerSource(),
                    monitor,
                    extracted,
                    this.apeiron$actionSource(),
                    Actionable.MODULATE);
                break;
            }

            final BigInteger used = BigInteger.valueOf(filled.rightLong());
            final BigInteger remainder = BigAEStackValues.get(extracted)
                .subtract(used);
            if (remainder.signum() > 0) {
                BigPoweredTransfers.poweredInsertBig(
                    this.apeiron$powerSource(),
                    monitor,
                    BigAEStackValues.copyWithSize(extracted, remainder),
                    this.apeiron$actionSource(),
                    Actionable.MODULATE);
            }
            Platform.addToPlayerInvOrDrop(player, filled.left());
            hand.stackSize--;
            if (hand.stackSize <= 0) {
                player.inventory.setItemStack(null);
                break;
            }
        }
        this.apeiron$updateHeld(player);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$quickTransfer(final int slotIndex, final EntityPlayerMP player) {
        final Slot slot = this.apeiron$slots()
            .get(slotIndex);
        final ItemStack input = slot.getStack();
        if (input == null) return;
        final IAEStackType type = this.apeiron$typeFor(input);
        if (type == null) return;
        final IAEStack<?> contained = type.getStackFromContainerItem(input);
        if (contained == null) return;
        final IMEMonitor monitor = this.getMonitorWithFilter(type);
        if (monitor == null) return;

        final BigInteger total = BigAEStackValues.get(contained)
            .multiply(BigInteger.valueOf(input.stackSize));
        final IAEStack<?> request = BigAEStackValues.copyWithSize(contained, total);
        if (BigPoweredTransfers.poweredInsertBig(
            this.apeiron$powerSource(),
            monitor,
            request,
            this.apeiron$actionSource(),
            Actionable.SIMULATE) != null) {
            return;
        }

        final IAEStack<?> leftover = BigPoweredTransfers.poweredInsertBig(
            this.apeiron$powerSource(),
            monitor,
            request,
            this.apeiron$actionSource(),
            Actionable.MODULATE);
        if (leftover != null) return;
        final ItemStack empty = type.clearFilledContainer(input.copy());
        if (empty == null || empty.getMaxStackSize() < input.stackSize) return;
        empty.stackSize = input.stackSize;
        slot.putStack(empty);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private IAEStackType<?> apeiron$typeFor(final ItemStack stack) {
        if (stack == null) return null;
        for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (type.isContainerItemForType(stack)) return type;
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ObjectLongPair<ItemStack> apeiron$probeFill(final IAEStackType type, final ItemStack hand,
        final IAEStack<?> target) {
        final ItemStack one = hand.copy();
        one.stackSize = 1;
        return type.fillContainer(one, BigAEStackValues.copyWithSize(target, BigInteger.valueOf(Long.MAX_VALUE)));
    }
}
