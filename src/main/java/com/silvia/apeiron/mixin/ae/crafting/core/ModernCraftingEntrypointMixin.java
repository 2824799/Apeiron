package com.silvia.apeiron.mixin.ae.crafting.core;

import java.util.concurrent.Future;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.crafting.BigCraftingJobFast;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class ModernCraftingEntrypointMixin {

    @Inject(
        method = "beginCraftingJob(Lnet/minecraft/world/World;Lappeng/api/networking/IGrid;Lappeng/api/networking/security/BaseActionSource;Lappeng/api/storage/data/IAEStack;Lappeng/api/config/CraftingMode;ZLappeng/api/networking/crafting/ICraftingCallback;)Ljava/util/concurrent/Future;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$beginExactJob(World world, IGrid grid, BaseActionSource source, IAEStack<?> stack,
        CraftingMode mode, boolean lite, ICraftingCallback callback, CallbackInfoReturnable<Future<ICraftingJob>> cir) {
        if (stack == null || !BigAEStackValues.isBig(stack)) return;
        BigCraftingJobFast<?> job = new BigCraftingJobFast(world, grid, source, (IAEStack) stack, mode, callback);
        cir.setReturnValue((Future) job.schedule());
    }
}
