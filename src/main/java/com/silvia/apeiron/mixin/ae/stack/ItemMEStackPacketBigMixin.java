package com.silvia.apeiron.mixin.ae.stack;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.storage.data.IAEStack;
import appeng.items.misc.ItemMEStackPacket;

/** Shows the exact amount stored in an AE stack packet. */
@Mixin(value = ItemMEStackPacket.class, remap = false)
public abstract class ItemMEStackPacketBigMixin {

    @Inject(method = "addCheckedInformation", at = @At("TAIL"))
    private void apeiron$exactPacketAmount(final ItemStack stack, final EntityPlayer player, final List<String> lines,
        final boolean displayMoreInfo, final CallbackInfo ci) {
        final IAEStack<?> value = ItemMEStackPacket.toAEStack(stack);
        if (value == null) return;
        final java.math.BigInteger amount = BigAEStackValues.get(value);
        if (BigAEStackValues.fitsLong(amount)) return;
        if (!lines.isEmpty()) lines.remove(lines.size() - 1);
        lines.add(value.getDisplayName() + ": " + BigNumberFormatter.formatExact(amount));
    }
}
