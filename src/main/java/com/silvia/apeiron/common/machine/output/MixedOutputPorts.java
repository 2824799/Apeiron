package com.silvia.apeiron.common.machine.output;

import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Registers both views of one physical assembly, including when an addon overrides the GT scanner. */
public final class MixedOutputPorts {

    private MixedOutputPorts() {}

    public static boolean register(MTEMultiBlockBase machine, IMetaTileEntity meta, int texture) {
        if (!(meta instanceof MTEInfiniteMEOutputAssembly)) return false;
        MTEInfiniteMEOutputAssembly assembly = (MTEInfiniteMEOutputAssembly) meta;
        assembly.updateTexture(texture);
        assembly.updateCraftingIcon(machine.getMachineCraftingIcon());
        if (!machine.mOutputBusses.contains(assembly)) machine.mOutputBusses.add(assembly);
        if (!machine.mOutputHatches.contains(assembly.getFluidOutput()))
            machine.mOutputHatches.add(assembly.getFluidOutput());
        return true;
    }
}
