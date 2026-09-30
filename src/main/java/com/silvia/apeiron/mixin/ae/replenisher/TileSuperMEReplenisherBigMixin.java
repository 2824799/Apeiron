package com.silvia.apeiron.mixin.ae.replenisher;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.automation.BigSuperMEReplenisher;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.util.item.IAEStackList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/** Extends Super ME Replenisher byte accounting and item movement past long. */
@Mixin(value = TileSuperMEReplenisher.class, remap = false)
public abstract class TileSuperMEReplenisherBigMixin implements BigSuperMEReplenisher {

    @Shadow @Final private IAEStackList storage;
    @Shadow @Final private IAEStackList out;
    @Shadow @Final private Object2IntOpenHashMap<IAEStackType<?>> unusedCount;
    @Shadow @Final private AppEngInternalInventory cells;
    @Shadow private long totalBytes;
    @Shadow private long usedBytes;
    @Shadow private int status;
    @Shadow @Final private BaseActionSource src;

    @Shadow private void updatePowerDraw() {}
    @Shadow private void postStorageChange(IAEStack<?> stack, long amount, BaseActionSource source) {}

    @Unique
    private TileSuperMEReplenisher apeiron$self() {
        return (TileSuperMEReplenisher) (Object) this;
    }

    @Override
    public BigInteger getTotalBytesBig() {
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < this.cells.getSizeInventory(); i++) {
            final ItemStack cell = this.cells.getStackInSlot(i);
            if (cell != null && cell.getItem() instanceof IStorageCell storageCell) {
                total = total.add(BigInteger.valueOf(storageCell.getBytesLong(cell)));
            }
        }
        return total;
    }

    @Override
    public BigInteger getUsedBytesBig() {
        return this.apeiron$rebuildUsedBytes();
    }

    @Unique
    private BigInteger apeiron$rebuildUsedBytes() {
        this.unusedCount.clear();
        BigInteger used = BigInteger.ZERO;
        for (final IAEStack<?> stack : this.storage) used = used.add(this.apeiron$countStack(stack));
        for (final IAEStack<?> stack : this.out) used = used.add(this.apeiron$countStack(stack));
        for (final int remainder : this.unusedCount.values()) {
            if (remainder != 0) used = used.add(BigInteger.ONE);
        }
        this.usedBytes = BigAEStackValues.saturatedLong(used);
        return used;
    }

    @Unique
    private BigInteger apeiron$countStack(final IAEStack<?> stack) {
        final int weight = stack.getStackType().getAmountPerByte();
        final BigInteger[] quotient = BigAEStackValues.get(stack)
                .divideAndRemainder(BigInteger.valueOf(weight));
        final int previous = this.unusedCount.getOrDefault(stack.getStackType(), 0);
        final BigInteger remainder = quotient[1].add(BigInteger.valueOf(previous));
        this.unusedCount.put(
                stack.getStackType(),
                remainder.remainder(BigInteger.valueOf(weight)).intValue());
        return quotient[0].add(remainder.divide(BigInteger.valueOf(weight)));
    }

    @Unique
    private boolean apeiron$needsExact(final IAEStack<?> stack) {
        return BigAEStackValues.isBig(stack)
                || !BigAEStackValues.fitsLong(this.getTotalBytesBig())
                || !BigAEStackValues.fitsLong(this.getUsedBytesBig());
    }

    @Unique
    private static BigInteger apeiron$bytesFor(final BigInteger amount, final int amountPerByte) {
        if (amount.signum() <= 0) return BigInteger.ZERO;
        final BigInteger divisor = BigInteger.valueOf(amountPerByte);
        return amount.add(divisor).subtract(BigInteger.ONE).divide(divisor);
    }

    @Unique
    private static int apeiron$remainder(final BigInteger amount, final int amountPerByte) {
        return amount.remainder(BigInteger.valueOf(amountPerByte)).intValue();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Unique
    private IAEStack<?> apeiron$injectExact(final IAEStack<?> input, final Actionable mode,
        final BaseActionSource source) {
        if (input == null) return null;
        final IAEStackType type = input.getStackType();
        final int weight = type.getAmountPerByte();
        final BigInteger usedBefore = this.apeiron$rebuildUsedBytes();
        final BigInteger free = this.getTotalBytesBig().subtract(usedBefore);
        final BigInteger stackSize = BigAEStackValues.get(input);
        final int unused = this.unusedCount.getOrDefault(type, 0);
        if (free.signum() < 0 || free.signum() == 0 && unused == 0) return input;

        final int freeUnused = unused == 0 ? 0 : weight - unused;
        final BigInteger toCount = stackSize.compareTo(BigInteger.valueOf(freeUnused)) > 0
                ? stackSize.subtract(BigInteger.valueOf(freeUnused)) : BigInteger.ZERO;
        final BigInteger needBytes = apeiron$bytesFor(toCount, weight);
        final int newUnused = toCount.signum() == 0
                ? apeiron$remainder(BigInteger.valueOf(unused).add(stackSize), weight)
                : apeiron$remainder(toCount, weight);

        if (mode == Actionable.SIMULATE) {
            if (free.compareTo(needBytes) >= 0) return null;
            final BigInteger capacity = free.multiply(BigInteger.valueOf(weight))
                    .add(BigInteger.valueOf(freeUnused));
            return BigAEStackValues.copyWithSize(input, stackSize.subtract(capacity).max(BigInteger.ZERO));
        }

        this.status = 1;
        if (free.compareTo(needBytes) >= 0) {
            this.unusedCount.put(type, newUnused);
            final BigInteger used = this.getUsedBytesBig().add(needBytes);
            this.usedBytes = BigAEStackValues.saturatedLong(used);
            this.storage.add(input);
            this.postStorageChange(input, BigAEStackValues.saturatedLong(stackSize), source);
            return null;
        }

        final BigInteger capacity = free.multiply(BigInteger.valueOf(weight))
                .add(BigInteger.valueOf(freeUnused)).max(BigInteger.ZERO).min(stackSize);
        final IAEStack<?> allowed = BigAEStackValues.copyWithSize(input, capacity);
        this.storage.add(allowed);
        this.unusedCount.put(type, 0);
        this.usedBytes = BigAEStackValues.saturatedLong(usedBefore.add(free));
        this.postStorageChange(allowed, BigAEStackValues.saturatedLong(capacity), source);
        return capacity.signum() == 0 ? input : BigAEStackValues.copyWithSize(input, stackSize.subtract(capacity));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Unique
    private IAEStack<?> apeiron$extractExact(final IAEStack<?> request, final Actionable mode,
        final BaseActionSource source) {
        if (request == null) return null;
        final IAEStack<?> stored = this.storage.findPrecise(request);
        if (stored == null) return null;
        final BigInteger available = BigAEStackValues.get(stored);
        if (available.signum() <= 0) return null;
        final BigInteger amount = BigAEStackValues.get(request).min(available);
        final IAEStack<?> result = BigAEStackValues.copyWithSize(stored, amount);
        if (mode == Actionable.MODULATE) {
            final int weight = request.getStackType().getAmountPerByte();
            final BigInteger used = this.getUsedBytesBig();
            BigAEStackValues.set(stored, available.subtract(amount));
            final int unused = this.unusedCount.getOrDefault(request.getStackType(), 0);
            final BigInteger partial = BigInteger.valueOf(unused == 0 ? 0 : weight - unused)
                    .add(amount.remainder(BigInteger.valueOf(weight)));
            final BigInteger freed = amount.divide(BigInteger.valueOf(weight))
                    .add(partial.divide(BigInteger.valueOf(weight)));
            final int next = partial.remainder(BigInteger.valueOf(weight)).intValue();
            this.unusedCount.put(request.getStackType(), next == 0 ? 0 : weight - next);
            this.usedBytes = BigAEStackValues.saturatedLong(used.subtract(freed));
            this.postStorageChange(result, BigAEStackValues.saturatedLong(amount.negate()), source);
        }
        return result;
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(final IAEStack<?> input, final Actionable mode, final BaseActionSource source,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (this.apeiron$needsExact(input)) cir.setReturnValue(this.apeiron$injectExact(input, mode, source));
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(final IAEStack<?> request, final Actionable mode,
        final BaseActionSource source, final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (this.apeiron$needsExact(request)) cir.setReturnValue(this.apeiron$extractExact(request, mode, source));
    }

    @Inject(method = "getTotalBytes", at = @At("HEAD"), cancellable = true)
    private void apeiron$totalBytes(final CallbackInfoReturnable<Long> cir) {
        final BigInteger total = this.getTotalBytesBig();
        if (!BigAEStackValues.fitsLong(total)) cir.setReturnValue(BigAEStackValues.saturatedLong(total));
    }

    @Inject(method = "getUsedBytes", at = @At("HEAD"), cancellable = true)
    private void apeiron$usedBytes(final CallbackInfoReturnable<Long> cir) {
        final BigInteger used = this.getUsedBytesBig();
        if (!BigAEStackValues.fitsLong(used)) cir.setReturnValue(BigAEStackValues.saturatedLong(used));
    }
}
