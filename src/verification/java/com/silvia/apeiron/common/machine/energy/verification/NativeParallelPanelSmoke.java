package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Method;
import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;
import io.netty.buffer.Unpooled;

/** Exercises the transformed native panel and its first sync without clicking the maximum toggle. */
public final class NativeParallelPanelSmoke {

    private NativeParallelPanelSmoke() {}

    private static final class Controller extends MTEElectricBlastFurnace {

        private int maximum = 12;

        private Controller() {
            super("apeiron.verification.native_parallel");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public int getMaxParallelRecipes() {
            return maximum;
        }
    }

    private static final class Gui extends MTEMultiBlockBaseGui<MTEMultiBlockBase> {

        private IntSyncValue value;
        private IntSyncValue maximum;
        private BooleanSyncValue locked;

        private Gui(MTEMultiBlockBase machine) {
            super(machine);
        }

        @Override
        protected IWidget makeParallelConfiguratorTextFieldWidget(IntSyncValue maximum, BooleanSyncValue locked,
            IntSyncValue value) {
            this.maximum = maximum;
            this.locked = locked;
            this.value = value;
            return super.makeParallelConfiguratorTextFieldWidget(maximum, locked, value);
        }
    }

    public static void verify() {
        try {
            Controller machine = new Controller();
            check(
                !machine.getBaseMetaTileEntity()
                    .isActive() && machine.getBaseMetaTileEntity()
                        .isAllowedToWork(),
                "native placement work/active defaults were changed");
            check(
                machine.mMaxProgresstime == 0 && machine.getmStartUpCheck() > 0,
                "fresh controller has a running recipe instead of native structure startup");
            machine.loadNBTData(new NBTTagCompound());
            check(machine.isAlwaysMaxParallel(), "missing NBT did not default to maximum lock");
            check(InfiniteEnergyHatches.find(machine) == null, "native fixture has an infinite hatch");
            check(machine.getTrueParallel() == 12, "native processing maximum is not 12");
            Gui gui = panel(machine);
            check(gui.locked.getBoolValue(), "first panel sync lost the maximum lock");
            check(
                gui.value.getIntValue() == 12,
                "first native panel sync should be 12, got " + gui.value.getIntValue());
            assertPacket(gui.value, 12);

            machine.maximum = 24;
            gui = panel(machine);
            check(gui.value.getIntValue() == 24, "reopened locked panel did not track the machine maximum");

            machine.setAlwaysMaxParallel(false);
            machine.setPowerPanelMaxParallel(7);
            gui.locked.updateCacheFromSource(false);
            gui.value.updateCacheFromSource(false);
            check(gui.value.getIntValue() == 7 && machine.getTrueParallel() == 7, "manual parallel cap was changed");
            NBTTagCompound manualSave = new NBTTagCompound();
            machine.saveNBTData(manualSave);
            Controller restored = new Controller();
            restored.loadNBTData(manualSave);
            check(
                !restored.isAlwaysMaxParallel() && panel(restored).value.getIntValue() == 7,
                "manual cap did not survive reload");
            NBTTagCompound stale = new NBTTagCompound();
            stale.setInteger("powerPanelMaxParallel", 0);
            stale.setBoolean("alwaysMaxParallel", true);
            NBTTagCompound wireless = new NBTTagCompound();
            wireless.setInteger("settingsVersion", 1);
            wireless.setLong("parallelSetting", 0);
            stale.setTag("ApeironWirelessRecipe", wireless);
            restored.loadNBTData(stale);
            check(
                ((BigWirelessController) (Object) restored).getWirelessRecipeState()
                    .getParallelSettingBig()
                    .signum() == 0,
                "stale fixture did not retain the independent wireless zero");
            check(panel(restored).value.getIntValue() == 12, "stale save or wireless state overrode native parallel");

            verifyTst();
            verifyInfiniteHatch(machine);
            Apeiron.LOG.info(
                "Native parallel panel verification passed: first sync, maximum changes, manual caps, reload, TST and infinite hatch isolation");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native parallel panel verification failed", error);
        }
    }

    private static Gui panel(MTEMultiBlockBase machine) throws ReflectiveOperationException {
        Gui gui = new Gui(machine);
        PanelSyncManager sync = new PanelSyncManager(new ModularSyncManager(true), true);
        Method open = MTEMultiBlockBaseGui.class
            .getDeclaredMethod("openPowerControlPanel", PanelSyncManager.class, ModularPanel.class);
        open.setAccessible(true);
        open.invoke(gui, sync, new ModularPanel("apeiron.verification.parent"));
        check(gui.value != null && gui.maximum != null && gui.locked != null, "native text field was not created");
        return gui;
    }

    private static void assertPacket(IntSyncValue value, int expected) {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            value.write(packet);
            IntSyncValue client = new IntSyncValue(() -> 0);
            client.read(packet);
            check(client.getIntValue() == expected, "first client packet contained a stale native value");
        } finally {
            packet.release();
        }
    }

    private static void verifyTst() throws ReflectiveOperationException {
        if (!cpw.mods.fml.common.Loader.isModLoaded("TwistSpaceTechnology")) return;
        MTEMultiBlockBase machine = (MTEMultiBlockBase) Class
            .forName("com.Nxer.TwistSpaceTechnology.common.machine.GT_TileEntity_PhysicalFormSwitcher")
            .getConstructor(String.class)
            .newInstance("apeiron.verification.native_tst_parallel");
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        machine.loadNBTData(new NBTTagCompound());
        check(machine.isAlwaysMaxParallel(), "TST lost its default maximum lock");
        int expected = machine.getMaxParallelRecipes();
        check(
            expected > 0 && panel(machine).value.getIntValue() == expected,
            "TST first panel sync ignored its native maximum");
    }

    private static void verifyInfiniteHatch(Controller machine) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(
            null,
            (short) ApeironConfig.getMachineId(ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET));
        machine.mEnergyHatches.add((MTEInfiniteEnergyHatch) tile.getMetaTileEntity());
        check(InfiniteEnergyHatches.find(machine) != null, "infinite hatch fixture is invalid");
        BigWirelessController wireless = (BigWirelessController) (Object) machine;
        wireless.getWirelessRecipeState()
            .setParallelSettingBig(BigInteger.ZERO);
        check(
            wireless.getParallelLimitBig()
                .isUnlimited(),
            "native panel repair disabled manual unlimited mode");
        BigInteger huge = BigInteger.TEN.pow(60);
        wireless.getWirelessRecipeState()
            .setParallelSettingBig(huge);
        check(
            wireless.getParallelLimitBig()
                .getBound()
                .get()
                .equals(huge),
            "native panel repair truncated the wireless cap");
        check(
            wireless.getWirelessRecipeState()
                .getParallelSettingBig()
                .equals(huge),
            "native panel changed the saved wireless cap");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Native parallel panel verification: " + message);
    }
}
