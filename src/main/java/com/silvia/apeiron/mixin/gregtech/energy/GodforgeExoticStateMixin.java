package com.silvia.apeiron.mixin.gregtech.energy;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.api.machine.parallel.BigGodforgeExoticModule;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;

import gregtech.api.util.GTRecipe;
import tectech.thing.metaTileEntity.multi.godforge.MTEExoticModule;

@Mixin(value = MTEExoticModule.class, remap = false)
public abstract class GodforgeExoticStateMixin implements BigGodforgeExoticModule {

    @Shadow
    private long actualParallel;
    @Shadow
    private boolean recipeInProgress;
    @Shadow
    private boolean recipeRegenerated;
    @Shadow
    private GTRecipe plasmaRecipe;

    @Shadow
    private void setPlasmaRecipe(GTRecipe recipe) {
        throw new AssertionError();
    }

    @Override
    public BigInteger getExoticRecipeMultiplier() {
        // Waiting recipes from older saves can still contain the main controller's
        // multiplier. Hatch parallel is absolute even when executing one of those recipes.
        if (InfiniteEnergyHatches.find((MTEExoticModule) (Object) this) != null) return BigInteger.ONE;
        return BigInteger.valueOf(Math.max(1, actualParallel));
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), require = 1)
    private void apeiron$saveZeroInputRecipe(NBTTagCompound tag, CallbackInfo ci) {
        // Native persistence only stores input plasmas. A generated zero-input recipe
        // needs its output and cost saved while waiting for energy or output space.
        tag.removeTag("apeironExoticWaitingRecipe");
        if (!recipeInProgress || plasmaRecipe == null
            || plasmaRecipe.mInputs.length != 0
            || plasmaRecipe.mFluidInputs.length != 0
            || plasmaRecipe.mOutputs.length != 0
            || plasmaRecipe.mFluidOutputs.length != 1
            || plasmaRecipe.mFluidOutputs[0] == null) return;
        NBTTagCompound recipe = new NBTTagCompound();
        recipe.setTag("output", plasmaRecipe.mFluidOutputs[0].writeToNBT(new NBTTagCompound()));
        recipe.setInteger("duration", plasmaRecipe.mDuration);
        recipe.setInteger("eut", plasmaRecipe.mEUt);
        recipe.setInteger("special", plasmaRecipe.mSpecialValue);
        tag.setTag("apeironExoticWaitingRecipe", recipe);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), require = 1)
    private void apeiron$loadZeroInputRecipe(NBTTagCompound tag, CallbackInfo ci) {
        if (!recipeInProgress || plasmaRecipe != null) return;
        if (tag.hasKey("apeironExoticWaitingRecipe", 10)) {
            NBTTagCompound recipe = tag.getCompoundTag("apeironExoticWaitingRecipe");
            FluidStack output = FluidStack.loadFluidStackFromNBT(recipe.getCompoundTag("output"));
            if (output != null && recipe.getInteger("duration") > 0) {
                setPlasmaRecipe(
                    new GTRecipe(
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new FluidStack[0],
                        new FluidStack[] { output },
                        recipe.getInteger("duration"),
                        recipe.getInteger("eut"),
                        recipe.getInteger("special")));
                return;
            }
        }
        // Older saves can retain the waiting flag without any serialized recipe.
        // Let the current recipe generator (including other mods' configuration) recover it.
        recipeInProgress = false;
        recipeRegenerated = false;
    }
}
