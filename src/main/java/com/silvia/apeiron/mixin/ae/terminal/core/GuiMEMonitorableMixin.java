package com.silvia.apeiron.mixin.ae.terminal.core;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.core.localization.ButtonToolTips;

/** Replace AE's saturated long tooltip quantity with the exact item count. */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class GuiMEMonitorableMixin {

    private VirtualMESlot apeiron$getHoveredSlot() {
        try {
            Method method = this.getClass()
                .getMethod("getVirtualMESlotUnderMouse");
            return (VirtualMESlot) method.invoke(this);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Inject(method = "handleItemTooltip", at = @At("RETURN"), remap = false)
    private void apeiron$replaceExactCount(ItemStack stack, int mouseX, int mouseY, List<String> currentToolTip,
        CallbackInfoReturnable<List<String>> cir) {
        VirtualMESlot hoveredSlot = apeiron$getHoveredSlot();
        if (hoveredSlot == null) return;
        boolean monitorable = hoveredSlot instanceof VirtualMEMonitorableSlot;
        boolean pattern = hoveredSlot instanceof VirtualMEPatternSlot;
        if (!monitorable && !pattern) return;

        IAEStack<?> aeStack = hoveredSlot.getAEStack();
        if (!(aeStack instanceof BigAEStack)) return;
        BigAEStack exact = (BigAEStack) aeStack;
        if (!exact.isStackSizeBig()) return;

        BigInteger count = exact.getStackSizeBig();
        if (count.signum() <= 0) return;

        String local = monitorable ? ButtonToolTips.ItemsStored.getLocal() : ButtonToolTips.ItemCount.getLocal();
        String legacyAmount = NumberFormat.getNumberInstance(Locale.US)
            .format(aeStack.getStackSize());
        String legacyLine = EnumChatFormatting.GRAY + String.format(local, legacyAmount);
        currentToolTip.remove(legacyLine);

        String exactLine = EnumChatFormatting.GRAY + String.format(local, BigNumberFormatter.formatExact(count));
        if (!currentToolTip.contains(exactLine)) currentToolTip.add(exactLine);
    }
}
