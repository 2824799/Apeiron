package com.silvia.apeiron.mixin.ae.terminal.core;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.silvia.apeiron.ae.crafting.packets.BigCraftPackets;
import com.silvia.apeiron.ae.terminal.BigAmountGui;

import appeng.client.gui.implementations.GuiCraftAmount;
import appeng.core.sync.AppEngPacket;

/** Adds the exact amount suffix to the normal AE crafting request. */
@Mixin(value = GuiCraftAmount.class, remap = false)
public abstract class GuiCraftAmountMixin {

    @ModifyArg(
        method = { "actionPerformed", "func_146284_a" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/core/sync/network/NetworkHandler;sendToServer(Lappeng/core/sync/AppEngPacket;)V"),
        index = 0,
        require = 1)
    private AppEngPacket apeiron$appendExactAmount(final AppEngPacket packet) {
        final BigInteger amount = ((BigAmountGui) (Object) this).getAmountBig();
        return BigCraftPackets.withExactAmount(packet, amount);
    }
}
