package com.silvia.apeiron.mixin.ae.reshuffle;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.reshuffle.BigReshuffleItemChangeAccess;

import appeng.helpers.ReshuffleReport.ItemChange;
import appeng.api.storage.data.IAEStack;

/** Stores exact counts beside AE's legacy report fields. */
@Mixin(value = ItemChange.class, remap = false)
public abstract class ReshuffleItemChangeMixin implements BigReshuffleItemChangeAccess {

    @Shadow @Final public long beforeCount;
    @Shadow @Final public long afterCount;

    @Unique private BigInteger apeiron$before;
    @Unique private BigInteger apeiron$after;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void apeiron$initExact(final IAEStack<?> stack, final long before, final long after, final CallbackInfo ci) {
        this.apeiron$before = BigInteger.valueOf(before);
        this.apeiron$after = BigInteger.valueOf(after);
    }

    @Override
    public BigInteger getBeforeCountBig() {
        return this.apeiron$before == null ? BigInteger.valueOf(this.beforeCount) : this.apeiron$before;
    }

    @Override
    public BigInteger getAfterCountBig() {
        return this.apeiron$after == null ? BigInteger.valueOf(this.afterCount) : this.apeiron$after;
    }

    @Override
    public BigInteger getDifferenceBig() {
        return getAfterCountBig().subtract(getBeforeCountBig());
    }

    @Override
    public void setCountsBig(final BigInteger before, final BigInteger after) {
        this.apeiron$before = before;
        this.apeiron$after = after;
    }
}
