package com.silvia.apeiron.mixin.aeinfinitycell.storage;

import java.math.BigInteger;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.aeinfinitycell.BigCellCount;
import com.silvia.apeiron.api.aeinfinitycell.BigInfinityCellRecord;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.aeinfinitycell.storage.CellCount;
import cn.dancingsnow.aeinfinitycell.storage.EssentiaStackKey;
import cn.dancingsnow.aeinfinitycell.storage.FluidStackKey;
import cn.dancingsnow.aeinfinitycell.storage.ItemStackKey;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.AEEssentiaStackType;

/** Updates native records in place, including removal of completely drained types. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord", remap = false)
public abstract class InfinityCellRecordBigMixin implements BigInfinityCellRecord {

    @Shadow
    @Final
    private Map<ItemStackKey, CellCount> items;
    @Shadow
    @Final
    private Map<FluidStackKey, CellCount> fluids;
    @Shadow
    @Final
    private Map<EssentiaStackKey, CellCount> essentia;
    @Shadow
    @Final
    private CellCount eu;

    @Override
    public boolean canStoreStackBig(final IAEStack<?> input) {
        return input != null && ("appeu.eu".equals(
            input.getStackType()
                .getId())
            || apeiron$key(input) != null);
    }

    @Override
    public boolean addStackBig(final IAEStack<?> input, final BigInteger amount) {
        apeiron$validate(amount);
        if (input == null || amount.signum() == 0) return false;
        final CellCount count;
        if ("appeu.eu".equals(
            input.getStackType()
                .getId())) {
            count = eu;
        } else {
            final Object key = apeiron$key(input);
            if (key == null) return false;
            count = apeiron$map(input.getStackType()).computeIfAbsent(key, ignored -> new CellCount());
        }
        ((BigCellCount) (Object) count).addAmountBig(amount);
        return true;
    }

    @Override
    public BigInteger extractStackBig(final IAEStack<?> request, final BigInteger amount, final boolean modulate) {
        apeiron$validate(amount);
        if (request == null || amount.signum() == 0) return BigInteger.ZERO;
        final boolean energy = "appeu.eu".equals(
            request.getStackType()
                .getId());
        final Object key = energy ? null : apeiron$key(request);
        if (!energy && key == null) return BigInteger.ZERO;
        final Map<Object, CellCount> map = energy ? null : apeiron$map(request.getStackType());
        final CellCount count = energy ? eu : map.get(key);
        if (count == null) return BigInteger.ZERO;
        final BigInteger extracted = amount.min(count.toBigInteger());
        if (modulate && extracted.signum() > 0) {
            ((BigCellCount) (Object) count).extractAmountBig(extracted);
            if (!energy && count.isZero()) map.remove(key);
        }
        return extracted;
    }

    @Override
    public BigInteger getAmountBig(final IAEStack<?> request) {
        if (request == null) return BigInteger.ZERO;
        if ("appeu.eu".equals(
            request.getStackType()
                .getId()))
            return eu.toBigInteger();
        final Object key = apeiron$key(request);
        final CellCount count = key == null ? null : apeiron$map(request.getStackType()).get(key);
        return count == null ? BigInteger.ZERO : count.toBigInteger();
    }

    @Override
    public BigInteger getStoredUnitsBig(final IAEStackType<?> type) {
        if ("appeu.eu".equals(type.getId())) return eu.toBigInteger();
        BigInteger total = BigInteger.ZERO;
        for (final CellCount count : apeiron$map(type).values()) total = total.add(count.toBigInteger());
        return total;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public IItemList<?> getAvailableStacksBig(final IAEStackType<?> type, final IItemList<?> out) {
        if ("appeu.eu".equals(type.getId())) {
            if (eu.isPositive())
                ((IItemList) out).addStorage(BigAEStackValues.copyWithSize(type.getTestStack(), eu.toBigInteger()));
            return out;
        }
        for (final Map.Entry<Object, CellCount> entry : apeiron$map(type).entrySet()) {
            final Object key = entry.getKey();
            final IAEStack<?> stack;
            if (key instanceof ItemStackKey) stack = AEItemStack.create(((ItemStackKey) key).toStack(1));
            else if (key instanceof FluidStackKey) stack = AEFluidStack.create(((FluidStackKey) key).toStack(1));
            else stack = ((EssentiaStackKey) key).toStack(1);
            if (stack != null && entry.getValue()
                .isPositive()) {
                BigAEStackValues.set(
                    stack,
                    entry.getValue()
                        .toBigInteger());
                ((IItemList) out).addStorage(stack);
            }
        }
        return out;
    }

    @Unique
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<Object, CellCount> apeiron$map(final IAEStackType<?> type) {
        if (type.getId()
            .equals(appeng.util.item.AEItemStackType.ITEM_STACK_TYPE.getId())) return (Map) items;
        if (type.getId()
            .equals(appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE.getId())) return (Map) fluids;
        if (type.getId()
            .equals(AEEssentiaStackType.ESSENTIA_STACK_TYPE.getId())) return (Map) essentia;
        throw new IllegalArgumentException("Unsupported Infinity Cell channel: " + type.getId());
    }

    @Unique
    private static Object apeiron$key(final IAEStack<?> stack) {
        try {
            if (stack instanceof IAEItemStack) return ItemStackKey.from(((IAEItemStack) stack).getItemStack());
            if (stack instanceof IAEFluidStack) return FluidStackKey.from(((IAEFluidStack) stack).getFluidStack());
            if (stack instanceof AEEssentiaStack) return EssentiaStackKey.from((AEEssentiaStack) stack);
        } catch (final IllegalArgumentException invalidType) {
            return null;
        }
        return null;
    }

    @Unique
    private static void apeiron$validate(final BigInteger amount) {
        if (amount == null || amount.signum() < 0)
            throw new IllegalArgumentException("Negative or missing cell amount");
    }
}
