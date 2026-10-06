package com.silvia.apeiron.mixin.tst.energy;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.Nxer.TwistSpaceTechnology.common.api.random.RandomPackageFactory;
import com.Nxer.TwistSpaceTechnology.common.machine.TST_SuperWaterPurifier;

/** Exposes only the native random pool; processing uses the common generated-recipe transaction. */
@Mixin(value = TST_SuperWaterPurifier.class, remap = false)
public interface WaterPurifierRecipeAccessor {

    @Accessor("fluidRandomGetter")
    static RandomPackageFactory<FluidStack> apeiron$randomOutputs() {
        throw new AssertionError("Mixin accessor not applied");
    }
}
