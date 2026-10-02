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
    targets = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase$ItemStackLong",
    remap = false)
public abstract class ItemStackLongBigMixin implements BigOutputAmount {

    @Shadow
    public abstract long stackSize();

    @Shadow
    @Final
    @Mutable
    private long stackSize;
    @Unique
    private AdaptiveInteger apeiron$amount;

    @Override
    public BigInteger getOutputAmountBig() {
        return apeiron$amount == null ? BigInteger.valueOf(stackSize()) : apeiron$amount.toBigInteger();
    }

    @Override
    public void setOutputAmountBig(BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative item output");
        apeiron$amount = new AdaptiveInteger(amount);
        stackSize = BigAEStackValues.saturatedLong(amount);
    }
}
