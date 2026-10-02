package com.silvia.apeiron.mixin.aeinfinitycell.storage;

import java.math.BigInteger;
import java.util.Objects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.api.aeinfinitycell.BigCellCount;
import com.silvia.apeiron.math.AdaptiveInteger;

/** Uses the native counter fields so old methods, NEI and saves see the same exact amount. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.aeinfinitycell.storage.CellCount", remap = false)
public abstract class CellCountBigMixin implements BigCellCount {

    @Shadow
    private long value;
    @Shadow
    private BigInteger big;

    @Shadow
    public abstract void add(long amount);

    @Shadow
    public abstract long extract(long requested);

    @Override
    public void addAmountBig(final BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative cell amount");
        if (AdaptiveInteger.fitsLong(amount)) {
            add(amount.longValue());
        } else {
            big = (big == null ? BigInteger.valueOf(value) : big).add(amount);
            value = 0L;
        }
    }

    @Override
    public BigInteger extractAmountBig(final BigInteger requested) {
        Objects.requireNonNull(requested, "requested");
        if (requested.signum() < 0) throw new IllegalArgumentException("Negative cell request");
        if (AdaptiveInteger.fitsLong(requested)) return BigInteger.valueOf(extract(requested.longValue()));
        if (big == null) {
            final BigInteger removed = BigInteger.valueOf(value);
            value = 0L;
            return removed;
        }
        final BigInteger removed = big.min(requested);
        big = big.subtract(removed);
        if (AdaptiveInteger.fitsLong(big)) {
            value = big.longValue();
            big = null;
        }
        return removed;
    }
}
