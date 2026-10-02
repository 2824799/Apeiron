package com.silvia.apeiron.mixin.ae.storage;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEItemStack;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigCellInventory;
import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.ae.storage.BigStorageCell;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.CellInventory;
import appeng.me.storage.CellInventoryHandler;

/** Exact item accounting; fluid cells continue to use AE2's implementation. */
@Mixin(value = CellInventory.class, remap = false)
public abstract class CellInventoryMixin implements BigCellInventory, BigMEInventory {

    @Shadow
    @Final
    protected NBTTagCompound tagCompound;
    @Shadow
    @Final
    protected ISaveProvider container;
    @Shadow
    @Final
    protected IItemList<IAEItemStack> cellStacks;
    @Shadow
    @Final
    private ItemStack cellItem;
    @Shadow
    @Final
    private IStorageCell cellType;
    @Shadow
    @Final
    private int distTypesCount;
    @Shadow
    private int typeWeight;
    @Shadow
    private int maxTypes;
    @Shadow
    protected short storedTypes;
    @Shadow
    protected long storedCount;

    @Shadow
    protected abstract void saveChanges();

    @Shadow
    protected abstract IAEStack<?> readStack(NBTTagCompound tag);

    @Shadow
    protected abstract String getStackTypeTag();

    @Shadow
    protected abstract String getStackCountTag();

    @Unique
    private AdaptiveInteger apeiron$storedCountBig;

    @Unique
    private CellInventory<IAEItemStack> apeiron$cell() {
        return (CellInventory<IAEItemStack>) (Object) this;
    }

    @Unique
    private boolean apeiron$isItemCell() {
        return cellType.getStackType() == ITEM_STACK_TYPE;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Unique
    private IItemList<IAEStack> apeiron$rawStacks() {
        return (IItemList) (Object) cellStacks;
    }

    @Unique
    private boolean apeiron$needsBigMath() {
        return apeiron$storedCountBig != null || cellType instanceof BigStorageCell
            || tagCompound.hasKey("ApeironCellRestrictionAmount")
            || cellType.getBytesLong(cellItem) > (Long.MAX_VALUE / typeWeight) - 1;
    }

    @Unique
    private BigInteger apeiron$restriction() {
        return BigValueCodec.readNBT(tagCompound, "cellRestrictionAmount", "ApeironCellRestrictionAmount")
            .toBigInteger();
    }

    @Unique
    private BigInteger apeiron$remainingItemsCountDistGeneric(final IAEStack<?> stack) {
        BigInteger share = getTotalBytesBig().divide(BigInteger.valueOf(distTypesCount))
            .subtract(BigInteger.valueOf(apeiron$cell().getBytesPerType()))
            .multiply(BigInteger.valueOf(typeWeight));
        BigInteger restriction = apeiron$restriction();
        if (stack != null) {
            if (restriction.signum() > 0) {
                return restriction.divide(BigInteger.valueOf(distTypesCount))
                    .subtract(BigAEStackValues.get(stack))
                    .min(getRemainingItemCountBig())
                    .max(BigInteger.ZERO);
            }
            return share.subtract(BigAEStackValues.get(stack))
                .max(BigInteger.ZERO);
        }
        if (restriction.signum() > 0) share = share.min(restriction.divide(BigInteger.valueOf(distTypesCount)));
        return share.max(BigInteger.ZERO);
    }

    @Override
    public BigInteger getStoredItemCountBig() {
        return apeiron$storedCountBig == null ? BigInteger.valueOf(storedCount) : apeiron$storedCountBig.toBigInteger();
    }

    @Override
    public BigInteger getTotalTypesBig() {
        return BigInteger.valueOf(apeiron$cell().getTotalItemTypes());
    }

    @Override
    public BigInteger getStoredTypesBig() {
        return BigInteger.valueOf(Math.max(0, storedTypes));
    }

    @Override
    public BigInteger getRemainingTypesBig() {
        return getTotalTypesBig().subtract(getStoredTypesBig())
            .max(BigInteger.ZERO);
    }

    @Override
    public BigInteger getTotalBytesBig() {
        BigInteger bytes = cellType instanceof BigStorageCell ? ((BigStorageCell) cellType).getBytesBig(cellItem)
            : BigInteger.valueOf(cellType.getBytesLong(cellItem));
        if (bytes.signum() < 0) throw new IllegalStateException("negative item cell capacity");
        return bytes;
    }

    @Override
    public BigInteger getUsedBytesBig() {
        BigInteger weight = BigInteger.valueOf(typeWeight);
        BigInteger[] rounded = getStoredItemCountBig().divideAndRemainder(weight);
        BigInteger dataBytes = rounded[0].add(rounded[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
        return dataBytes.add(
            BigInteger.valueOf(storedTypes)
                .multiply(BigInteger.valueOf(apeiron$cell().getBytesPerType())));
    }

    @Override
    public BigInteger getFreeBytesBig() {
        return getTotalBytesBig().subtract(getUsedBytesBig());
    }

    @Override
    public BigInteger getRemainingItemCountBig() {
        BigInteger remaining = getFreeBytesBig().multiply(BigInteger.valueOf(typeWeight))
            .add(BigInteger.valueOf(apeiron$unusedCount()));
        BigInteger restriction = apeiron$restriction();
        if (restriction.signum() > 0) remaining = remaining.min(restriction.subtract(getStoredItemCountBig()));
        return remaining.max(BigInteger.ZERO);
    }

    @Override
    public BigInteger getRemainingItemsCountDistBig(IAEItemStack stack) {
        return getRemainingItemsCountDistBig((IAEStack<?>) stack);
    }

    @Override
    public BigInteger getRemainingItemsCountDistBig(IAEStack<?> stack) {
        BigInteger share = getTotalBytesBig().divide(BigInteger.valueOf(distTypesCount))
            .subtract(BigInteger.valueOf(apeiron$cell().getBytesPerType()))
            .multiply(BigInteger.valueOf(typeWeight));
        BigInteger restriction = apeiron$restriction();
        if (stack != null) {
            if (restriction.signum() > 0) {
                return restriction.divide(BigInteger.valueOf(distTypesCount))
                    .subtract(BigAEStackValues.get(stack))
                    .min(getRemainingItemCountBig())
                    .max(BigInteger.ZERO);
            }
            return share.subtract(BigAEStackValues.get(stack))
                .max(BigInteger.ZERO);
        }
        if (restriction.signum() > 0) share = share.min(restriction.divide(BigInteger.valueOf(distTypesCount)));
        return share.max(BigInteger.ZERO);
    }

    @Unique
    private int apeiron$unusedCount() {
        int remainder = getStoredItemCountBig().remainder(BigInteger.valueOf(typeWeight))
            .intValue();
        return remainder == 0 ? 0 : typeWeight - remainder;
    }

    @Unique
    private boolean apeiron$canHoldNewItem() {
        BigInteger free = getFreeBytesBig();
        BigInteger perType = BigInteger.valueOf(apeiron$cell().getBytesPerType());
        BigInteger restriction = apeiron$restriction();
        return (free.compareTo(perType) > 0 || free.equals(perType) && apeiron$unusedCount() > 0)
            && (restriction.signum() <= 0 || restriction.compareTo(getStoredItemCountBig()) > 0)
            && apeiron$cell().getTotalItemTypes() > storedTypes;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final Actionable mode, final BaseActionSource source) {
        if (input instanceof IAEItemStack) {
            return injectItemsBig((IAEItemStack) input, mode, source);
        }
        if (input == null || input.getStackSize() == 0) return null;
        if (input.getStackSize() < 0) throw new IllegalArgumentException("negative injection count");
        if (!apeiron$needsBigMath() && !BigAEStackValues.isBig(input)) {
            return (IAEStack<?>) ((CellInventory) (Object) this).injectItems(input, mode, source);
        }
        if (mode == Actionable.MODULATE && input.isCraftable()) input.setCraftable(false);
        if (cellType.isBlackListed(input)) return input;

        IAEStack stored = apeiron$rawStacks().findPrecise(input);
        if (stored != null && BigAEStackValues.get(stored)
            .signum() <= 0) stored = null;
        if (stored == null && !apeiron$canHoldNewItem()) return input;

        BigInteger room = apeiron$cell().isDistribution() ? apeiron$remainingItemsCountDistGeneric(stored)
            : getRemainingItemCountBig();
        if (stored == null && !apeiron$cell().isDistribution() && apeiron$restriction().signum() <= 0) {
            BigInteger byteRoom = getFreeBytesBig().subtract(BigInteger.valueOf(apeiron$cell().getBytesPerType()))
                .multiply(BigInteger.valueOf(typeWeight))
                .add(BigInteger.valueOf(apeiron$unusedCount()));
            room = room.min(byteRoom)
                .max(BigInteger.ZERO);
        }
        if (room.signum() == 0) return stored != null && apeiron$cell().isOverflow() ? null : input;

        BigInteger requested = BigAEStackValues.get(input);
        BigInteger accepted = requested.min(room);
        if (mode == Actionable.MODULATE) {
            if (stored == null) {
                IAEStack added = BigAEStackValues.copyWithSize(input, accepted);
                added.setCraftable(false);
                apeiron$rawStacks().add(added);
            } else {
                BigAEStackValues.set(
                    stored,
                    BigAEStackValues.get(stored)
                        .add(accepted));
            }
            saveChanges();
        }
        return requested.equals(accepted) || apeiron$cell().isOverflow() ? null
            : BigAEStackValues.copyWithSize(input, requested.subtract(accepted));
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IAEStack<?> extractItemsBig(final IAEStack<?> request, final Actionable mode,
        final BaseActionSource source) {
        if (request instanceof IAEItemStack) {
            return extractItemsBig((IAEItemStack) request, mode, source);
        }
        if (request == null || request.getStackSize() == 0) return null;
        if (request.getStackSize() < 0) throw new IllegalArgumentException("negative extraction count");
        if (!apeiron$needsBigMath() && !BigAEStackValues.isBig(request)) {
            return (IAEStack<?>) ((CellInventory) (Object) this).extractItems(request, mode, source);
        }

        IAEStack stored = apeiron$rawStacks().findPrecise(request);
        if (stored == null || BigAEStackValues.get(stored)
            .signum() <= 0) return null;
        BigInteger extracted = BigAEStackValues.get(request)
            .min(BigAEStackValues.get(stored));
        IAEStack result = BigAEStackValues.copyWithSize(stored, extracted);
        if (mode == Actionable.MODULATE) {
            BigAEStackValues.set(
                stored,
                BigAEStackValues.get(stored)
                    .subtract(extracted));
            saveChanges();
        }
        return result;
    }

    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        if (!apeiron$isItemCell()) throw new UnsupportedOperationException("item API used on a non-item cell");
        if (input == null || input.getStackSize() == 0) return null;
        if (input.getStackSize() < 0) throw new IllegalArgumentException("negative injection count");
        if (!apeiron$needsBigMath() && !BigAEItemStacks.isStackSizeBig(input)) {
            return apeiron$cell().injectItems(input, mode, source);
        }
        if (mode == Actionable.MODULATE && input.isCraftable()) input.setCraftable(false);
        if (cellType.isBlackListed(input)) return input;
        if (input.getItem() instanceof IStorageCell) {
            IStorageCell nestedType = (IStorageCell) input.getItem();
            if (!nestedType.storableInStorageCell()) {
                CellInventoryHandler<?> nested = (CellInventoryHandler<?>) CellInventory
                    .getCell(input.getItemStack(), null, nestedType.getStackType());
                if (nested != null && nested.getUsedBytes() > 0) return input;
            }
        }
        IAEItemStack stored = cellStacks.findPrecise(input);
        if (stored != null && stored.getStackSize() <= 0) stored = null;
        if (stored == null && !apeiron$canHoldNewItem()) return input;
        BigInteger room = apeiron$cell().isDistribution() ? getRemainingItemsCountDistBig(stored)
            : getRemainingItemCountBig();
        if (stored == null && !apeiron$cell().isDistribution() && apeiron$restriction().signum() <= 0) {
            // Reserve the new type's metadata bytes before accepting its contents.
            BigInteger byteRoom = getFreeBytesBig().subtract(BigInteger.valueOf(apeiron$cell().getBytesPerType()))
                .multiply(BigInteger.valueOf(typeWeight))
                .add(BigInteger.valueOf(apeiron$unusedCount()));
            room = room.min(byteRoom)
                .max(BigInteger.ZERO);
        }
        if (room.signum() == 0) return stored != null && apeiron$cell().isOverflow() ? null : input;
        BigInteger requested = BigAEItemStacks.stackSize(input);
        BigInteger accepted = requested.min(room);
        if (mode == Actionable.MODULATE) {
            if (stored == null) {
                IAEItemStack added = BigAEItemStacks.copyWithSize(input, accepted);
                added.setCraftable(false);
                cellStacks.add(added);
            } else {
                ((BigAEItemStack) stored).incStackSizeBig(accepted);
            }
            saveChanges();
        }
        return requested.equals(accepted) || apeiron$cell().isOverflow() ? null
            : BigAEItemStacks.copyWithSize(input, requested.subtract(accepted));
    }

    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        if (!apeiron$isItemCell()) throw new UnsupportedOperationException("item API used on a non-item cell");
        if (request == null || request.getStackSize() == 0) return null;
        if (request.getStackSize() < 0) throw new IllegalArgumentException("negative extraction count");
        if (!apeiron$needsBigMath() && !BigAEItemStacks.isStackSizeBig(request)) {
            return apeiron$cell().extractItems(request, mode, source);
        }
        IAEItemStack stored = cellStacks.findPrecise(request);
        if (stored == null || stored.getStackSize() <= 0) return null;
        BigInteger extracted = BigAEItemStacks.stackSize(request)
            .min(BigAEItemStacks.stackSize(stored));
        IAEItemStack result = BigAEItemStacks.copyWithSize(stored, extracted);
        if (mode == Actionable.MODULATE) {
            ((BigAEItemStack) stored).decStackSizeBig(extracted);
            saveChanges();
        }
        return result;
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(IAEStack<?> input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (apeiron$isItemCell() && (apeiron$needsBigMath()
            || input instanceof IAEItemStack && BigAEItemStacks.isStackSizeBig((IAEItemStack) input))) {
            cir.setReturnValue(injectItemsBig((IAEItemStack) input, mode, source));
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(IAEStack<?> request, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (apeiron$isItemCell() && (apeiron$needsBigMath()
            || request instanceof IAEItemStack && BigAEItemStacks.isStackSizeBig((IAEItemStack) request))) {
            cir.setReturnValue(extractItemsBig((IAEItemStack) request, mode, source));
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyGenericInject(IAEStack<?> input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (!apeiron$isItemCell() && (apeiron$needsBigMath() || BigAEStackValues.isBig(input))) {
            cir.setReturnValue(injectItemsBig(input, mode, source));
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyGenericExtract(IAEStack<?> request, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (!apeiron$isItemCell() && (apeiron$needsBigMath() || BigAEStackValues.isBig(request))) {
            cir.setReturnValue(extractItemsBig(request, mode, source));
        }
    }

    @Inject(method = "saveChanges", at = @At("HEAD"), cancellable = true)
    private void apeiron$saveExact(CallbackInfo ci) {
        AdaptiveInteger total = new AdaptiveInteger(0L);
        int oldTypes = storedTypes;
        int slot = 0;
        for (IAEStack stack : apeiron$rawStacks()) {
            if (BigAEStackValues.get(stack)
                .signum() <= 0) continue;
            AdaptiveInteger count = new AdaptiveInteger(BigAEStackValues.get(stack));
            if (count.isBig()) total.add(count.toBigInteger());
            else total.add(count.longValueExact());
            NBTTagCompound stackTag = tagCompound.getCompoundTag("#" + slot);
            stack.writeToNBT(stackTag);
            tagCompound.setTag("#" + slot, stackTag);
            BigValueCodec.writeNBT(tagCompound, "@" + slot, "Apeiron@" + slot, count);
            slot++;
        }
        storedTypes = (short) slot;
        if (slot == 0) tagCompound.removeTag(getStackTypeTag());
        else tagCompound.setShort(getStackTypeTag(), storedTypes);
        apeiron$setTotal(total);
        for (; slot < oldTypes; slot++) {
            tagCompound.removeTag("#" + slot);
            tagCompound.removeTag("@" + slot);
            tagCompound.removeTag("Apeiron@" + slot);
        }
        if (container != null) container.saveChanges(apeiron$cell());
        ci.cancel();
    }

    @Unique
    private void apeiron$setTotal(AdaptiveInteger total) {
        storedCount = total.longValueSaturated();
        apeiron$storedCountBig = total.isBig() ? total : null;
        BigValueCodec.writeNBT(tagCompound, getStackCountTag(), "Apeiron" + getStackCountTag(), total);
        if (total.signum() == 0) tagCompound.removeTag(getStackCountTag());
    }

    @Inject(method = "loadCellStacks", at = @At("HEAD"), cancellable = true)
    private void apeiron$loadExact(CallbackInfo ci) {
        // Validate an existing exact total before recomputing it from the entries.
        BigValueCodec.readNBT(tagCompound, getStackCountTag(), "Apeiron" + getStackCountTag());
        AdaptiveInteger total = new AdaptiveInteger(0L);
        int declaredTypes = storedTypes;
        if (declaredTypes < 0 || declaredTypes > maxTypes)
            throw new IllegalArgumentException("invalid cell type count");
        for (int slot = 0; slot < declaredTypes; slot++) {
            NBTTagCompound stackTag = tagCompound.getCompoundTag("#" + slot);
            IAEStack stack = readStack(stackTag);
            if (stack == null) continue;
            if (tagCompound.hasKey("Apeiron@" + slot)) {
                BigAEStackValues.set(
                    stack,
                    BigValueCodec.readNBT(tagCompound, "@" + slot, "Apeiron@" + slot)
                        .toBigInteger());
            } else if (!stackTag.hasKey("ApeironCnt")) {
                long outer = tagCompound.getLong("@" + slot);
                if (outer > 0) BigAEStackValues.set(stack, BigInteger.valueOf(outer));
            }
            if (BigAEStackValues.get(stack)
                .signum() < 0) throw new IllegalArgumentException("negative stored item count");
            if (BigAEStackValues.get(stack)
                .signum() == 0) continue;
            apeiron$rawStacks().add(stack);
            total.add(BigAEStackValues.get(stack));
        }
        storedTypes = (short) cellStacks.size();
        apeiron$setTotal(total);
        if (storedTypes != declaredTypes) {
            storedTypes = (short) declaredTypes;
            saveChanges();
        }
        ci.cancel();
    }

    @Inject(method = "getAvailableItem", at = @At("HEAD"), cancellable = true)
    private void apeiron$availableExact(IAEStack<?> request, int iteration, CallbackInfoReturnable<IAEStack<?>> cir) {
        if (apeiron$storedCountBig != null) {
            IAEStack found = apeiron$rawStacks().findPrecise(request);
            if (found == null || BigAEStackValues.get(found)
                .signum() <= 0) {
                cir.setReturnValue(null);
            } else {
                IAEStack result = request.copy();
                BigAEStackValues.set(result, BigAEStackValues.get(found));
                cir.setReturnValue(result);
            }
        }
    }

    @Inject(method = "getTotalBytes", at = @At("HEAD"), cancellable = true)
    private void apeiron$totalBytes(CallbackInfoReturnable<Long> cir) {
        if (cellType instanceof BigStorageCell) cir.setReturnValue(apeiron$saturate(getTotalBytesBig()));
    }

    @Inject(method = "getUsedBytes", at = @At("HEAD"), cancellable = true)
    private void apeiron$usedBytes(CallbackInfoReturnable<Long> cir) {
        if (apeiron$needsBigMath()) cir.setReturnValue(apeiron$saturate(getUsedBytesBig()));
    }

    @Inject(method = "getFreeBytes", at = @At("HEAD"), cancellable = true)
    private void apeiron$freeBytes(CallbackInfoReturnable<Long> cir) {
        if (apeiron$needsBigMath()) cir.setReturnValue(apeiron$saturate(getFreeBytesBig()));
    }

    @Inject(method = "getRemainingItemCount", at = @At("HEAD"), cancellable = true)
    private void apeiron$remainingCount(CallbackInfoReturnable<Long> cir) {
        if (apeiron$needsBigMath()) cir.setReturnValue(apeiron$saturate(getRemainingItemCountBig()));
    }

    @Inject(method = "getRemainingItemsCountDist", at = @At("HEAD"), cancellable = true)
    private void apeiron$remainingDistribution(IAEStack<?> stack, CallbackInfoReturnable<Long> cir) {
        if (apeiron$needsBigMath()) {
            cir.setReturnValue(apeiron$saturate(apeiron$remainingItemsCountDistGeneric(stack)));
        }
    }

    @Inject(method = "getUnusedItemCount", at = @At("HEAD"), cancellable = true)
    private void apeiron$unused(CallbackInfoReturnable<Integer> cir) {
        if (apeiron$needsBigMath()) cir.setReturnValue(apeiron$unusedCount());
    }

    @Inject(method = "canHoldNewItem", at = @At("HEAD"), cancellable = true)
    private void apeiron$newType(CallbackInfoReturnable<Boolean> cir) {
        if (apeiron$needsBigMath()) cir.setReturnValue(apeiron$canHoldNewItem());
    }

    @Inject(method = "getRemainingItemTypes", at = @At("HEAD"), cancellable = true)
    private void apeiron$remainingTypes(CallbackInfoReturnable<Long> cir) {
        if (apeiron$needsBigMath()) {
            long perType = apeiron$cell().getBytesPerType();
            BigInteger byBytes = perType > 0 ? getFreeBytesBig().divide(BigInteger.valueOf(perType))
                : BigInteger.valueOf(maxTypes);
            cir.setReturnValue(
                apeiron$saturate(byBytes.min(BigInteger.valueOf(apeiron$cell().getTotalItemTypes() - storedTypes))));
        }
    }

    @Unique
    private static long apeiron$saturate(BigInteger value) {
        return AdaptiveInteger.fitsLong(value) ? value.longValue()
            : value.signum() >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
}
