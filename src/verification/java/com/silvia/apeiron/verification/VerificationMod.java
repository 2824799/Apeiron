package com.silvia.apeiron.verification;

import com.silvia.apeiron.compat.CompatibilitySmoke;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/** Only installed into isolated verification profiles; never included in the released mod. */
@Mod(
    modid = "apeiron_verification",
    name = "Apeiron Verification",
    version = "1",
    dependencies = "required-after:apeiron")
public final class VerificationMod {

    @Mod.EventHandler
    public void verify(FMLPostInitializationEvent event) {
        if ("1".equals(System.getenv("APEIRON_VERIFY_MACHINE_BLOCKS_ONLY"))) {
            com.silvia.apeiron.common.machine.block.verification.MachineDropsSmoke.verify();
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide()
                .isClient()) com.silvia.apeiron.common.machine.block.verification.MachineBlockTooltipSmoke.verify();
            com.silvia.apeiron.Apeiron.LOG.info("Apeiron targeted machine block migration verification passed");
        } else if ("1".equals(System.getenv("APEIRON_VERIFY_INPUT_CIRCUIT_ONLY"))) {
            com.silvia.apeiron.common.machine.me.input.verification.InfinitePatternInputSmoke.verifyGlobalCircuit();
            com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.verifyGlobalCircuits();
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide()
                .isClient()) com.silvia.apeiron.common.machine.me.input.verification.InputCircuitGuiSmoke.verify();
            com.silvia.apeiron.Apeiron.LOG.info("Apeiron targeted input circuit verification passed");
        } else if ("1".equals(System.getenv("APEIRON_VERIFY_GODFORGE_ONLY"))) {
            com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.verify();
            com.silvia.apeiron.Apeiron.LOG.info("Apeiron targeted godforge verification passed");
        } else if ("1".equals(System.getenv("APEIRON_VERIFY_STOCKING_DISPLAY_ONLY"))) {
            com.silvia.apeiron.common.machine.me.stocking.verification.StockingNetworkCacheSmoke.verify();
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide()
                .isClient()) com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputGuiSmoke.verify();
            com.silvia.apeiron.Apeiron.LOG.info("Apeiron targeted stocking display verification passed");
        } else if ("1".equals(System.getenv("APEIRON_VERIFY_DSP_ONLY"))) {
            com.silvia.apeiron.common.machine.tst.verification.DysonSphereMachineSmoke.verify();
            com.silvia.apeiron.Apeiron.LOG.info("Apeiron targeted DSP verification passed");
        } else if ("1".equals(System.getenv("APEIRON_VERIFY_PATTERN_INPUT_ONLY"))) {
            com.silvia.apeiron.common.machine.me.input.verification.InfinitePatternInputSmoke.verify();
            com.silvia.apeiron.Apeiron.LOG.info("Apeiron targeted pattern input verification passed");
        } else CompatibilitySmoke.verify();
        if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
            .exitJava(0, false);
    }
}
