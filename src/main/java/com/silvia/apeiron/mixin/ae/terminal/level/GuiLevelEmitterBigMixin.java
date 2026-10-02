package com.silvia.apeiron.mixin.ae.terminal.level;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.silvia.apeiron.ae.automation.BigLevelEmitterContainerAccess;

import appeng.client.gui.implementations.GuiLevelEmitter;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerLevelEmitter;

/** Makes the ordinary level-emitter text field accept and send exact integers. */
@Mixin(value = GuiLevelEmitter.class, remap = false)
public abstract class GuiLevelEmitterBigMixin {

    @Shadow
    private MEGuiTextField amountTextField;

    @Shadow
    private ContainerLevelEmitter container;

    @org.spongepowered.asm.mixin.injection.Inject(method = { "initGui", "func_73866_w_" }, at = @At("TAIL"))
    private void apeiron$refreshExactField(final org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (this.container instanceof BigLevelEmitterContainerAccess access && this.amountTextField != null) {
            this.amountTextField.setText(
                access.getLevelBig()
                    .toString());
            this.amountTextField.setCursorPositionEnd();
        }
    }

    @Redirect(
        method = { "actionPerformed", "func_146284_a" },
        at = @org.spongepowered.asm.mixin.injection.At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/ContainerLevelEmitter;setLevel(J)V"))
    private void apeiron$sendExact(final ContainerLevelEmitter container, final long legacyValue) {
        final BigInteger value = apeiron$parse();
        if (value != null && container instanceof BigLevelEmitterContainerAccess access) {
            access.setLevelBig(value);
        } else {
            container.setLevel(legacyValue);
        }
    }

    private BigInteger apeiron$parse() {
        if (this.amountTextField == null || this.amountTextField.getText() == null) return null;
        final String text = this.amountTextField.getText()
            .trim()
            .replace(",", "");
        if (!text.matches("[0-9]+")) return null;
        try {
            return new BigInteger(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
