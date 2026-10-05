package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import java.math.BigInteger;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.parallel.BigSpaceElevatorModule;
import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.spaceelevator.ModuleParallelParameter;

import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModulePump;
import tectech.thing.metaTileEntity.multi.base.parameter.CompositeParameter;
import tectech.thing.metaTileEntity.multi.base.parameter.IntegerParameter;
import tectech.thing.metaTileEntity.multi.base.parameter.Parameter;

@Mixin(value = TileEntityModulePump.class, remap = false)
public abstract class SpaceElevatorPumpParallelMixin implements BigSpaceElevatorModule {

    @Shadow(remap = false)
    private IntegerParameter[] parallelParameters;
    @Shadow(remap = false)
    private CompositeParameter[] recipeParameters;

    @Inject(method = "initParameters", at = @At("RETURN"), require = 1)
    private void apeiron$wrapParallel(CallbackInfo ci) {
        TileEntityModulePump module = (TileEntityModulePump) (Object) this;
        for (int i = 0; i < parallelParameters.length; i++) {
            parallelParameters[i] = new ModuleParallelParameter(module, parallelParameters[i]);
            List<Parameter<?, ?>> parameters = recipeParameters[i].getValue();
            parameters.set(2, parallelParameters[i]);
        }
    }

    @Override
    public ParallelLimit getModuleParallelLimitBig() {
        BigInteger total = BigInteger.ZERO;
        for (IntegerParameter parameter : parallelParameters)
            total = total.add(((ModuleParallelParameter) parameter).getBig());
        return ParallelLimit.bounded(total);
    }
}
