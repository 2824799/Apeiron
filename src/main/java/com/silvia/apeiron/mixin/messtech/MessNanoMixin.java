package com.silvia.apeiron.mixin.messtech;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.silvia.apeiron.common.integration.messtech.MessNanoController;
import com.silvia.apeiron.common.integration.messtech.MessNanoTasks;
import com.silvia.apeiron.common.integration.messtech.MessTechAccess;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;

@Pseudo
@Mixin(targets = "com.MessTech.common.machine.MTNanoScaleFoundry", remap = false)
public abstract class MessNanoMixin implements MessNanoController {

    @Unique
    private final MessNanoTasks apeiron$tasks = new MessNanoTasks();

    @Override
    public MessNanoTasks getMessNanoTasks() {
        return apeiron$tasks;
    }

    @Inject(
        method = "checkAndStartThread",
        at = @At(
            value = "INVOKE",
            target = "Lcom/MessTech/common/machine/MTNanoScaleFoundry;calculateOverclockedTask(Lcom/MessTech/common/machine/Base/TickableParallelismAcrossMultiMachineBase$WorkThread;Lgregtech/api/util/GTRecipe;Ljava/util/ArrayList;J)[I"),
        cancellable = true,
        require = 1)
    private void apeiron$start(IGregTechTileEntity base, @Coerce Object thread,
        CallbackInfoReturnable<CheckRecipeResult> cir, @Local GTRecipe recipe,
        @Local(ordinal = 1) ArrayList<ItemStack> items, @Local(ordinal = 2) ArrayList<FluidStack> fluids) {
        CheckRecipeResult result = apeiron$tasks
            .start((MTEMultiBlockBase) (Object) this, thread, recipe, items, fluids);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "checkProcessing", at = @At("HEAD"), require = 1)
    private void apeiron$scheduler(CallbackInfoReturnable<CheckRecipeResult> cir) {
        apeiron$tasks.schedulerTick();
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 1)
    private void apeiron$interval(CallbackInfoReturnable<CheckRecipeResult> cir) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) != null
            && ((Number) MessTechAccess.get(this, "machineMode")).intValue() == 0
            && cir.getReturnValue()
                .wasSuccessful())
            machine.mMaxProgresstime = ((java.util.Collection<?>) MessTechAccess.call(this, "getThreads")).stream()
                .anyMatch(thread -> (Boolean) MessTechAccess.call(thread, "isActive")) ? 1 : 20;
    }

    @Inject(method = "getThreadOutputText", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$products(int index, CallbackInfoReturnable<String> cir) {
        Object thread = MessTechAccess.call(this, "getThreadByIndex", index);
        if (thread == null) return;
        com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = apeiron$tasks.threadState(thread);
        if (state == null) return;
        StringBuilder text = new StringBuilder();
        for (appeng.api.storage.data.IAEStack<?> output : state.getOutputsUnsorted()) {
            if (text.length() > 0) text.append('\n');
            text.append("  ")
                .append(output.getDisplayName())
                .append(" x ")
                .append(
                    com.silvia.apeiron.math.BigNumberFormatter
                        .formatCompact(com.silvia.apeiron.ae.stack.BigAEStackValues.get(output)));
        }
        cir.setReturnValue(text.toString());
    }

    @Inject(method = "getWailaNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$threadDisplay(net.minecraft.entity.player.EntityPlayerMP player,
        net.minecraft.tileentity.TileEntity tile, NBTTagCompound tag, net.minecraft.world.World world, int x, int y,
        int z, CallbackInfo ci) {
        net.minecraft.nbt.NBTTagList rows = tag.getTagList("threads", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            Object thread = MessTechAccess.call(this, "getThread", row.getString("name"));
            com.silvia.apeiron.common.machine.parallel.WirelessRecipeState state = thread == null ? null
                : apeiron$tasks.threadState(thread);
            if (state == null) continue;
            row.setString(
                "ApeironParallelBig",
                com.silvia.apeiron.math.BigNumberFormatter.formatCompact(state.getParallelsBig()));
            row.setString(
                "ApeironEUtBig",
                com.silvia.apeiron.math.BigNumberFormatter.formatCompact(
                    state.remainingEnergy()
                        .signum() == 0 ? java.math.BigInteger.ZERO : state.nextDebit(10000)));
        }
    }

    @Inject(method = "getWailaBody", at = @At("RETURN"), require = 1)
    private void apeiron$threadTooltip(ItemStack item, java.util.List<String> lines,
        mcp.mobius.waila.api.IWailaDataAccessor accessor, mcp.mobius.waila.api.IWailaConfigHandler config,
        CallbackInfo ci) {
        net.minecraft.nbt.NBTTagList rows = accessor.getNBTData()
            .getTagList("threads", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            if (!row.hasKey("ApeironParallelBig")) continue;
            String parallelUnit = net.minecraft.util.StatCollector.translateToLocal("machine.nanoscale.unit.parallel");
            String powerUnit = net.minecraft.util.StatCollector.translateToLocal("machine.nanoscale.unit.eut");
            String old = row.getInteger("parallel") + parallelUnit
                + " "
                + net.minecraft.util.EnumChatFormatting.RED
                + row.getLong("eut")
                + powerUnit;
            String replacement = row.getString("ApeironParallelBig") + parallelUnit
                + " "
                + net.minecraft.util.EnumChatFormatting.RED
                + row.getString("ApeironEUtBig")
                + powerUnit;
            for (int line = 0; line < lines.size(); line++) lines.set(
                line,
                lines.get(line)
                    .replace(old, replacement));
        }
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$save(NBTTagCompound tag, CallbackInfo ci) {
        apeiron$tasks.save((MTEMultiBlockBase) (Object) this, tag);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 1)
    private void apeiron$load(NBTTagCompound tag, CallbackInfo ci) {
        apeiron$tasks.load((MTEMultiBlockBase) (Object) this, tag);
    }
}
