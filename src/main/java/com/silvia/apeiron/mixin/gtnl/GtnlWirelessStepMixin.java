package com.silvia.apeiron.mixin.gtnl;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase;
import com.silvia.apeiron.api.machine.gtnl.BigGtnlRecipeLogic;
import com.silvia.apeiron.common.integration.gtnl.parallel.BigGtnlParallelHelper;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.misc.WirelessNetworkManager;

@Pseudo
@Mixin(
    targets = { "com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase",
        "com.science.gtnl.common.machine.multiblock.wireless.HighwayToHell",
        "com.science.gtnl.common.machine.multiblock.wireless.TransliminalOasis",
        "com.science.gtnl.common.machine.multiblock.wireless.NineIndustrialMultiMachine" },
    remap = false)
public abstract class GtnlWirelessStepMixin {

    @Unique
    private BigGtnlParallelHelper apeiron$plan() {
        ProcessingLogic logic = ((MultiBlockProcessingAccessor) this).apeiron$getProcessingLogic();
        return logic instanceof BigGtnlRecipeLogic ? ((BigGtnlRecipeLogic) logic).getGtnlPreparedRecipe() : null;
    }

    @Redirect(
        method = "wirelessModeProcessOnce",
        at = @At(value = "INVOKE", target = "Ljava/math/BigInteger;valueOf(J)Ljava/math/BigInteger;", ordinal = 0),
        require = 1)
    private BigInteger apeiron$exactEUt(long legacy) {
        BigGtnlParallelHelper plan = apeiron$plan();
        return plan == null ? BigInteger.valueOf(legacy) : plan.getEutBig();
    }

    @Redirect(
        method = "wirelessModeProcessOnce",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/misc/WirelessNetworkManager;addEUToGlobalEnergyMap(Ljava/util/UUID;Ljava/math/BigInteger;)Z"),
        require = 1)
    private boolean apeiron$commit(UUID owner, BigInteger debit) {
        BigGtnlParallelHelper plan = apeiron$plan();
        return plan == null ? WirelessNetworkManager.addEUToGlobalEnergyMap(owner, debit) : plan.commitWirelessStep();
    }

    @Redirect(
        method = "wirelessModeProcessOnce",
        at = @At(value = "INVOKE", target = "Lgregtech/api/logic/ProcessingLogic;getCurrentParallels()I"),
        require = 0)
    private int apeiron$nativeBudget(ProcessingLogic logic) {
        return InfiniteEnergyHatches.isUltimate((MTEMultiBlockBase) (Object) this) && apeiron$plan() != null ? 0
            : logic.getCurrentParallels();
    }

    @Inject(method = "wirelessModeProcessOnce", at = @At("RETURN"), require = 1)
    private void apeiron$closeFailure(ItemStack stack, CallbackInfoReturnable<CheckRecipeResult> cir) {
        WirelessEnergyMultiMachineBase<?> machine = (WirelessEnergyMultiMachineBase<?>) (Object) this;
        if (!cir.getReturnValue()
            .wasSuccessful() && apeiron$plan() != null && machine.isRecipeProcessing) machine.endRecipeProcessing();
    }
}
