package com.silvia.apeiron.mixin.ae.terminal.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.terminal.BigAmountGui;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.client.gui.implementations.GuiAmount;
import appeng.client.gui.widgets.MEGuiTextField;

/** Allows AE amount dialogs to retain exact decimal input beyond long. */
@Mixin(value = GuiAmount.class, remap = false)
public abstract class GuiAmountMixin implements BigAmountGui {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    @Shadow
    protected MEGuiTextField amountTextField;

    @Unique
    private BigInteger apeiron$amountBig;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void apeiron$expandAmountField(final CallbackInfo ci) {
        if (this.amountTextField != null) this.amountTextField.setMaxStringLength(256);
    }

    @Inject(method = "getAmountLong", at = @At("RETURN"), cancellable = true)
    private void apeiron$readExactAmount(final CallbackInfoReturnable<Long> cir) {
        final BigInteger value = apeiron$parseAmount();
        this.apeiron$amountBig = value;
        if (value == null) return;
        if (value.compareTo(LONG_MAX) > 0) {
            cir.setReturnValue(Long.MAX_VALUE);
        } else if (value.compareTo(LONG_MIN) < 0) {
            cir.setReturnValue(Long.MIN_VALUE);
        } else {
            cir.setReturnValue(value.longValue());
        }
    }

    @Inject(method = "addAmount", at = @At("HEAD"), cancellable = true)
    private void apeiron$addExactAmount(final int amount, final CallbackInfo ci) {
        final BigInteger current = apeiron$parseAmount();
        if (current == null || current.compareTo(LONG_MAX) <= 0 && current.compareTo(LONG_MIN) >= 0) return;

        BigInteger result = current;
        if (current.equals(BigInteger.ONE) && amount > 1) result = BigInteger.ZERO;
        result = result.add(BigInteger.valueOf(amount));
        if (result.signum() < 1) result = BigInteger.ONE;
        this.amountTextField.setText(result.toString());
        this.amountTextField.setCursorPositionEnd();
        this.apeiron$amountBig = result;
        ci.cancel();
    }

    @Inject(method = "updateTextFieldTooltip", at = @At("HEAD"), cancellable = true)
    private void apeiron$exactTooltip(final CallbackInfo ci) {
        final BigInteger value = apeiron$parseAmount();
        if (value != null && !fitsLong(value) && value.signum() > 0) {
            this.amountTextField.setMessage("= " + BigNumberFormatter.formatExact(value));
            ci.cancel();
        }
    }

    @Override
    public BigInteger getAmountBig() {
        final BigInteger parsed = apeiron$parseAmount();
        return parsed == null ? this.apeiron$amountBig : parsed;
    }

    @Unique
    private BigInteger apeiron$parseAmount() {
        if (this.amountTextField == null || this.amountTextField.getText() == null) return null;
        String text = this.amountTextField.getText().trim().replace(",", "");
        if (text.isEmpty() || !text.matches("[0-9]+")) return null;
        try {
            return new BigInteger(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Unique
    private static boolean fitsLong(final BigInteger value) {
        return value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
    }
}
