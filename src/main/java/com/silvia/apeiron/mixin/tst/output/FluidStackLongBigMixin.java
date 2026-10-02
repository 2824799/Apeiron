package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.output.BigOutputAmount;
import com.silvia.apeiron.math.AdaptiveInteger;

@Pseudo
@Mixin(
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase$FluidStackLong",
    remap = false)
public abstract class FluidStackLongBigMixin implements BigOutputAmount {

    @Shadow
    public abstract long amount();

    @Shadow
    @Final
    @Mutable
    private long amount;
    @Unique
    private AdaptiveInteger apeiron$amount;

    @Override
    public BigInteger getOutputAmountBig() {
        return apeiron$amount == null ? BigInteger.valueOf(amount()) : apeiron$amount.toBigInteger();
    }

    @Override
    public void setOutputAmountBig(BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative fluid output");
        apeiron$amount = new AdaptiveInteger(amount);
        this.amount = BigAEStackValues.saturatedLong(amount);
    }
}
