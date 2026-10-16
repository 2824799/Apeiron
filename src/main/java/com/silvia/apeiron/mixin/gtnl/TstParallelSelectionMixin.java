package com.silvia.apeiron.mixin.gtnl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replace only the incompatible TST hook; all other GTNL integrations keep their original selection. */
@Pseudo
@Mixin(targets = "com.science.gtnl.mixins.LateMixinLoader", remap = false)
public abstract class TstParallelSelectionMixin {

    @Inject(method = "getMixins", at = @At("RETURN"), cancellable = true, require = 1)
    private void apeiron$inheritedParallelState(Set<String> loadedMods, CallbackInfoReturnable<List<String>> cir) {
        final List<String> mixins = new ArrayList<>(cir.getReturnValue());
        mixins.remove("overpowered.MixinGTCM_ParallelHelper");
        cir.setReturnValue(mixins);
    }
}
