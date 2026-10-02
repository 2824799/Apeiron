package com.silvia.apeiron.common.integration.gtnl.parallel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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

import com.silvia.apeiron.api.machine.parallel.ParallelLimit;
import com.silvia.apeiron.common.machine.parallel.BigParallelPlan;
import com.silvia.apeiron.config.GtnlUnlimitedParallelConfig;

import cpw.mods.fml.relauncher.FMLInjectionData;

public class GtnlParallelPolicyTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Field minecraftHome;
    private Object previousHome;
    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    @Before
    public void enableSelectedAdapters() throws Exception {
        minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        previousHome = minecraftHome.get(null);
        minecraftHome.set(null, folder.getRoot());
        final File config = folder.newFile();
        Files.write(
            config.toPath(),
            ("general {\n B:enableUnlimitedParallel=true\n}\nmachines {\n B:LapotronChip=true\n B:AssemblerMatrix=true\n}\n")
                .getBytes(StandardCharsets.UTF_8));
        GtnlUnlimitedParallelConfig.load(config);
    }

    @After
    public void resetConfiguration() throws Exception {
        try {
            GtnlUnlimitedParallelConfig.load(folder.newFile());
        } finally {
            minecraftHome.set(null, previousHome);
        }
    }

    @Test
    public void selectedUnlimitedModeCalculatesExactLargeInputsOutputsAndEnergy() {
        assertTrue(limit(GtnlParallelMachine.LAPOTRON_CHIP, INT_MAX, true, BigInteger.ONE).isUnlimited());
        final BigInteger available = HUGE.multiply(BigInteger.valueOf(32));
        final BigParallelPlan plan = GtnlParallelPolicy.calculate(
            GtnlParallelMachine.LAPOTRON_CHIP,
            INT_MAX,
            true,
            BigInteger.ONE,
            HUGE,
            ParallelLimit.unlimited(),
            available,
            BigInteger.valueOf(32),
            200);
        assertEquals(HUGE, plan.getParallelsBig());
        assertEquals(available, plan.getEUtBig());
        assertEquals(available.multiply(BigInteger.valueOf(200)), plan.getTotalEUBig());
        assertEquals(HUGE.multiply(BigInteger.valueOf(64)), plan.scaleAmountBig(BigInteger.valueOf(64)));
        assertEquals(Integer.MAX_VALUE, plan.getParallels());
        assertEquals(Long.MAX_VALUE, plan.getEUt());
    }

    @Test
    public void manualPanelCapsAndRemainingEnergyStillLimitAnUnlimitedMachine() {
        final ParallelLimit manual = limit(GtnlParallelMachine.LAPOTRON_CHIP, INT_MAX, false, BigInteger.valueOf(8));
        assertFalse(manual.isUnlimited());
        assertEquals(BigInteger.valueOf(8), manual.applyTo(HUGE));
        final BigParallelPlan plan = GtnlParallelPolicy.calculate(
            GtnlParallelMachine.LAPOTRON_CHIP,
            INT_MAX,
            false,
            BigInteger.valueOf(8),
            HUGE,
            ParallelLimit.bounded(7),
            BigInteger.valueOf(95),
            BigInteger.valueOf(32),
            200);
        assertEquals(BigInteger.valueOf(2), plan.getParallelsBig());
        assertEquals(
            BigInteger.ONE,
            limit(GtnlParallelMachine.LAPOTRON_CHIP, INT_MAX, false, BigInteger.ZERO).applyTo(HUGE));
    }

    @Test
    public void unselectedFiniteAndDisabledModesRetainTheirNativeLimits() throws Exception {
        assertEquals(
            INT_MAX,
            limit(GtnlParallelMachine.INDUSTRIAL_ARCANE_ASSEMBLER, INT_MAX, true, BigInteger.ONE).applyTo(HUGE));
        assertEquals(
            BigInteger.valueOf(4096),
            limit(GtnlParallelMachine.LAPOTRON_CHIP, BigInteger.valueOf(4096), true, BigInteger.ONE).applyTo(HUGE));
        GtnlUnlimitedParallelConfig.load(folder.newFile());
        assertEquals(INT_MAX, limit(GtnlParallelMachine.LAPOTRON_CHIP, INT_MAX, true, BigInteger.ONE).applyTo(HUGE));
    }

    @Test
    public void longNativeModesAndInvalidValuesAreNeverMistakenForIntUnlimitedModes() {
        assertTrue(
            limit(GtnlParallelMachine.ASSEMBLER_MATRIX, BigInteger.valueOf(Long.MAX_VALUE), true, BigInteger.ONE)
                .isUnlimited());
        assertFalse(limit(GtnlParallelMachine.ASSEMBLER_MATRIX, INT_MAX, true, BigInteger.ONE).isUnlimited());
        assertFalse(
            limit(GtnlParallelMachine.LAPOTRON_CHIP, BigInteger.valueOf(Long.MAX_VALUE), true, BigInteger.ONE)
                .isUnlimited());
        assertThrows(
            IllegalArgumentException.class,
            () -> limit(GtnlParallelMachine.ASSEMBLER_MATRIX, BigInteger.valueOf(-1), true, BigInteger.ONE));
        assertThrows(
            IllegalArgumentException.class,
            () -> limit(GtnlParallelMachine.LAPOTRON_CHIP, INT_MAX, false, BigInteger.valueOf(-1)));
    }

    private static ParallelLimit limit(final GtnlParallelMachine machine, final BigInteger nativeMaximum,
        final boolean alwaysMax, final BigInteger manualMaximum) {
        return GtnlParallelPolicy.getRequestedLimitBig(machine, nativeMaximum, alwaysMax, manualMaximum);
    }
}
