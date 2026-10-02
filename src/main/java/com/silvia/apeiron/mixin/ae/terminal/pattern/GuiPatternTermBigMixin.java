package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizon.gtnhlib.util.numberformatting.options.FormatOptions;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiPatternTerm;

/** Shows the exact blank-pattern count in the pattern terminal tooltip. */
@Mixin(value = GuiPatternTerm.class, remap = false)
public abstract class GuiPatternTermBigMixin {

    @Shadow
    @Final
    private IAEItemStack blankPatternView;

    @Redirect(
        method = "handleItemTooltip",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizon/gtnhlib/util/numberformatting/NumberFormatUtil;formatNumber(Ljava/lang/Number;Lcom/gtnewhorizon/gtnhlib/util/numberformatting/options/FormatOptions;)Ljava/lang/String;"))
    private String apeiron$formatBlankPattern(final Number ignored, final FormatOptions options) {
        final BigInteger exact = BigAEStackValues.get(this.blankPatternView);
        return BigNumberFormatter.formatExact(exact);
    }
}
