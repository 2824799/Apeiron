package com.silvia.apeiron.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraftforge.common.config.Configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.silvia.apeiron.mixin.config.ApeironMixinConfigPlugin;

import cpw.mods.fml.relauncher.FMLInjectionData;

public class MachineIdConfigTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Field minecraftHome;
    private Object previousHome;

    @Before
    public void initializeForgeConfigRoot() throws Exception {
        minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        previousHome = minecraftHome.get(null);
        minecraftHome.set(null, folder.getRoot());
    }

    @After
    public void resetConfiguration() throws Exception {
        try {
            ApeironConfig.load(folder.newFile());
        } finally {
            minecraftHome.set(null, previousHome);
        }
    }

    @Test
    public void reservesOneHundredIdsFromTheConfiguredStart() throws Exception {
        ApeironConfig.load(folder.newFile());
        assertEquals(31300, ApeironConfig.getMachineIdStart());
        assertEquals(31399, ApeironConfig.getMachineIdEnd());
        loadStart(32666);
        assertEquals(32666, ApeironConfig.getMachineId(0));
        assertEquals(32765, ApeironConfig.getMachineId(99));
        assertThrows(IllegalArgumentException.class, () -> ApeironConfig.getMachineId(100));
        assertThrows(IllegalArgumentException.class, () -> ApeironConfig.getMachineId(-1));
    }

    @Test
    public void rejectsRangesThatCannotFitOrOverlapReservedCoreIds() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> loadStart(2048));
        assertThrows(IllegalArgumentException.class, () -> loadStart(32667));
        loadStart(2049);
        assertEquals(2148, ApeironConfig.getMachineIdEnd());
    }

    private void loadStart(final int start) throws Exception {
        final File config = folder.newFile();
        Files.write(
            config.toPath(),
            ("machines {\n I:machineIdStart=" + start + "\n}\n").getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
    }

    @Test
    public void migratesLegacyAeSettingWithoutEnablingADisabledIntegration() throws Exception {
        final File config = folder.newFile();
        Files.write(config.toPath(), "mixins {\n B:enableAeMixins=false\n}\n".getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
        assertFalse(ApeironConfig.areAeMixinsEnabled());
        assertTrue(ApeironConfig.isEyeOfHarmonyBigOutputEnabled());
        final Configuration saved = new Configuration(config);
        assertFalse(saved.hasKey("mixins", "enableAeMixins"));
        assertTrue(saved.hasKey("mixins.appliedenergistics2", "enableAeMixins"));
        assertTrue(saved.hasKey("mixins.tectech", "enableEyeOfHarmonyBigOutput"));
        final ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
        assertFalse(
            plugin.shouldApplyMixin(
                "tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony",
                "com.silvia.apeiron.mixin.tectech.EyeOfHarmonyBigOutputMixin"));
    }

    @Test
    public void modGroupsHaveIndependentSwitchesAndNewValuesOverrideLegacyValues() throws Exception {
        final File config = folder.newFile();
        Files.write(
            config.toPath(),
            ("mixins {\n B:enableAeMixins=false\n appliedenergistics2 {\n B:enableAeMixins=true\n }\n"
                + " tectech {\n B:enableEyeOfHarmonyBigOutput=false\n }\n}\n").getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
        assertTrue(ApeironConfig.areAeMixinsEnabled());
        assertFalse(ApeironConfig.isEyeOfHarmonyBigOutputEnabled());
        final ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
        assertTrue(
            plugin.shouldApplyMixin(
                "appeng.util.item.AEItemStack",
                "com.silvia.apeiron.mixin.ae.stack.AEItemStackMixin"));
        assertFalse(
            plugin.shouldApplyMixin(
                "tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony",
                "com.silvia.apeiron.mixin.tectech.EyeOfHarmonyBigOutputMixin"));
    }

    @Test
    public void movesEyeSettingToTecTechWhilePreservingOtherGregTechOptions() throws Exception {
        final File config = folder.newFile();
        Files.write(
            config.toPath(),
            ("mixins {\n gregtech {\n B:enableEyeOfHarmonyBigOutput=false\n" + " B:otherGregTechOption=true\n }\n}\n")
                .getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
        assertFalse(ApeironConfig.isEyeOfHarmonyBigOutputEnabled());
        final Configuration saved = new Configuration(config);
        assertTrue(saved.hasKey("mixins.tectech", "enableEyeOfHarmonyBigOutput"));
        assertFalse(saved.hasKey("mixins.gregtech", "enableEyeOfHarmonyBigOutput"));
        assertTrue(
            saved.get("mixins.gregtech", "otherGregTechOption", false)
                .getBoolean(false));
    }

    @Test
    public void tstSwitchIsIndependentAndUsesTheProvidingModCategory() throws Exception {
        final File config = folder.newFile();
        Files.write(
            config.toPath(),
            "mixins {\n twistspacetechnology {\n B:enableTstBigOutput=false\n }\n}\n".getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
        assertFalse(ApeironConfig.isTstBigOutputEnabled());
        assertTrue(ApeironConfig.areAeMixinsEnabled());
        assertTrue(ApeironConfig.isEyeOfHarmonyBigOutputEnabled());
        final ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
        assertFalse(
            plugin.shouldApplyMixin(
                "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase",
                "com.silvia.apeiron.mixin.tst.output.MultiMachineOutputBigMixin"));
        assertEquals(31302, ApeironConfig.getMachineId(2));
    }

    @Test
    public void infinityCellSwitchIsIndependentAndRequiresAeMixins() throws Exception {
        final File config = folder.newFile();
        Files.write(
            config.toPath(),
            "mixins {\n aeinfinitycell {\n B:enableInfinityCellBigStorage=false\n }\n}\n"
                .getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
        assertFalse(ApeironConfig.isInfinityCellBigStorageEnabled());
        assertTrue(ApeironConfig.areAeMixinsEnabled());
        assertTrue(ApeironConfig.isTstBigOutputEnabled());
        final ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
        assertFalse(
            plugin.shouldApplyMixin(
                "cn.dancingsnow.aeinfinitycell.storage.CellCount",
                "com.silvia.apeiron.mixin.aeinfinitycell.storage.CellCountBigMixin"));
        Files.write(
            config.toPath(),
            "mixins {\n appliedenergistics2 {\n B:enableAeMixins=false\n }\n}\n".getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
        assertTrue(ApeironConfig.isInfinityCellBigStorageEnabled());
        assertFalse(
            plugin.shouldApplyMixin(
                "cn.dancingsnow.aeinfinitycell.storage.CellCount",
                "com.silvia.apeiron.mixin.aeinfinitycell.storage.CellCountBigMixin"));
    }

    @Test
    public void disablingCoreAlsoDisablesPreviouslyUngatedGtInputsAndOutputs() throws Exception {
        final File config = folder.newFile();
        Files.write(
            config.toPath(),
            "mixins {\n appliedenergistics2 {\n B:enableAeMixins=false\n }\n}\n".getBytes(StandardCharsets.UTF_8));
        ApeironConfig.load(config);
        ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
        for (String name : new String[] { "gregtech.input.BigDualInputProcessingMixin",
            "gregtech.input.BigInputRegistrationMixin", "gregtech.output.MixedOutputRegistrationMixin",
            "gregtech.output.PendingBigOutputTickMixin" })
            assertFalse(
                plugin.shouldApplyMixin(
                    "gregtech.api.metatileentity.implementations.MTEMultiBlockBase",
                    "com.silvia.apeiron.mixin." + name));
    }

    @Test
    public void everyRegisteredMixinHasAnExplicitFeatureOwner() throws Exception {
        try (java.io.Reader reader = new java.io.InputStreamReader(
            getClass().getClassLoader()
                .getResourceAsStream("mixins.apeiron.json"),
            StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject config = new com.google.gson.JsonParser().parse(reader)
                .getAsJsonObject();
            assertEquals(
                1,
                config.getAsJsonObject("injectors")
                    .get("defaultRequire")
                    .getAsInt());
            for (String section : new String[] { "mixins", "client" })
                for (com.google.gson.JsonElement entry : config.getAsJsonArray(section))
                    org.junit.Assert.assertNotNull(MixinFeature.of(entry.getAsString()));
        }
        assertThrows(IllegalArgumentException.class, () -> MixinFeature.of("unregistered.SomeMixin"));
    }

    @Test
    public void optionalIntegrationSwitchesAreIndependent() throws Exception {
        for (MixinFeature selected : MixinFeature.values()) {
            if (selected.key == null) continue;
            File file = folder.newFile();
            Configuration config = new Configuration(file);
            config.get(selected.category, selected.key, true)
                .set(false);
            config.save();
            ApeironConfig.load(file);
            assertFalse(selected.name(), selected.isEnabled());
            assertTrue(MixinFeature.CORE.isEnabled());
            for (MixinFeature other : MixinFeature.values())
                if (other.key != null && other != selected) assertTrue(other.name(), other.isEnabled());
        }
    }

}
