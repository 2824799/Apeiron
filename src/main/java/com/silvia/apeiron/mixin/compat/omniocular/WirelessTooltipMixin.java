package com.silvia.apeiron.mixin.compat.omniocular;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.energy.MachineWailaSnapshot;
import com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@Pseudo
@Mixin(targets = "me.exz.omniocular.waila.TileEntityHandler", remap = false)
public abstract class WirelessTooltipMixin {

    @Redirect(
        method = "getNBTData",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/tileentity/TileEntity;writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)V",
            remap = true),
        require = 1)
    private void apeiron$operatingSnapshot(TileEntity tile, NBTTagCompound tag, EntityPlayerMP player,
        TileEntity requestedTile, NBTTagCompound requestedTag, World world, int x, int y, int z) {
        if (!MachineWailaSnapshot.write(player, tile, tag)) tile.writeToNBT(tag);
    }

    @Inject(method = "getWailaBody", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$exactUpperRows(ItemStack item, List<String> lines, IWailaDataAccessor accessor,
        IWailaConfigHandler config, CallbackInfoReturnable<List<String>> cir) {
        cir.setReturnValue(WirelessWailaDisplay.updateOmni(cir.getReturnValue(), accessor.getNBTData()));
    }
}
