package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.value.sync.IValueSyncHandler;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigPurificationUnit;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.client.gui.machine.energy.PurificationParallelWidgets;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.gui.modularui.multiblock.MTEPurificationUnitBaseGui;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationPlant;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import io.netty.buffer.Unpooled;

/** Exercise the real unit button and popup with a client that has no link or hatch list. */
public final class PurificationParallelPanelSmoke {

    private PurificationParallelPanelSmoke() {}

    private static final class Panel {

        final PanelSyncManager sync;
        final PanelSyncManager popupSync;
        final IWidget button;
        final ModularPanel popup;

        Panel(MTEPurificationUnitBase<?> unit, boolean client) throws ReflectiveOperationException {
            ModularSyncManager manager = new ModularSyncManager(client);
            sync = new PanelSyncManager(manager, true);
            popupSync = new PanelSyncManager(manager, false);
            MTEPurificationUnitBaseGui gui = new MTEPurificationUnitBaseGui(unit);
            Method register = MTEPurificationUnitBaseGui.class
                .getDeclaredMethod("registerSyncValues", PanelSyncManager.class);
            register.setAccessible(true);
            register.invoke(gui, sync);
            Method create = MTEPurificationUnitBaseGui.class
                .getDeclaredMethod("createParallelButton", PanelSyncManager.class, ModularPanel.class);
            create.setAccessible(true);
            button = (IWidget) create.invoke(gui, sync, new ModularPanel("verificationParent"));
            PanelSyncHandler handler = (PanelSyncHandler) sync.findPanelHandlerNullable("apeironPurificationParallel");
            check(handler != null, "unit button omitted the exact popup");
            popup = handler.createUI(popupSync);
        }

        StringSyncValue value() {
            return popupSync.findSyncHandler(PurificationParallelWidgets.VALUE, StringSyncValue.class);
        }
    }

    public static void verify() {
        try {
            for (int kind = 0; kind < 8; kind++) for (boolean ultimate : new boolean[] { false, true }) {
                MTEPurificationUnitBase<?> server = PurificationWaterSmoke.unit(kind);
                MTEPurificationUnitBase<?> client = PurificationWaterSmoke.unit(kind);
                server.setBaseMetaTileEntity(new BaseMetaTileEntity());
                client.setBaseMetaTileEntity(new BaseMetaTileEntity());
                server.setMaxParallel(12345);
                MTEPurificationPlant plant = new MTEPurificationPlant("apeiron.verify.parallel.popup.plant");
                plant.setBaseMetaTileEntity(new BaseMetaTileEntity());
                Field controller = MTEPurificationUnitBase.class.getDeclaredField("controller");
                controller.setAccessible(true);
                controller.set(server, plant);
                ApeironMachineTile tile = new ApeironMachineTile();
                tile.setInitialValuesAsNBT(
                    null,
                    (short) ApeironConfig.getMachineId(
                        ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET
                            : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET));
                plant.mEnergyHatches.add((MTEInfiniteEnergyHatch) tile.getMetaTileEntity());
                Panel sent = new Panel(server, false), received = new Panel(client, true);
                check(client.getController() == null, "client unexpectedly knows the linked plant");
                copy(
                    sent.sync.findSyncHandler(PurificationParallelWidgets.INSTALLED, BooleanSyncValue.class),
                    received.sync.findSyncHandler(PurificationParallelWidgets.INSTALLED, BooleanSyncValue.class));
                copy(sent.value(), received.value());
                update(received.button);
                check(
                    find(received.button, "apeiron_purificationParallelButton").isEnabled(),
                    "client did not unlock the unit button");
                check(
                    sent.value()
                        .getStringValue()
                        .equals("12345"),
                    "native parallel setting was not retained");
                for (String text : new String[] { "5000000000", "1e24", "0" }) {
                    TextFieldWidget field = (TextFieldWidget) find(received.popup, PurificationParallelWidgets.VALUE);
                    check(
                        field.getValidator()
                            .apply(text)
                            .equals(text),
                        "client rejected big parallel " + text);
                    PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
                    try {
                        new StringSyncValue(() -> text).write(packet);
                        sent.value()
                            .readOnServer(0, packet);
                    } finally {
                        packet.release();
                    }
                    BigInteger expected = new java.math.BigDecimal(text).toBigIntegerExact();
                    check(
                        ((BigPurificationUnit) server).getPurificationParallelSettingBig()
                            .equals(expected),
                        "server truncated parallel " + text);
                    copy(sent.value(), received.value());
                    check(
                        received.value()
                            .getStringValue()
                            .equals(
                                sent.value()
                                    .getStringValue()),
                        "exact reply was truncated");
                }
                check(
                    ((BigWirelessController) server).getParallelLimitBig()
                        .isUnlimited(),
                    "zero did not unlock the cycle limit");
                plant.mEnergyHatches.clear();
                sent.sync.findSyncHandler(PurificationParallelWidgets.INSTALLED, BooleanSyncValue.class)
                    .updateCacheFromSource(false);
                copy(
                    sent.sync.findSyncHandler(PurificationParallelWidgets.INSTALLED, BooleanSyncValue.class),
                    received.sync.findSyncHandler(PurificationParallelWidgets.INSTALLED, BooleanSyncValue.class));
                update(received.button);
                check(
                    !find(received.button, "apeiron_purificationParallelButton").isEnabled(),
                    "removed hatch kept the exact editor");
                PacketBuffer stale = new PacketBuffer(Unpooled.buffer());
                try {
                    new StringSyncValue(() -> "999").write(stale);
                    sent.value()
                        .readOnServer(0, stale);
                } finally {
                    stale.release();
                }
                check(
                    ((BigPurificationUnit) server).getPurificationParallelSettingBig()
                        .signum() == 0,
                    "stale popup changed settings after the hatch was removed");
            }
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Purification parallel popup verification failed", error);
        }
        Apeiron.LOG.info(
            "Purification parallel popup verification passed: all eight units, inherited plant hatch, client without links, >int/>long/zero edits and removed-hatch guard");
    }

    private static void copy(IValueSyncHandler<?> server, IValueSyncHandler<?> client) throws java.io.IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            server.write(packet);
            client.read(packet);
        } finally {
            packet.release();
        }
    }

    private static IWidget find(IWidget root, String name) {
        if (root.isName(name)) return root;
        for (IWidget child : root.getChildren()) {
            IWidget found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void update(IWidget root) {
        if (root instanceof Widget && ((Widget) root).getOnUpdateListener() != null)
            ((Widget) root).getOnUpdateListener()
                .accept(root);
        for (IWidget child : root.getChildren()) update(child);
    }

    private static void check(boolean okay, String message) {
        if (!okay) throw new IllegalStateException("Purification parallel popup: " + message);
    }
}
