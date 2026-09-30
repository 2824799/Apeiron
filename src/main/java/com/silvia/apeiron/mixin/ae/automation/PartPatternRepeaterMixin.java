package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.BigStorageInterceptor;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.parts.misc.PartPatternRepeater;

/** Keeps pattern repeater waiting stacks exact while forwarding them to the target network. */
@Mixin(value = PartPatternRepeater.class, remap = false)
public abstract class PartPatternRepeaterMixin implements BigStorageInterceptor {

    @Shadow
    @Final
    private Map<IAEStackType<?>, MEMonitorPassThrough> monitors;

    @Shadow
    private IItemList<IAEStack<?>> waitingStacks;

    @Shadow
    private MachineSource actionSource;

    @Shadow
    private boolean injecting;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public IAEStack<?> injectItemsBig(final IAEStack<?> input, final Actionable type, final BaseActionSource src) {
        if (input == null) return null;

        final IAEStack<?> waitingStack = waitingStacks.findPrecise(input);
        if (!BigAEStackValues.isBig(input)
            && (waitingStack == null || !BigAEStackValues.isBig(waitingStack))) return input;

        if (injecting) {
            return input;
        }
        injecting = true;
        try {
            final BigInteger inputSize = BigAEStackValues.get(input);
            BigInteger leftOver = BigInteger.ZERO;
            final IAEStack<?> tempStack = input.copy();
            if (waitingStack != null) {
                final BigInteger waitingSize = BigAEStackValues.get(waitingStack);
                if (inputSize.compareTo(waitingSize) > 0) {
                    leftOver = inputSize.subtract(waitingSize);
                    BigAEStackValues.set(tempStack, waitingSize);
                }
            }

            final MEMonitorPassThrough monitor = monitors.get(tempStack.getStackType());
            if (monitor == null) {
                return input;
            }
            final BigInteger tempSize = BigAEStackValues.get(tempStack);
            final IAEStack<?> result = BigMEInventories.injectItemsBig(
                (IMEInventory) monitor,
                tempStack,
                type,
                actionSource);
            final BigInteger reducedSize = result == null ? BigInteger.ZERO : BigAEStackValues.get(result);
            final BigInteger returnSize = reducedSize.add(leftOver);

            if (waitingStack != null && type == Actionable.MODULATE) {
                final BigInteger next = BigAEStackValues.get(waitingStack)
                    .subtract(tempSize)
                    .add(reducedSize);
                BigAEStackValues.set(waitingStack, next);
            }

            return returnSize.signum() > 0 ? BigAEStackValues.copyWithSize(input, returnSize) : null;
        } finally {
            injecting = false;
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$injectBig(final IAEStack<?> input, final Actionable type, final BaseActionSource src,
        final CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(input)) cir.setReturnValue(this.injectItemsBig(input, type, src));
    }
}
