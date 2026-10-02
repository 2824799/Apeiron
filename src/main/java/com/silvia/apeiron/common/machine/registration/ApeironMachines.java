package com.silvia.apeiron.common.machine.registration;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.machine.block.ApeironMachineBlock;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.config.ApeironConfig;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** Stable offsets within Apeiron's configurable reservation of 100 GregTech machine IDs. */
public final class ApeironMachines {

    public static final int ITEM_OUTPUT_BUS_OFFSET = 0;
    public static final int FLUID_OUTPUT_HATCH_OFFSET = 1;
    public static final int MIXED_OUTPUT_ASSEMBLY_OFFSET = 2;
    public static final int INFINITE_ENERGY_HATCH_OFFSET = 3;
    public static MTEInfiniteEnergyHatch infiniteEnergyHatch;
    public static final int PATTERN_INPUT_ASSEMBLY_OFFSET = 4;
    public static final int PATTERN_INPUT_MIRROR_OFFSET = 5;
    public static MTEInfinitePatternInputAssembly patternInputAssembly;
    public static MTEInfinitePatternInputMirror patternInputMirror;
    public static ApeironMachineBlock block;
    public static MTEBoundlessMEOutputBus itemOutputBus;
    public static MTEBoundlessMEOutputHatch fluidOutputHatch;
    public static MTEInfiniteMEOutputAssembly mixedOutputAssembly;
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs("apeiron") {

        @Override
        public Item getTabIconItem() {
            return block == null ? Items.ender_pearl : Item.getItemFromBlock(block);
        }

        @Override
        public ItemStack getIconItemStack() {
            return itemOutputBus == null ? new ItemStack(Items.ender_pearl) : itemOutputBus.getStackForm(1L);
        }
    };

    private ApeironMachines() {}

    public static void registerBlock() {
        if (block == null) {
            block = new ApeironMachineBlock();
            GameRegistry.registerTileEntity(ApeironMachineTile.class, "apeiron.machine");
        }
    }

    public static void registerMachines() {
        if (itemOutputBus != null) return;
        validateReservation();
        itemOutputBus = new MTEBoundlessMEOutputBus(
            ApeironConfig.getMachineId(ITEM_OUTPUT_BUS_OFFSET),
            "apeiron.boundless_me_output_bus",
            "Infinite ME Output Bus");
        fluidOutputHatch = new MTEBoundlessMEOutputHatch(
            ApeironConfig.getMachineId(FLUID_OUTPUT_HATCH_OFFSET),
            "apeiron.boundless_me_output_hatch",
            "Infinite ME Output Hatch");
        mixedOutputAssembly = new MTEInfiniteMEOutputAssembly(
            ApeironConfig.getMachineId(MIXED_OUTPUT_ASSEMBLY_OFFSET),
            "apeiron.infinite_me_output_assembly",
            "Infinite ME Output Assembly");
        infiniteEnergyHatch = new MTEInfiniteEnergyHatch(
            ApeironConfig.getMachineId(INFINITE_ENERGY_HATCH_OFFSET),
            "apeiron.infinite_energy_hatch",
            "Infinite Energy Hatch");
        patternInputAssembly = new MTEInfinitePatternInputAssembly(
            ApeironConfig.getMachineId(PATTERN_INPUT_ASSEMBLY_OFFSET),
            "apeiron.infinite_pattern_input",
            "Infinite Pattern Input Assembly");
        patternInputMirror = new MTEInfinitePatternInputMirror(
            ApeironConfig.getMachineId(PATTERN_INPUT_MIRROR_OFFSET),
            "apeiron.infinite_pattern_input_mirror",
            "Infinite Pattern Input Assembly Mirror");
        Apeiron.LOG.info(
            "Apeiron machine ID reservation: {}..{} ({} IDs); ME outputs: {}, {}, {}",
            ApeironConfig.getMachineIdStart(),
            ApeironConfig.getMachineIdEnd(),
            ApeironConfig.MACHINE_ID_COUNT,
            ApeironConfig.getMachineId(ITEM_OUTPUT_BUS_OFFSET),
            ApeironConfig.getMachineId(FLUID_OUTPUT_HATCH_OFFSET),
            ApeironConfig.getMachineId(MIXED_OUTPUT_ASSEMBLY_OFFSET));
    }

    private static void validateReservation() {
        validateReservation(false);
    }

    public static void validateRegisteredReservation() {
        validateReservation(true);
    }

    private static void validateReservation(final boolean registered) {
        if (ApeironConfig.getMachineIdEnd() >= GregTechAPI.METATILEENTITIES.length) {
            throw new IllegalStateException("Apeiron machine ID reservation exceeds GregTech's registry");
        }
        for (int id = ApeironConfig.getMachineIdStart(); id <= ApeironConfig.getMachineIdEnd(); id++) {
            final IMetaTileEntity existing = GregTechAPI.METATILEENTITIES[id];
            if (registered && (existing == itemOutputBus || existing == fluidOutputHatch
                || existing == mixedOutputAssembly
                || existing == infiniteEnergyHatch
                || existing == patternInputAssembly
                || existing == patternInputMirror)) continue;
            if (existing != null) {
                throw new IllegalStateException(
                    "Apeiron's configured 100-ID reservation conflicts at ID " + id
                        + " with "
                        + existing.getClass()
                            .getName()
                        + ". Choose an unused machines.machineIdStart in config/apeiron/apeiron.cfg.");
            }
        }
    }
}
