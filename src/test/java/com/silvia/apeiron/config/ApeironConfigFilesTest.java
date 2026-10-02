package com.silvia.apeiron.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.launchwrapper.Launch;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.silvia.apeiron.common.integration.tst.parallel.TstParallelMachine;
import com.silvia.apeiron.mixin.config.ApeironMixinConfigPlugin;

import cpw.mods.fml.relauncher.FMLInjectionData;

public class ApeironConfigFilesTest {

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
            ApeironConfig.loadFromDirectory(folder.newFolder());
        } finally {
            minecraftHome.set(null, previousHome);
        }
    }

    @Test
    public void earlyMixinBootstrapUsesTheGameDirectoryAndMigratesItsDisabledSetting() throws Exception {
        final File gameHome = folder.newFolder();
        final File configRoot = new File(gameHome, "config");
        Files.createDirectories(configRoot.toPath());
        Files.write(
            new File(configRoot, ApeironConfig.FILE_NAME).toPath(),
            "mixins {\n B:enableAeMixins=false\n}\n".getBytes(StandardCharsets.UTF_8));
        final File previousGameHome = Launch.minecraftHome;
        try {
            Launch.minecraftHome = gameHome;
            final ApeironMixinConfigPlugin plugin = new ApeironMixinConfigPlugin();
            plugin.onLoad("com.silvia.apeiron.mixin");
            assertFalse(ApeironConfig.areAeMixinsEnabled());
            assertTrue(new File(configRoot, "apeiron/apeiron.cfg").isFile());
            assertTrue(new File(configRoot, "apeiron/tst-unlimited-parallel.cfg").isFile());
            assertTrue(new File(configRoot, "apeiron/gtnl-unlimited-parallel.cfg").isFile());
        } finally {
            Launch.minecraftHome = previousGameHome;
        }
    }

    @Test
    public void migratesLegacyFileAndPreservesDisabledMixinsAndMachineIds() throws Exception {
        final File configRoot = folder.newFolder();
        final File oldFile = new File(configRoot, ApeironConfig.FILE_NAME);
        Files.write(
            oldFile.toPath(),
            ("mixins {\n B:enableAeMixins=false\n}\nmachines {\n I:machineIdStart=32000\n}\n")
                .getBytes(StandardCharsets.UTF_8));
        ApeironConfig.loadFromDirectory(configRoot);
        assertFalse(oldFile.exists());
        assertTrue(new File(configRoot, "apeiron/apeiron.cfg").isFile());
        assertTrue(new File(configRoot, "apeiron/tst-unlimited-parallel.cfg").isFile());
        assertTrue(new File(configRoot, "apeiron/gtnl-unlimited-parallel.cfg").isFile());
        assertFalse(ApeironConfig.areAeMixinsEnabled());
        assertEquals(32000, ApeironConfig.getMachineIdStart());
        ApeironConfig.loadFromDirectory(configRoot);
        assertFalse(ApeironConfig.areAeMixinsEnabled());
        assertEquals(32000, ApeironConfig.getMachineIdStart());
    }

    @Test
    public void existingNewConfigWinsWithoutOverwritingOrDeletingTheLegacyFile() throws Exception {
        final File configRoot = folder.newFolder();
        final File directory = new File(configRoot, "apeiron");
        Files.createDirectories(directory.toPath());
        final File oldFile = new File(configRoot, ApeironConfig.FILE_NAME);
        final byte[] legacy = "machines {\n I:machineIdStart=32000\n}\n".getBytes(StandardCharsets.UTF_8);
        Files.write(oldFile.toPath(), legacy);
        Files.write(
            new File(directory, ApeironConfig.FILE_NAME).toPath(),
            "machines {\n I:machineIdStart=31000\n}\n".getBytes(StandardCharsets.UTF_8));
        ApeironConfig.loadFromDirectory(configRoot);
        assertEquals(31000, ApeironConfig.getMachineIdStart());
        assertEquals(
            new String(legacy, StandardCharsets.UTF_8),
            new String(Files.readAllBytes(oldFile.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void selectionsNeedTheMasterSwitchAndAnIntMaxNativeMode() throws Exception {
        final File config = folder.newFile();
        final String machine = TstParallelMachine.MIRACLE_DOOR.getKey();
        Files.write(
            config.toPath(),
            ("general {\n B:enableUnlimitedParallel=false\n}\nmachines {\n B:" + machine + "=true\n}\n")
                .getBytes(StandardCharsets.UTF_8));
        TstUnlimitedParallelConfig.load(config);
        assertTrue(TstUnlimitedParallelConfig.isSelected(TstParallelMachine.MIRACLE_DOOR));
        assertFalse(TstUnlimitedParallelConfig.isRequested(TstParallelMachine.MIRACLE_DOOR, Integer.MAX_VALUE));
        Files.write(
            config.toPath(),
            ("general {\n B:enableUnlimitedParallel=true\n}\nmachines {\n B:" + machine + "=true\n}\n")
                .getBytes(StandardCharsets.UTF_8));
        TstUnlimitedParallelConfig.load(config);
        assertTrue(TstUnlimitedParallelConfig.isRequested(TstParallelMachine.MIRACLE_DOOR, Integer.MAX_VALUE));
        assertFalse(TstUnlimitedParallelConfig.isRequested(TstParallelMachine.MIRACLE_DOOR, 65536));
        assertFalse(TstUnlimitedParallelConfig.isRequested(TstParallelMachine.DEPLOYED_NANO_CORE, Integer.MAX_VALUE));
        TstUnlimitedParallelConfig.load(folder.newFile());
        assertFalse(TstUnlimitedParallelConfig.isEnabled());
        assertFalse(TstUnlimitedParallelConfig.isSelected(TstParallelMachine.MIRACLE_DOOR));
    }
}
