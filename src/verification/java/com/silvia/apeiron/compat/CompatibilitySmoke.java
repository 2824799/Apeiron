package com.silvia.apeiron.compat;

import com.silvia.apeiron.Apeiron;

/** Exercises the transformed core integrations under each historical dependency combination. */
public final class CompatibilitySmoke {

    private CompatibilitySmoke() {}

    public static void verify() {
        if (cpw.mods.fml.common.Loader.isModLoaded("sciencenotleisure"))
            com.silvia.apeiron.common.integration.gtnl.parallel.GtnlIntegrationSmoke.verify();
        if (DependencyCapabilities.hasMethod("gtnhlanth.common.tileentity.MTETargetChamber", "getMaskItemStack", null))
            com.silvia.apeiron.common.integration.lanthanides.TargetChamberInputSmoke.verify();
        // Older GT beam crafters bypass ProcessingLogic; their particle-item Mixins are deliberately disabled.
        if (DependencyCapabilities.hasMethod(
            "gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter$1",
            "createParallelHelper",
            null)
            && DependencyCapabilities.hasMethod(
                "gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter$1",
                "applyRecipe",
                null))
            com.silvia.apeiron.common.integration.lanthanides.BeamCrafterParticleSmoke.verify();
        com.silvia.apeiron.ae.smoke.AEProductionEntrypointSmoke.verify();
        com.silvia.apeiron.ae.smoke.AEMixinTargetSmoke.verify();
        com.silvia.apeiron.ae.smoke.AEItemStackSmoke.verify();
        com.silvia.apeiron.ae.smoke.AEInventorySmoke.verify();
        com.silvia.apeiron.ae.smoke.InfiniteCapabilitiesSmoke.verify();
        com.silvia.apeiron.ae.smoke.AESecurityInventorySmoke.verify();
        com.silvia.apeiron.ae.smoke.AECraftingCPUSmoke.verify();
        com.silvia.apeiron.ae.smoke.AECraftingPlanningSmoke.verify();
        com.silvia.apeiron.ae.smoke.AESelfRecursiveCraftingSmoke.verify();
        com.silvia.apeiron.ae.smoke.AECraftingTreeSmoke.verify();
        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) com.silvia.apeiron.ae.smoke.AECraftingConfirmationSortSmoke.verify();
        com.silvia.apeiron.ae.smoke.AEBackendSmoke.verify();
        if (cpw.mods.fml.common.Loader.isModLoaded("aeinfinitycell"))
            com.silvia.apeiron.common.integration.aeinfinitycell.verification.InfinityCellSmoke.verify();
        com.silvia.apeiron.common.machine.me.output.verification.BoundlessMEOutputSmoke.verify();
        com.silvia.apeiron.common.machine.me.output.verification.MEBeamlineOutputSmoke.verify();
        com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke.verify();
        OutputCompatibilitySmoke.verify();
        if (cpw.mods.fml.common.Loader.isModLoaded("TwistSpaceTechnology")) {
            com.silvia.apeiron.common.machine.tst.verification.TstOutputSmoke.verify();
            com.silvia.apeiron.common.machine.tst.verification.TstGeneratedEnergySmoke.verify();
        }
        com.silvia.apeiron.common.machine.me.input.verification.InfinitePatternInputSmoke.verify();
        com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputsSmoke.verify();
        com.silvia.apeiron.common.machine.energy.verification.InfiniteEnergySmoke.verify();
        com.silvia.apeiron.common.machine.energy.verification.PcbFactoryEnergySmoke.verify();
        com.silvia.apeiron.common.machine.quantum.verification.QuantumEnhancementSmoke.verify();
        com.silvia.apeiron.common.machine.tectech.verification.EyeOfHarmonyEnhancementSmoke.verify();
        if (DependencyCapabilities.hasClass("gregtech.api.interfaces.IOutputHatch"))
            com.silvia.apeiron.common.machine.tectech.verification.EyeOfHarmonyOutputSmoke.verify();
        if (DependencyCapabilities.hasClass("me.exz.omniocular.waila.TileEntityHandler")) {
            com.silvia.apeiron.common.integration.waila.verification.WailaSnapshotSmoke.verify();
        } else {
            com.silvia.apeiron.common.machine.tectech.verification.EyeOfHarmonyWailaSmoke.verify();
            com.silvia.apeiron.common.integration.waila.verification.MachineWailaCompatibilitySmoke.verify();
        }
        com.silvia.apeiron.common.machine.spaceelevator.verification.SpaceElevatorIntegrationSmoke.verify();
        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) com.silvia.apeiron.common.machine.energy.verification.WirelessPowerPanelSmoke.verify();
        if (DependencyCapabilities.hasClass("tectech.thing.metaTileEntity.multi.godforge.MTESmeltingModule")) {
            com.silvia.apeiron.common.machine.energy.verification.WirelessMachineIntegrationSmoke.verify();
            com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.verify();
        }
        if (DependencyCapabilities
            .hasClass("gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEMassFabricator"))
            com.silvia.apeiron.common.machine.energy.verification.MassFabricatorEnergySmoke.verify();
        Apeiron.LOG
            .info("Apeiron compatibility verification passed: {}", System.getenv("APEIRON_COMPATIBILITY_PROFILE"));
    }
}
