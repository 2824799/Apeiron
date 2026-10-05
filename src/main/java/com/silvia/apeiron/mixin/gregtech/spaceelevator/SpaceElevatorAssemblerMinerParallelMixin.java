package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.spaceelevator.ModuleParallelParameter;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import tectech.thing.metaTileEntity.multi.base.parameter.IntegerParameter;

@Mixin(
    targets = { "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleAssembler",
        "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner" },
    remap = false)
public abstract class SpaceElevatorAssemblerMinerParallelMixin implements BigSpaceElevatorModule {

    @Shadow(remap = false)
    private IntegerParameter parallelParameter;

    @Inject(method = "initParameters", at = @At("RETURN"), require = 1)
    private void apeiron$wrapParallel(CallbackInfo ci) {
        parallelParameter = new ModuleParallelParameter((MTEMultiBlockBase) (Object) this, parallelParameter);
    }

    @Override
    public ParallelLimit getModuleParallelLimitBig() {
        return ParallelLimit.bounded(((ModuleParallelParameter) parallelParameter).getBig());
    }
}
