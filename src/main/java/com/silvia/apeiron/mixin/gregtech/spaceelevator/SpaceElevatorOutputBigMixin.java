package com.silvia.apeiron.mixin.gregtech.spaceelevator;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.compat.OutputTransactions;
import com.silvia.apeiron.mixin.gregtech.output.MultiBlockProcessingAccessor;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

/** Merges GT5U elevator module chunks before an exact ME output transaction. */
@Mixin(value = TTMultiblockBase.class, remap = false)
public abstract class SpaceElevatorOutputBigMixin {

    @Inject(method = "addClassicOutputs_EM", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$mergeChunks(CallbackInfo ci) {
        if (!((Object) this instanceof TileEntityModuleBase)) return;
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        MultiBlockProcessingAccessor outputs = (MultiBlockProcessingAccessor) this;
        if (!apeiron$allOutputsHaveExactTargets(machine)) return;

        BigMachineOutputQueue queue = new BigMachineOutputQueue();
        if (outputs.apeiron$getOutputItems() != null)
            for (ItemStack output : outputs.apeiron$getOutputItems()) if (output != null && output.stackSize > 0)
                queue.addItem(output, java.math.BigInteger.valueOf(output.stackSize));
        if (outputs.apeiron$getOutputFluids() != null)
            for (FluidStack output : outputs.apeiron$getOutputFluids()) if (output != null && output.amount > 0)
                queue.addFluid(output, java.math.BigInteger.valueOf(output.amount));
        if (queue.isEmpty()) return;
        if (!BigRecipeOutputCapacity.fits(machine, queue.snapshotOutputs())) return;
        queue.flush(
            machine.getOutputBusses(),
            OutputTransactions.hatches(machine),
            machine.protectsExcessItem(),
            machine.protectsExcessFluid());
        outputs.apeiron$setOutputItems(null);
        outputs.apeiron$setOutputFluids(null);
        ci.cancel();
    }

    private boolean apeiron$allOutputsHaveExactTargets(MTEMultiBlockBase machine) {
        MultiBlockProcessingAccessor outputs = (MultiBlockProcessingAccessor) this;
        boolean itemOutput = false;
        if (outputs.apeiron$getOutputItems() != null) for (ItemStack output : outputs.apeiron$getOutputItems())
            if (output != null && output.stackSize > 0) itemOutput = true;
        if (itemOutput) {
            boolean exact = false;
            for (gregtech.api.interfaces.IOutputBus bus : machine.getOutputBusses()) {
                if (OutputTransactions.items(bus)
                    .hasExactItems()) exact = true;
                else return false;
            }
            if (!exact) return false;
        }

        boolean fluidOutput = false;
        if (outputs.apeiron$getOutputFluids() != null) for (FluidStack output : outputs.apeiron$getOutputFluids())
            if (output != null && output.amount > 0) fluidOutput = true;
        if (fluidOutput) {
            boolean exact = false;
            for (Object hatch : OutputTransactions.hatches(machine)) {
                if (OutputTransactions.fluids(hatch)
                    .hasExactFluids()) exact = true;
                else return false;
            }
            if (!exact) return false;
        }
        return itemOutput || fluidOutput;
    }
}
