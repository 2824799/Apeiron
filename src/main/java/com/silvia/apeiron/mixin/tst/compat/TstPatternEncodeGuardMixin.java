package com.silvia.apeiron.mixin.tst.compat;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.slot.SlotRestrictedInput;

/** Stop an unsuccessful empty encoding before TST's RETURN callback dereferences the missing output. */
@Mixin(value = ContainerPatternTerm.class, remap = false, priority = 500)
public abstract class TstPatternEncodeGuardMixin {

    @Shadow
    private SlotRestrictedInput patternSlotOUT;

    // Applied after TST's default-priority RETURN injection. The cancellation return below is therefore
    // outside TST's callback, while successful encodings still follow the original conversion path.
    @Inject(
        method = "encode()V",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/parts/IPatternTerminal;encode(Lappeng/api/networking/energy/IEnergySource;Lappeng/api/storage/IMEMonitor;Lappeng/api/networking/security/BaseActionSource;Ljava/lang/String;Lnet/minecraft/world/World;)Z",
            shift = At.Shift.AFTER),
        cancellable = true,
        require = 1)
    private void apeiron$skipMissingEncodedOutput(CallbackInfo ci) {
        ItemStack output = patternSlotOUT.getStack();
        if (output == null || !output.hasTagCompound()) ci.cancel();
    }
}
