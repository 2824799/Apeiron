package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerCraftConfirm;

/** Keeps the crafting confirmation plan exact while it is converted into update packets. */
@Mixin(value = ContainerCraftConfirm.class, remap = false)
public abstract class ContainerCraftConfirmMixin {

    private static final ThreadLocal<BigInteger> APEIRON_NEXT_STACK_SIZE = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> APEIRON_NEXT_REQUESTABLE = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> APEIRON_NEXT_REQUESTABLE_CRAFTS = new ThreadLocal<>();

    @Redirect(
        method = "detectAndSendChanges",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;getStackSize()J",
            ordinal = 0))
    private long apeiron$capturePlannedStackSize(final IAEStack<?> stack) {
        APEIRON_NEXT_STACK_SIZE.remove();
        if (stack instanceof BigAEStack && ((BigAEStack) stack).isStackSizeBig()) {
            APEIRON_NEXT_STACK_SIZE.set(((BigAEStack) stack).getStackSizeBig());
        }
        return stack.getStackSize();
    }

    @Redirect(
        method = "detectAndSendChanges",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            ordinal = 0))
    private IAEStack<?> apeiron$restoreExtractStackSize(final IAEStack<?> stack, final long value) {
        final BigInteger exact = APEIRON_NEXT_STACK_SIZE.get();
        APEIRON_NEXT_STACK_SIZE.remove();
        return exact == null ? stack.setStackSize(value) : BigAEStackValues.set(stack, exact);
    }

    @Redirect(
        method = "detectAndSendChanges",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;getCountRequestable()J",
            ordinal = 0))
    private long apeiron$captureRequestable(final IAEStack<?> stack) {
        APEIRON_NEXT_REQUESTABLE.remove();
        if (stack instanceof BigAERequestableStack && ((BigAERequestableStack) stack).isCountRequestableBig()) {
            APEIRON_NEXT_REQUESTABLE.set(((BigAERequestableStack) stack).getCountRequestableBig());
        }
        return stack.getCountRequestable();
    }

    @Redirect(
        method = "detectAndSendChanges",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            ordinal = 1))
    private IAEStack<?> apeiron$restoreCraftRequestSize(final IAEStack<?> stack, final long value) {
        final BigInteger exact = APEIRON_NEXT_REQUESTABLE.get();
        APEIRON_NEXT_REQUESTABLE.remove();
        return exact == null ? stack.setStackSize(value) : BigAEStackValues.set(stack, exact);
    }

    @Redirect(
        method = "detectAndSendChanges",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;getCountRequestableCrafts()J",
            ordinal = 0))
    private long apeiron$captureRequestableCrafts(final IAEStack<?> stack) {
        APEIRON_NEXT_REQUESTABLE_CRAFTS.remove();
        if (stack instanceof BigAERequestableStack
            && ((BigAERequestableStack) stack).isCountRequestableCraftsBig()) {
            APEIRON_NEXT_REQUESTABLE_CRAFTS.set(((BigAERequestableStack) stack).getCountRequestableCraftsBig());
        }
        return stack.getCountRequestableCrafts();
    }

    @Redirect(
        method = "detectAndSendChanges",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setCountRequestableCrafts(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$restoreRequestableCrafts(final IAEStack<?> stack, final long value) {
        final BigInteger exact = APEIRON_NEXT_REQUESTABLE_CRAFTS.get();
        APEIRON_NEXT_REQUESTABLE_CRAFTS.remove();
        if (exact != null && stack instanceof BigAERequestableStack) {
            return ((BigAERequestableStack) stack).setCountRequestableCraftsBig(exact);
        }
        return stack.setCountRequestableCrafts(value);
    }
}
