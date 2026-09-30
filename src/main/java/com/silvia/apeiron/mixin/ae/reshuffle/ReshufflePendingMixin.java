package com.silvia.apeiron.mixin.ae.reshuffle;

import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.util.List;

import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;

import com.silvia.apeiron.ae.reshuffle.BigReshufflePending;
import com.silvia.apeiron.ae.reshuffle.BigReshuffleSource;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;

/** Accesses and extends AE2's private reshuffle pending entry. */
@Mixin(targets = "appeng.helpers.ReshuffleTask$PendingInjection", remap = false)
public abstract class ReshufflePendingMixin implements BigReshufflePending {

    @Shadow
    private IAEStack<?> stack;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    @SuppressWarnings("rawtypes")
    private List sources;

    @Override
    public IAEStack<?> getStackBig() {
        return this.stack;
    }

    @Override
    public void setStackBig(final IAEStack<?> stack) {
        this.stack = stack;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<?> getSourcesBig() {
        return this.sources;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addSourceBig(final IMEInventoryHandler source, final BigInteger amount) {
        this.sources.add(com.silvia.apeiron.ae.reshuffle.BigReshuffleObjects.source(source, amount));
    }
}
