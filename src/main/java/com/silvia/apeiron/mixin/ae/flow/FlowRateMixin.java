package com.silvia.apeiron.mixin.ae.flow;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.silvia.apeiron.ae.flow.BigFlowRate;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.me.cache.ItemFlowGridCache.FlowRate;

/** Exact totals attached to AE2's long-based FlowRate object. */
@Mixin(value = FlowRate.class, remap = false)
public abstract class FlowRateMixin implements BigFlowRate {

    @Unique
    private BigInteger apeiron$in;

    @Unique
    private BigInteger apeiron$out;

    @Override
    public BigInteger inBig() {
        return this.apeiron$in == null ? BigInteger.valueOf(((FlowRate) (Object) this).in()) : this.apeiron$in;
    }

    @Override
    public BigInteger outBig() {
        return this.apeiron$out == null ? BigInteger.valueOf(((FlowRate) (Object) this).out()) : this.apeiron$out;
    }

    @Override
    public BigInteger netBig() {
        return inBig().subtract(outBig());
    }

    @Override
    public boolean isBigFlow() {
        return this.apeiron$in != null || this.apeiron$out != null;
    }

    @Override
    public void setBigFlow(final BigInteger in, final BigInteger out) {
        this.apeiron$in = BigAEStackValues.isBigValue(in) ? in : null;
        this.apeiron$out = BigAEStackValues.isBigValue(out) ? out : null;
    }
}
