package com.silvia.apeiron.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.silvia.apeiron.common.integration.gtnl.parallel.GtnlParallelMachine;
import com.silvia.apeiron.common.integration.tst.parallel.TstParallelMachine;

import cpw.mods.fml.relauncher.FMLInjectionData;

public class GtnlUnlimitedParallelConfigTest {

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
    public void generatesIndependentDisabledSelectionsWithoutGtnlClasses() throws Exception {
        final File configRoot = folder.newFolder();
        ApeironConfig.loadFromDirectory(configRoot);
        final File config = new File(configRoot, "apeiron/" + GtnlUnlimitedParallelConfig.FILE_NAME);
        assertTrue(config.isFile());
        final String text = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("sciencenotleisure"));
        assertTrue(text.contains("B:enableUnlimitedParallel=false"));
        for (final GtnlParallelMachine machine : GtnlParallelMachine.values()) {
            assertTrue(text.contains("B:" + machine.getKey() + "=false"));
            assertFalse(GtnlUnlimitedParallelConfig.isSelected(machine));
        }
        assertFalse(GtnlUnlimitedParallelConfig.isEnabled());
    }

    @Test
    public void masterSwitchModeAndReloadAllControlRequests() throws Exception {
        final File config = folder.newFile();
        final GtnlParallelMachine machine = GtnlParallelMachine.LAPOTRON_CHIP;
        writeSelection(config, false, machine.getKey());
        GtnlUnlimitedParallelConfig.load(config);
        assertTrue(GtnlUnlimitedParallelConfig.isSelected(machine));
        assertFalse(GtnlUnlimitedParallelConfig.isRequested(machine, Integer.MAX_VALUE));
        writeSelection(config, true, machine.getKey());
        GtnlUnlimitedParallelConfig.load(config);
        assertTrue(GtnlUnlimitedParallelConfig.isRequested(machine, Integer.MAX_VALUE));
        assertFalse(GtnlUnlimitedParallelConfig.isRequested(machine, 65536));
        assertFalse(
            GtnlUnlimitedParallelConfig
                .isRequested(GtnlParallelMachine.INDUSTRIAL_ARCANE_ASSEMBLER, Integer.MAX_VALUE));
        GtnlUnlimitedParallelConfig.load(folder.newFile());
        assertFalse(GtnlUnlimitedParallelConfig.isEnabled());
        assertFalse(GtnlUnlimitedParallelConfig.isSelected(machine));
    }

    @Test
    public void assemblerMatrixRequiresTheOriginalLongFieldNotItsIntProjection() throws Exception {
        final File config = folder.newFile();
        writeSelection(config, true, GtnlParallelMachine.ASSEMBLER_MATRIX.getKey());
        GtnlUnlimitedParallelConfig.load(config);
        assertTrue(
            GtnlUnlimitedParallelConfig
                .isRequested(GtnlParallelMachine.ASSEMBLER_MATRIX, BigInteger.valueOf(Long.MAX_VALUE)));
        assertFalse(GtnlUnlimitedParallelConfig.isRequested(GtnlParallelMachine.ASSEMBLER_MATRIX, Integer.MAX_VALUE));
        assertFalse(
            GtnlUnlimitedParallelConfig.isRequested(
                GtnlParallelMachine.ASSEMBLER_MATRIX,
                BigInteger.valueOf(Integer.MAX_VALUE)
                    .multiply(BigInteger.TEN)));
    }

    @Test
    public void enablingAndReloadingEitherModNeverChangesTheOtherMod() throws Exception {
        final File configRoot = folder.newFolder();
        final File directory = ApeironConfigFiles.prepareDirectory(configRoot);
        writeSelection(
            new File(directory, GtnlUnlimitedParallelConfig.FILE_NAME),
            true,
            GtnlParallelMachine.LAPOTRON_CHIP.getKey());
        writeSelection(
            new File(directory, TstUnlimitedParallelConfig.FILE_NAME),
            false,
            TstParallelMachine.MIRACLE_DOOR.getKey());
        ApeironConfig.loadFromDirectory(configRoot);
        assertTrue(GtnlUnlimitedParallelConfig.isRequested(GtnlParallelMachine.LAPOTRON_CHIP, Integer.MAX_VALUE));
        assertFalse(TstUnlimitedParallelConfig.isRequested(TstParallelMachine.MIRACLE_DOOR, Integer.MAX_VALUE));
        writeSelection(
            new File(directory, TstUnlimitedParallelConfig.FILE_NAME),
            true,
            TstParallelMachine.MIRACLE_DOOR.getKey());
        TstUnlimitedParallelConfig.load(new File(directory, TstUnlimitedParallelConfig.FILE_NAME));
        GtnlUnlimitedParallelConfig.load(folder.newFile());
        assertTrue(TstUnlimitedParallelConfig.isRequested(TstParallelMachine.MIRACLE_DOOR, Integer.MAX_VALUE));
        assertFalse(GtnlUnlimitedParallelConfig.isEnabled());
        ApeironConfig.loadFromDirectory(configRoot);
        assertTrue(GtnlUnlimitedParallelConfig.isRequested(GtnlParallelMachine.LAPOTRON_CHIP, Integer.MAX_VALUE));
        assertTrue(TstUnlimitedParallelConfig.isRequested(TstParallelMachine.MIRACLE_DOOR, Integer.MAX_VALUE));
    }

    private static void writeSelection(final File file, final boolean enabled, final String machine) throws Exception {
        Files.write(
            file.toPath(),
            ("general {\n B:enableUnlimitedParallel=" + enabled + "\n}\nmachines {\n B:" + machine + "=true\n}\n")
                .getBytes(StandardCharsets.UTF_8));
    }
}
