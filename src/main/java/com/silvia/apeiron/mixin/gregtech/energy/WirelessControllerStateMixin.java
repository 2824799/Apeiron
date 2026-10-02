package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.BigNumberFormatter;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.shutdown.ShutDownReason;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class WirelessControllerStateMixin implements BigWirelessController {

    @Unique
    private final WirelessRecipeState apeiron$wireless = new WirelessRecipeState();

    @Override
    public WirelessRecipeState getWirelessRecipeState() {
        return apeiron$wireless;
    }

    @Override
    public ParallelLimit getParallelLimitBig() {
        return InfiniteEnergyHatches.find(apeiron$machine()) == null
            ? ParallelLimit.bounded(apeiron$machine().getTrueParallel())
            : apeiron$wireless.getLimit();
    }

    @Override
    public BigInteger getCurrentParallelsBig() {
        return apeiron$wireless.getParallelsBig();
    }

    @Override
    public BigInteger getRecipeEUtBig() {
        return apeiron$wireless.getEUtBig();
    }

    @Override
    public BigInteger getRecipeTotalEUBig() {
        return apeiron$wireless.getTotalEUBig();
    }

    @Unique
    private MTEMultiBlockBase apeiron$machine() {
        return (MTEMultiBlockBase) (Object) this;
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$save(NBTTagCompound tag, CallbackInfo ci) {
        tag.setTag("ApeironWirelessRecipe", apeiron$wireless.save());
        com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay
            .write(tag, apeiron$wireless, apeiron$machine().mEfficiency);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$load(NBTTagCompound tag, CallbackInfo ci) {
        apeiron$wireless.load(tag.getCompoundTag("ApeironWirelessRecipe"));
    }

    @Inject(method = "outputAfterRecipe", at = @At("HEAD"), require = 1)
    private void apeiron$complete(CallbackInfo ci) {
        apeiron$wireless.complete();
        apeiron$flush();
    }

    @Inject(method = "onPostTick", at = @At("RETURN"), require = 1)
    private void apeiron$retry(IGregTechTileEntity tile, long tick, CallbackInfo ci) {
        if (tile.isServerSide() && tick % 20 == 0) apeiron$flush();
    }

    @Unique
    private void apeiron$flush() {
        MTEMultiBlockBase machine = apeiron$machine();
        if (!apeiron$wireless.pending()
            .isEmpty() && apeiron$wireless.pending()
                .flush(
                    machine.getOutputBusses(),
                    machine.getOutputHatches(),
                    machine.protectsExcessItem(),
                    machine.protectsExcessFluid()))
            machine.markDirty();
    }

    @Inject(method = "stopMachine(Lgregtech/api/util/shutdown/ShutDownReason;)V", at = @At("HEAD"), require = 1)
    private void apeiron$cancel(ShutDownReason reason, CallbackInfo ci) {
        apeiron$wireless.cancelRecipe();
    }

    @Inject(method = "getWailaNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$waila(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z, CallbackInfo ci) {
        if (InfiniteEnergyHatches.find(apeiron$machine()) == null && !apeiron$wireless.isRunning()) return;
        tag.setString(
            "ApeironParallelSetting",
            apeiron$wireless.getParallelSettingBig()
                .signum() == 0 ? "∞" : BigNumberFormatter.formatCompact(apeiron$wireless.getParallelSettingBig()));
        tag.setString("ApeironRunningParallels", BigNumberFormatter.formatCompact(getCurrentParallelsBig()));
        tag.setString("ApeironWirelessEUt", BigNumberFormatter.formatCompact(getRecipeEUtBig()));
        com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay
            .write(tag, apeiron$wireless, apeiron$machine().mEfficiency);
    }

    @Inject(method = "getWailaBody", at = @At("RETURN"), require = 1)
    private void apeiron$tooltip(ItemStack stack, java.util.List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config, CallbackInfo ci) {
        NBTTagCompound tag = accessor.getNBTData();
        com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay.updateNative(tip, tag);
        if (!tag.hasKey("ApeironParallelSetting")) return;
        tip.add(
            net.minecraft.util.StatCollector.translateToLocalFormatted(
                "apeiron.machine.energy.parallel_status",
                tag.getString("ApeironParallelSetting"),
                tag.getString("ApeironRunningParallels")));
        tip.add("EU/t: " + tag.getString("ApeironWirelessEUt"));
    }
}
