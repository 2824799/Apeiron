package com.silvia.apeiron.mixin.ae.replenisher;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.automation.BigSuperMEReplenisher;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiSuperMEReplenisher;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerSuperMEReplenisher;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;

/** Displays Super ME Replenisher quantities and byte usage exactly. */
@Mixin(value = GuiSuperMEReplenisher.class, remap = false)
public abstract class GuiSuperMEReplenisherBigMixin {

    @Shadow
    private ContainerSuperMEReplenisher containerSuperMEReplenisher;
    @Shadow
    private MEGuiTextField tickRateField;
    @Shadow
    private MEGuiTextField thresholdField;

    @Overwrite
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.containerSuperMEReplenisher.needUpdate) {
            this.containerSuperMEReplenisher.needUpdate = false;
            this.tickRateField.setText(String.valueOf(this.containerSuperMEReplenisher.tickRate.get()));
            this.thresholdField.setText(String.valueOf(this.containerSuperMEReplenisher.threshold.get()));
        }

        final net.minecraft.client.gui.FontRenderer fontRendererObj = this.apeiron$fontRenderer();
        final int ySize = this.apeiron$ySize();
        final VirtualMESlot slot = this.apeiron$hoveredSlot();
        final int color = ColorUtils.guiTextColorGray.getColor();
        if (slot != null) {
            final IAEStack<?> config = slot.getAEStack();
            if (config != null) {
                final IAEStackType<?> type = config.getStackType();
                final IAEStack<?> stored = this.containerSuperMEReplenisher.storedData.get().list.findPrecise(config);
                final BigInteger configSize = BigAEStackValues.get(config);
                final BigInteger storedSize = stored == null ? BigInteger.ZERO : BigAEStackValues.get(stored);
                final BigInteger bytesUsed = storedSize.add(BigInteger.valueOf(type.getAmountPerByte() - 1))
                    .divide(BigInteger.valueOf(type.getAmountPerByte()));

                fontRendererObj.drawString(
                    GuiText.SuperMEReplenisherTarget.getLocal(BigNumberFormatter.formatExact(configSize)),
                    29,
                    65,
                    color);
                fontRendererObj.drawString(
                    GuiText.SuperMEReplenisherStored.getLocal(BigNumberFormatter.formatExact(storedSize)),
                    29,
                    75,
                    color);
                fontRendererObj.drawString(
                    GuiText.SuperMEReplenisherBytesUsed.getLocal(BigNumberFormatter.formatExact(bytesUsed)),
                    29,
                    85,
                    color);
            }
        }

        final BigInteger totalBytes = ((BigSuperMEReplenisher) this.containerSuperMEReplenisher).getTotalBytesBig();
        final boolean unlimited = totalBytes.compareTo(BigInteger.valueOf(Long.MAX_VALUE / 16)) >= 0;
        fontRendererObj.drawString(
            GuiText.SuperMEReplenisherBytesTotal.getLocal(
                unlimited ? GuiText.SuperMEReplenisherBytesUnlimited.getLocal()
                    : BigNumberFormatter.formatExact(totalBytes)),
            29,
            104,
            color);
        fontRendererObj.drawString(
            GuiText.SuperMEReplenisherBytesUsed.getLocal(
                BigNumberFormatter
                    .formatExact(((BigSuperMEReplenisher) this.containerSuperMEReplenisher).getUsedBytesBig())),
            29,
            114,
            color);
        fontRendererObj.drawString(GuiText.SuperMEReplenisherTickRate.getLocal(), 29, 124, color);
        fontRendererObj.drawString(GuiText.SuperMEReplenisherThreshold.getLocal(), 100, 124, color);
        fontRendererObj.drawString("%", 131, 136, color);
        fontRendererObj.drawString(GuiText.inventory.getLocal(), 29, ySize - 99, color);
    }

    private net.minecraft.client.gui.FontRenderer apeiron$fontRenderer() {
        try {
            return (net.minecraft.client.gui.FontRenderer) this.getClass()
                .getMethod("getFontRenderer")
                .invoke(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AE base GUI font renderer bridge unavailable", e);
        }
    }

    private int apeiron$ySize() {
        try {
            return ((Number) this.getClass()
                .getMethod("getYSize")
                .invoke(this)).intValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AE base GUI size bridge unavailable", e);
        }
    }

    private VirtualMESlot apeiron$hoveredSlot() {
        try {
            return (VirtualMESlot) this.getClass()
                .getMethod("getVirtualMESlotUnderMouse")
                .invoke(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AE virtual slot bridge unavailable", e);
        }
    }
}
