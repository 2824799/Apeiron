package com.silvia.apeiron.mixin.compat.omniocular;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@Pseudo
@Mixin(targets = "me.exz.omniocular.waila.TileEntityHandler", remap = false)
public abstract class WirelessTooltipMixin {

    @Inject(method = "getWailaBody", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$exactUpperRows(ItemStack item, List<String> lines, IWailaDataAccessor accessor,
        IWailaConfigHandler config, CallbackInfoReturnable<List<String>> cir) {
        cir.setReturnValue(WirelessWailaDisplay.updateOmni(cir.getReturnValue(), accessor.getNBTData()));
    }
}
