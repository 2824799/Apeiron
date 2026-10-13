package com.silvia.apeiron.mixin.messtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.integration.messtech.MessNaniteController;
import com.silvia.apeiron.common.integration.messtech.MessTechAccess;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;

@Pseudo
@Mixin(targets = "com.MessTech.common.machine.BosesCraftingArray", remap = false)
public abstract class MessBosesMixin implements MessNaniteController {

    @Override
    public boolean canAdvanceNanites() {
        Object[] required = (Object[]) MessTechAccess.get(this, "requiredNanites");
        Object provided = MessTechAccess.call(this, "getHighestNaniteTier");
        boolean ready = required == null || required.length == 0;
        if (!ready && provided != null && ((Number) MessTechAccess.call(this, "getAvailableNanites")).longValue() > 0) {
            ready = true;
            int tier = ((Number) MessTechAccess.get(provided, "tier")).intValue();
            for (Object need : required)
                if (((Number) MessTechAccess.get(need, "tier")).intValue() > tier) ready = false;
        }
        MessTechAccess.call(this, "setNaniteStalled", !ready);
        return ready;
    }

    @Override
    public int naniteDuration(GTRecipe recipe, int duration) {
        try {
            Object key = Class.forName("gregtech.api.util.GTRecipeConstants")
                .getField("NANITE_TIERS")
                .get(null);
            Object[] required = (Object[]) MessTechAccess.call(recipe, "getMetadata", key);
            Object provided = MessTechAccess.call(this, "getHighestNaniteTier");
            long count = ((Number) MessTechAccess.call(this, "getAvailableNanites")).longValue();
            if (required == null || required.length == 0 || provided == null || count <= 0) return duration;
            int tier = ((Number) MessTechAccess.get(provided, "tier")).intValue();
            long total = 0;
            for (int i = 0; i < required.length; i++) {
                int need = ((Number) MessTechAccess.get(required[i], "tier")).intValue();
                if (need > tier) return duration;
                int length = duration / required.length + (i < duration % required.length ? 1 : 0);
                // Native BEC divisor: each nanite supplies half a work tick, reduced by tier distance.
                long work = (long) length * 2 * (1L << Math.min(30, Math.abs(need - tier)));
                total += work / count + (work % count == 0 ? 0 : 1);
            }
            return (int) Math.max(1, Math.min(Integer.MAX_VALUE, total));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Inject(method = "incrementProgressTime", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$time(CallbackInfo ci) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (InfiniteEnergyHatches.find(machine) == null || !((BigWirelessController) this).getWirelessRecipeState()
            .isRunning()) return;
        if (canAdvanceNanites()) machine.mProgresstime = Math.min(machine.mMaxProgresstime, machine.mProgresstime + 1);
        ci.cancel();
    }
}
