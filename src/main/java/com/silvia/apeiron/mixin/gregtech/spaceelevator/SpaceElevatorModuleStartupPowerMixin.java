package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;

/** Native elevator checks use the module's local buffer before the running tick; seed that view for direct power. */
@Mixin(
    targets = { "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModulePump",
        "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner",
        "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleManager" },
    remap = false)
public abstract class SpaceElevatorModuleStartupPowerMixin {

    @Unique
    private long apeiron$previousStoredEU;
    @Unique
    private boolean apeiron$seeded;

    @Inject(method = "checkProcessing_EM", at = @At("HEAD"), require = 1)
    private void apeiron$seedDirectPower(CallbackInfoReturnable<?> cir) {
        MTEMultiBlockBase module = (MTEMultiBlockBase) (Object) this;
        if (module instanceof TileEntityModuleBase && InfiniteEnergyHatches.find(module) != null) {
            apeiron$previousStoredEU = module.getEUVar();
            apeiron$seeded = true;
            module.setEUVar(Long.MAX_VALUE);
        }
    }

    @Inject(method = "checkProcessing_EM", at = @At("RETURN"), require = 1)
    private void apeiron$restoreLocalPower(CallbackInfoReturnable<?> cir) {
        if (!apeiron$seeded) return;
        ((MTEMultiBlockBase) (Object) this).setEUVar(apeiron$previousStoredEU);
        apeiron$seeded = false;
    }
}
