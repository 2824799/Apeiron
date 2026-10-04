package com.silvia.apeiron;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.silvia.apeiron.ae.smoke.AEInventorySmoke;
import com.silvia.apeiron.ae.smoke.AEItemStackSmoke;
import com.silvia.apeiron.common.lifecycle.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = Apeiron.MODID,
    name = Apeiron.NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:appliedenergistics2;required-after:gregtech;after:tectech;after:sciencenotleisure;after:programmablehatches;after:TwistSpaceTechnology;after:aeinfinitycell;after:appeu")
public final class Apeiron {

    public static final String MODID = "apeiron";
    public static final String NAME = "Apeiron";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.silvia.apeiron.client.ClientProxy",
        serverSide = "com.silvia.apeiron.common.lifecycle.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        if ("1".equals(System.getenv("APEIRON_VERIFY_COMPATIBILITY"))) {
            com.silvia.apeiron.compat.CompatibilitySmoke.verify();
            if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
                .exitJava(0, false);
        }
        if ("1".equals(System.getenv("APEIRON_VERIFY_TREE"))) {
            com.silvia.apeiron.ae.smoke.AEProductionEntrypointSmoke.verify();
            com.silvia.apeiron.ae.smoke.AECraftingTreeSmoke.verify();
            com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke.verify();
            if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
                .exitJava(0, false);
        }
        if ("1".equals(System.getenv("APEIRON_VERIFY_WAILA"))) {
            com.silvia.apeiron.common.integration.waila.verification.WailaSnapshotSmoke.verify();
            if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
                .exitJava(0, false);
        }
        if ("1".equals(System.getenv("APEIRON_VERIFY_PARALLEL"))) {
            com.silvia.apeiron.common.machine.energy.verification.NativeParallelPanelSmoke.verify();
            if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
                .exitJava(0, false);
        }
        if ("1".equals(System.getenv("APEIRON_VERIFY_ENERGY"))) {
            com.silvia.apeiron.common.machine.energy.verification.WirelessPowerPanelSmoke.verify();
            com.silvia.apeiron.common.machine.energy.verification.InfiniteEnergySmoke.verify();
            com.silvia.apeiron.common.machine.energy.verification.WirelessMachineIntegrationSmoke.verify();
            if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
                .exitJava(0, false);
        }
        if ("1".equals(System.getenv("APEIRON_VERIFY_STOCKING"))) {
            com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.verify();
            if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
                .exitJava(0, false);
        }
        if ("1".equals(System.getenv("APEIRON_VERIFY_STACK"))) {
            com.silvia.apeiron.ae.smoke.AEProductionEntrypointSmoke.verify();
            com.silvia.apeiron.ae.smoke.AEMixinTargetSmoke.verify();
            AEItemStackSmoke.verify();
            AEInventorySmoke.verify();
            com.silvia.apeiron.ae.smoke.InfiniteCapabilitiesSmoke.verify();
            com.silvia.apeiron.ae.smoke.AESecurityInventorySmoke.verify();
            com.silvia.apeiron.ae.smoke.AECraftingCPUSmoke.verify();
            com.silvia.apeiron.ae.smoke.AECraftingPlanningSmoke.verify();
            com.silvia.apeiron.ae.smoke.AECraftingTreeSmoke.verify();
            com.silvia.apeiron.ae.smoke.AEBackendSmoke.verify();
            com.silvia.apeiron.common.machine.me.output.verification.BoundlessMEOutputSmoke.verify();
            com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke.verify();
            com.silvia.apeiron.common.machine.me.input.verification.InfinitePatternInputSmoke.verify();
            com.silvia.apeiron.common.machine.energy.verification.InfiniteEnergySmoke.verify();
            com.silvia.apeiron.common.machine.tectech.verification.EyeOfHarmonyOutputSmoke.verify();
            if (cpw.mods.fml.common.Loader.isModLoaded("TwistSpaceTechnology")) {
                com.silvia.apeiron.common.machine.tst.verification.TstOutputSmoke.verify();
            } else {
                LOG.info("TST not installed; optional output integration skipped");
            }
            if (cpw.mods.fml.common.Loader.isModLoaded("aeinfinitycell")) {
                com.silvia.apeiron.common.integration.aeinfinitycell.verification.InfinityCellSmoke.verify();
            } else {
                LOG.info("AE2 Infinity Cell not installed; optional storage integration skipped");
            }
            if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) {
                cpw.mods.fml.common.FMLCommonHandler.instance()
                    .exitJava(0, false);
            }
        }
    }
}
