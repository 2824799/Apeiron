package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;

@Mixin(value = TileEntityModuleBase.class, remap = false)
public abstract class SpaceElevatorModuleEnergyMixin implements BigSpaceElevatorModule {

    @Shadow(remap = false)
    TileEntitySpaceElevator parent;

    @Shadow(remap = false)
    protected boolean isConnected;

    @Override
    public MTEMultiBlockBase getEnergyParent() {
        return isConnected ? parent : null;
    }

    @Override
    public ParallelLimit getModuleParallelLimitBig() {
        return ParallelLimit.bounded(((MTEMultiBlockBase) (Object) this).getTrueParallel());
    }

    @Inject(method = "drainEnergyInput(JJ)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$drainParent(long voltage, long amps, CallbackInfoReturnable<Boolean> cir) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find((MTEMultiBlockBase) (Object) this);
        if (hatch != null) cir.setReturnValue(
            hatch.consumeEUBig(
                BigInteger.valueOf(voltage)
                    .multiply(BigInteger.valueOf(amps))
                    .abs()));
    }

    @Redirect(
        method = "onPostTick",
        at = @At(value = "INVOKE", target = "Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;getStoredEU()J"),
        require = 1)
    private long apeiron$parentPowered(IGregTechTileEntity tile) {
        return InfiniteEnergyHatches.find((MTEMultiBlockBase) (Object) this) == null ? tile.getStoredEU() : 1L;
    }
}
