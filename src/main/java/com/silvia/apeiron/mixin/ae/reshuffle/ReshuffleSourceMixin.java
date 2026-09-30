package com.silvia.apeiron.mixin.ae.reshuffle;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.silvia.apeiron.ae.reshuffle.BigReshuffleSource;

import appeng.api.storage.IMEInventoryHandler;

/** Stores an exact source contribution for a reshuffle rollback. */
@Mixin(targets = "appeng.helpers.ReshuffleTask$SourceContribution", remap = false)
public abstract class ReshuffleSourceMixin implements BigReshuffleSource {

    @Shadow
    @Final
    private IMEInventoryHandler source;

    @Shadow
    @Final
    private long amount;

    @Unique
    private BigInteger apeiron$amountBig;

    @Override
    public IMEInventoryHandler getSourceBig() {
        return this.source;
    }

    @Override
    public BigInteger getAmountBig() {
        return this.apeiron$amountBig == null ? BigInteger.valueOf(this.amount) : this.apeiron$amountBig;
    }

    @Override
    public void setAmountBig(final BigInteger amount) {
        this.apeiron$amountBig = amount;
    }
}
