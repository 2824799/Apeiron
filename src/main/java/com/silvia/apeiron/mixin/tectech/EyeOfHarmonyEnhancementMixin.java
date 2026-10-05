package com.silvia.apeiron.mixin.tectech;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputAssembly;
import com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyInputSupport;
import com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyRenderSupport;
import com.silvia.apeiron.common.machine.tectech.MTEEyeOfHarmonyEnhancementModule;
import com.silvia.apeiron.math.ChancedParallelCounts;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.structure.error.ErrorType;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.ParallelHelper;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import tectech.TecTech;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

/** Applies the configurable Apeiron module installed in an Eye of Harmony input-hatch position. */
@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyEnhancementMixin extends TTMultiblockBase {

    @Shadow(remap = false)
    private long parallelAmount;
    @Shadow(remap = false)
    private long astralArrayAmount;
    @Shadow(remap = false)
    private double successChance;
    @Shadow(remap = false)
    private Map<Fluid, Long> validFluidMap;
    @Shadow(remap = false)
    private EyeOfHarmonyRecipe currentRecipe;
    @Shadow(remap = false)
    private boolean recipeRunning;
    @Shadow(remap = false)
    private boolean animationsEnabled;
    @Unique
    private boolean apeiron$inPostTick;
    @Unique
    private boolean apeiron$checkedThisTick;
    @Unique
    private boolean apeiron$deferRenderRemoval;

    @Shadow(remap = false)
    private void createRenderBlock(EyeOfHarmonyRecipe recipe) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private void destroyRenderBlock() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private double recipeChanceCalculator() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private int recipeProcessTimeCalculator(long recipeTime, long recipeTier) {
        throw new AssertionError();
    }

    protected EyeOfHarmonyEnhancementMixin(int id, String name, String localName) {
        super(id, name, localName);
    }

    protected EyeOfHarmonyEnhancementMixin(String name) {
        super(name);
    }

    @Unique
    private MTEEyeOfHarmonyEnhancementModule apeiron$module() {
        for (MTEHatchInput hatch : mInputHatches) {
            if (hatch instanceof MTEEyeOfHarmonyEnhancementModule) return (MTEEyeOfHarmonyEnhancementModule) hatch;
        }
        return null;
    }

    @Unique
    private boolean apeiron$enabled() {
        return apeiron$module() != null;
    }

    @Inject(method = "drainFluidFromHatchesAndStoreInternally", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$chargeStoredFluids(CallbackInfo ci) {
        if (!apeiron$enabled()) return;
        EyeOfHarmonyInputSupport.drain(this, validFluidMap, astralArrayAmount, true);
        ci.cancel();
    }

    @ModifyConstant(method = "onPreTick", constant = @Constant(longValue = 20L), require = 1)
    private long apeiron$removeEnhancedDrainDelay(long nativeInterval) {
        return apeiron$enabled() ? 1L : nativeInterval;
    }

    @Inject(method = "checkProcessing_EM", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$fillAndStartRecipe(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!apeiron$enabled()) return;
        if (apeiron$inPostTick && apeiron$checkedThisTick) {
            cir.setReturnValue(checkRecipeResult);
            return;
        }
        if (apeiron$inPostTick) apeiron$checkedThisTick = true;
        ItemStack planet = getControllerSlot();
        if (planet == null) {
            cir.setReturnValue(SimpleCheckRecipeResult.ofFailure("no_planet_block"));
            return;
        }
        currentRecipe = TecTech.eyeOfHarmonyRecipeStorage.recipeLookUp(planet);
        if (currentRecipe == null) {
            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            return;
        }
        EyeOfHarmonyInputSupport.drain(this, validFluidMap, astralArrayAmount, true);
        CheckRecipeResult result = ((MTEEyeOfHarmony) (Object) this).processRecipe(currentRecipe);
        if (!result.wasSuccessful()) currentRecipe = null;
        cir.setReturnValue(result);
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide() || !apeiron$enabled()) {
            super.onPostTick(tile, tick);
            return;
        }
        apeiron$inPostTick = true;
        apeiron$checkedThisTick = false;
        try {
            // Use the native recipe bracket once before work, only after the structure is ready.
            if (!recipeRunning && mMaxProgresstime <= 0
                && mMachine
                && mStartUpCheck < 0
                && mUpdate != 1
                && !cyclicUpdate_EM()
                && !tile.hasWorkJustBeenEnabled()
                && getRepairStatus() >= 3
                && tile.isAllowedToWork()) {
                if (checkRecipe()) {
                    mEfficiency = Math.max(
                        0,
                        Math.min(
                            mEfficiency + mEfficiencyIncrease,
                            getMaxEfficiency(mInventory[1]) - (getIdealStatus() - getRepairStatus()) * 1000));
                } else afterRecipeCheckFailed();
                updateSlots();
            }
            super.onPostTick(tile, tick);
        } finally {
            apeiron$inPostTick = false;
            if (apeiron$deferRenderRemoval) {
                apeiron$deferRenderRemoval = false;
                if (!recipeRunning || !animationsEnabled) destroyRenderBlock();
            }
        }
    }

    @Inject(method = "outputAfterRecipe_EM", at = @At("RETURN"), require = 1)
    private void apeiron$allowNextRecipe(CallbackInfo ci) {
        apeiron$checkedThisTick = false;
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;createRenderBlock(Ltectech/recipe/EyeOfHarmonyRecipe;)V"),
        require = 1)
    private void apeiron$reuseRender(MTEEyeOfHarmony owner, EyeOfHarmonyRecipe recipe) {
        if (!apeiron$enabled() || !EyeOfHarmonyRenderSupport.canReuse(owner, recipe)) createRenderBlock(recipe);
    }

    @Redirect(
        method = "outputAfterRecipe_EM",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;destroyRenderBlock()V"),
        require = 1)
    private void apeiron$keepRenderBetweenRecipes(MTEEyeOfHarmony owner) {
        if (apeiron$enabled() && apeiron$inPostTick) apeiron$deferRenderRemoval = true;
        else destroyRenderBlock();
    }

    @Inject(method = "processRecipe", at = @At("HEAD"), require = 1)
    private void apeiron$prepareRecipe(EyeOfHarmonyRecipe recipe, CallbackInfoReturnable<CheckRecipeResult> cir) {
        MTEEyeOfHarmonyEnhancementModule module = apeiron$module();
        if (module != null) successChance = module.getSuccessChance();
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatchInputBus;getRealInventory()[Lnet/minecraft/item/ItemStack;"),
        require = 2)
    private ItemStack[] apeiron$readInputBus(MTEHatchInputBus bus) {
        if (!apeiron$enabled()) return bus.getRealInventory();
        ItemStack[] result = new ItemStack[bus.getSizeInventory()];
        for (int slot = 0; slot < result.length; slot++) result[slot] = bus.getStackInSlot(slot);
        return result;
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/ParallelHelper;calculateIntegralChancedOutputMultiplier(II)J"),
        require = 1)
    private long apeiron$exactSuccessfulParallel(int chance, int parallels) {
        MTEEyeOfHarmonyEnhancementModule module = apeiron$module();
        if (module == null) return ParallelHelper.calculateIntegralChancedOutputMultiplier(chance, parallels);
        return ChancedParallelCounts
            .calculate(parallelAmount, module.getSuccessChance(), XSTR.XSTR_INSTANCE::nextDouble);
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;recipeChanceCalculator()D"),
        require = 1)
    private double apeiron$overrideSuccessChance(MTEEyeOfHarmony owner) {
        MTEEyeOfHarmonyEnhancementModule module = ((EyeOfHarmonyEnhancementMixin) (Object) owner).apeiron$module();
        return module == null ? ((EyeOfHarmonyEnhancementMixin) (Object) owner).recipeChanceCalculator()
            : module.getSuccessChance();
    }

    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;recipeProcessTimeCalculator(JJ)I"),
        require = 1)
    private int apeiron$overrideDuration(MTEEyeOfHarmony owner, long recipeTime, long recipeTier) {
        MTEEyeOfHarmonyEnhancementModule module = ((EyeOfHarmonyEnhancementMixin) (Object) owner).apeiron$module();
        return module == null
            ? ((EyeOfHarmonyEnhancementMixin) (Object) owner).recipeProcessTimeCalculator(recipeTime, recipeTier)
            : module.getDuration();
    }

    @ModifyConstant(method = "processRecipe", constant = @Constant(longValue = 8637L), require = 1)
    private long apeiron$removeAstralArrayLimit(long original) {
        return apeiron$enabled() ? Long.MAX_VALUE : original;
    }

    @ModifyConstant(method = "getInfoData", constant = @Constant(longValue = 8637L), require = 1)
    private long apeiron$removeDisplayedAstralArrayLimit(long original) {
        return apeiron$enabled() ? Long.MAX_VALUE : original;
    }

    @Inject(method = "checkMachine", at = @At("RETURN"), require = 1)
    private void apeiron$removeSupportedInputErrors(IGregTechTileEntity tile, ItemStack stack,
        List<StructureError> errors, CallbackInfo ci) {
        if (!apeiron$enabled()) return;
        if (mInputBusses.size() != 1) return;
        if (!mEnergyHatches.isEmpty() || !mExoticEnergyHatches.isEmpty()) return;
        boolean supportedBus = mInputBusses
            .get(0) instanceof com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputBus;
        boolean supportedHatches = mInputHatches.stream()
            .allMatch(
                hatch -> !(hatch instanceof MTEHatchInputME)
                    || hatch instanceof com.silvia.apeiron.common.machine.me.stocking.MTEInfiniteStorageInputHatch);
        boolean supportedDual = !mDualInputHatches.isEmpty() && mDualInputHatches.stream()
            .allMatch(dual -> dual instanceof MTEInfiniteStorageInputAssembly);
        errors.removeIf(
            error -> (supportedBus
                && error.equals(StructureErrors.of("GT5U.gui.text.structure_error.stocking_input_bus_not_allowed")))
                || (supportedHatches && error
                    .equals(StructureErrors.of("GT5U.gui.text.structure_error.stocking_input_hatch_not_allowed")))
                || (supportedDual && error.equals(StructureErrors.of("GT5U.gui.text.structure_error.crib_not_allowed")))
                || (supportedDual && mInputHatches.size() == 1
                    && gregtech.api.enums.HatchElement.valueOf("InputHatch")
                        .count(this) == 2
                    && error.equals(
                        StructureErrors.hatchCount(
                            ErrorType.NOT_MATCH,
                            gregtech.api.enums.HatchElement.valueOf("InputHatch"),
                            1,
                            2))));
    }

    @Inject(
        method = "checkMachine",
        at = @At(
            value = "INVOKE",
            target = "Ltectech/thing/metaTileEntity/multi/MTEEyeOfHarmony;checkPiece(Ljava/lang/String;IIILjava/util/List;)Z",
            shift = At.Shift.AFTER),
        require = 1)
    private void apeiron$registerSupportedInputs(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors,
        CallbackInfo ci) {
        if (apeiron$enabled()) apeiron$registerStorageAssemblies();
    }

    @Unique
    private void apeiron$registerStorageAssemblies() {
        for (IDualInputHatch dual : mDualInputHatches) {
            if (!(dual instanceof MTEInfiniteStorageInputAssembly)) continue;
            MTEInfiniteStorageInputAssembly assembly = (MTEInfiniteStorageInputAssembly) dual;
            if (!mInputBusses.contains(assembly)) {
                assembly.mRecipeMap = getRecipeMap();
                mInputBusses.add(assembly);
            }
            MTEHatchInput fluidPort = assembly.getFluidInput();
            boolean addedFluidPort = false;
            if (!mInputHatches.contains(fluidPort)) {
                mInputHatches.add(fluidPort);
                addedFluidPort = true;
            }
            if (addedFluidPort && gregtech.api.enums.HatchElement.valueOf("InputHatch")
                .count(this) > 2) mInputHatches.remove(fluidPort);
        }
    }
}
