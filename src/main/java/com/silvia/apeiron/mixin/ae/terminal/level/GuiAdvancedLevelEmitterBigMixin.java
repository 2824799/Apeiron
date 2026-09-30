package com.silvia.apeiron.mixin.ae.terminal.level;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.automation.BigAdvancedLevelEmitterContainerAccess;

import appeng.client.gui.implementations.GuiAdvancedLevelEmitter;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerAdvancedLevelEmitter;

/** Makes all six advanced level-emitter fields accept and send exact integers. */
@Mixin(value = GuiAdvancedLevelEmitter.class, remap = false)
public abstract class GuiAdvancedLevelEmitterBigMixin {

    @Shadow
    private MEGuiTextField[] amountFields;

    @Redirect(
        method = "onAmountChanged",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/ContainerAdvancedLevelEmitter;setLevel(IJ)V"))
    private void apeiron$sendExact(final ContainerAdvancedLevelEmitter container, final int slot,
        final long legacyValue) {
        final BigInteger value = apeiron$parse(slot);
        if (value != null && container instanceof BigAdvancedLevelEmitterContainerAccess access) {
            access.setLevelBig(slot, value);
        } else {
            container.setLevel(slot, legacyValue);
        }
    }


    @Shadow
    private ContainerAdvancedLevelEmitter container;

    @org.spongepowered.asm.mixin.injection.Inject(method = "initGui", at = @At("TAIL"))
    private void apeiron$refreshExactFields(final org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!(this.container instanceof BigAdvancedLevelEmitterContainerAccess access) || this.amountFields == null) return;
        for (int slot = 0; slot < this.amountFields.length; slot++) {
            final MEGuiTextField field = this.amountFields[slot];
            if (field != null) {
                field.setText(access.getLevelBig(slot).toString());
                field.setCursorPositionEnd();
            }
        }
    }

    private BigInteger apeiron$parse(final int slot) {
        if (this.amountFields == null || slot < 0 || slot >= this.amountFields.length
                || this.amountFields[slot] == null || this.amountFields[slot].getText() == null) return null;
        final String text = this.amountFields[slot].getText().trim().replace(",", "");
        if (!text.matches("[0-9]+")) return null;
        try {
            return new BigInteger(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
