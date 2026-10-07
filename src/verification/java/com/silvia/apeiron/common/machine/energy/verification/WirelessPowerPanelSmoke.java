package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.math.ScientificInteger;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;
import io.netty.buffer.Unpooled;

/** Client fixtures deliberately have no hatch list, just like a real connected multiplayer controller. */
public final class WirelessPowerPanelSmoke {

    private WirelessPowerPanelSmoke() {}

    private static MTEMultiBlockBase controller() {
        MTEMultiBlockBase machine = new MTEElectricBlastFurnace("apeiron.verify.power_panel");
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        return machine;
    }

    private static final class Panel {

        private final PanelSyncManager sync;
        private final ModularPanel panel;

        private Panel(MTEMultiBlockBase machine, boolean client) throws ReflectiveOperationException {
            sync = new PanelSyncManager(new ModularSyncManager(client), true);
            Method open = MTEMultiBlockBaseGui.class
                .getDeclaredMethod("openPowerControlPanel", PanelSyncManager.class, ModularPanel.class);
            open.setAccessible(true);
            panel = (ModularPanel) open
                .invoke(new MTEMultiBlockBaseGui<>(machine), sync, new ModularPanel("testParent"));
        }

        private TextFieldWidget field(String name) {
            List<IWidget> widgets = new ArrayList<>();
            collect(panel, widgets);
            for (IWidget widget : widgets)
                if (widget.isName(name) && widget instanceof TextFieldWidget) return (TextFieldWidget) widget;
            throw new IllegalStateException("Power panel omitted field " + name);
        }

        private StringSyncValue value(String name) {
            return sync.findSyncHandler(name, StringSyncValue.class);
        }
    }

    public static void verify() {
        try {
            for (boolean ultimate : new boolean[] { false, true }) {
                MTEMultiBlockBase server = controller(), client = controller();
                ApeironMachineTile tile = new ApeironMachineTile();
                tile.setInitialValuesAsNBT(
                    null,
                    (short) ApeironConfig.getMachineId(
                        ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET
                            : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET));
                server.mEnergyHatches.add((MTEInfiniteEnergyHatch) tile.getMetaTileEntity());
                WirelessRecipeState state = ((BigWirelessController) server).getWirelessRecipeState();
                Panel sent = new Panel(server, false), received = new Panel(client, true);
                check(client.mEnergyHatches.isEmpty(), "client unexpectedly has server structure data");
                for (String name : new String[] { "apeiron_parallel", "apeiron_voltage", "apeiron_duration" }) {
                    sent.field(name);
                    received.field(name);
                    copy(sent.value(name), received.value(name));
                }
                copy(
                    sent.sync.findSyncHandler("apeiron_energyInstalled", BooleanSyncValue.class),
                    received.sync.findSyncHandler("apeiron_energyInstalled", BooleanSyncValue.class));
                copy(
                    sent.sync.findSyncHandler("apeiron_ultimateEnergy", BooleanSyncValue.class),
                    received.sync.findSyncHandler("apeiron_ultimateEnergy", BooleanSyncValue.class));
                updateListeners(received.panel);
                check(
                    visible(received.panel, received.field("apeiron_parallel"), true),
                    "client parallel editor is hidden");
                check(
                    visible(received.panel, received.field(ultimate ? "apeiron_duration" : "apeiron_voltage"), true),
                    "client voltage/time editor is hidden");
                check(
                    !visible(received.panel, received.field(ultimate ? "apeiron_voltage" : "apeiron_duration"), true),
                    "wrong hatch editor is visible");
                check(
                    ScientificInteger.nonNegative(
                        received.value("apeiron_parallel")
                            .getStringValue())
                        .equals(BigInteger.valueOf(Integer.MAX_VALUE)),
                    "default parallel cap not synchronized");
                submit(sent, received, "apeiron_parallel", "1e60");
                check(
                    state.getParallelSettingBig()
                        .equals(BigInteger.TEN.pow(60)),
                    "scientific parallel input was clamped");
                submit(sent, received, "apeiron_parallel", "0");
                check(
                    state.getLimit()
                        .isUnlimited(),
                    "manual zero did not enable unlimited parallel");
                submit(sent, received, ultimate ? "apeiron_duration" : "apeiron_voltage", ultimate ? "1" : "3.2768E4");
                check(
                    ultimate ? state.getTargetDuration() == 1 : state.getVoltageSetting() == 32768,
                    "server ignored voltage/time edit");
                verifyLegacy(server, client, ultimate);
                server.mEnergyHatches.clear();
                sent.sync.findSyncHandler("apeiron_energyInstalled", BooleanSyncValue.class)
                    .updateCacheFromSource(false);
                copy(
                    sent.sync.findSyncHandler("apeiron_energyInstalled", BooleanSyncValue.class),
                    received.sync.findSyncHandler("apeiron_energyInstalled", BooleanSyncValue.class));
                updateListeners(received.panel);
                check(
                    !visible(received.panel, received.field("apeiron_parallel"), true),
                    "removed hatch retained wireless editor");
                BigInteger old = state.getParallelSettingBig();
                sendText(sent.value("apeiron_parallel"), "99");
                check(
                    state.getParallelSettingBig()
                        .equals(old),
                    "removed hatch accepted wireless configuration");
            }
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Wireless power panel verification failed", error);
        }
        Apeiron.LOG.info(
            "Wireless power panel verification passed: client without hatch lists, both MUI versions, installed mode packets, exact parallel and voltage/time edits");
    }

    private static void submit(Panel server, Panel client, String key, String text) throws java.io.IOException {
        String valid = client.field(key)
            .getValidator()
            .apply(text);
        check(valid.equals(text), "client validator rejected " + text);
        sendText(server.value(key), valid);
        copy(server.value(key), client.value(key));
        check(
            client.value(key)
                .getStringValue()
                .equals(
                    server.value(key)
                        .getStringValue()),
            "configuration reply mismatch");
    }

    private static void sendText(StringSyncValue server, String text) throws java.io.IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new StringSyncValue(() -> text).write(packet);
            server.readOnServer(0, packet);
        } finally {
            packet.release();
        }
    }

    private static void copy(com.cleanroommc.modularui.api.value.sync.IValueSyncHandler<?> server,
        com.cleanroommc.modularui.api.value.sync.IValueSyncHandler<?> client) throws java.io.IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            server.write(packet);
            client.read(packet);
        } finally {
            packet.release();
        }
    }

    private static void collect(IWidget root, List<IWidget> result) {
        result.add(root);
        for (IWidget child : root.getChildren()) collect(child, result);
    }

    private static boolean visible(IWidget root, IWidget target, boolean ancestorsEnabled) {
        boolean enabled = ancestorsEnabled && root.isEnabled();
        if (root == target) return enabled;
        for (IWidget child : root.getChildren()) if (visible(child, target, enabled)) return true;
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void updateListeners(IWidget widget) {
        if (widget instanceof Widget && ((Widget) widget).getOnUpdateListener() != null)
            ((Widget) widget).getOnUpdateListener()
                .accept(widget);
        for (IWidget child : widget.getChildren()) updateListeners(child);
    }

    private static void verifyLegacy(MTEMultiBlockBase server, MTEMultiBlockBase client, boolean ultimate)
        throws ReflectiveOperationException, java.io.IOException {
        com.gtnewhorizons.modularui.api.screen.ModularWindow sent = server.createPowerPanel(null);
        com.gtnewhorizons.modularui.api.screen.ModularWindow received = client.createPowerPanel(null);
        check(sent.syncedWidgets.size() == received.syncedWidgets.size(), "MUI1 client/server widget IDs diverged");
        List<com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.BooleanSyncer> bools = new ArrayList<>();
        List<com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget> fields = new ArrayList<>();
        for (com.gtnewhorizons.modularui.api.widget.Widget widget : received.getChildren()) {
            if (widget instanceof com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.BooleanSyncer)
                bools.add((com.gtnewhorizons.modularui.common.widget.FakeSyncWidget.BooleanSyncer) widget);
            if (widget instanceof com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget)
                fields.add((com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget) widget);
        }
        check(bools.size() == 4 && fields.size() == 2, "MUI1 client omitted wireless widgets");
        for (int index = 0; index < 2; index++) {
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            try {
                packet.writeBoolean(index == 0 || ultimate);
                bools.get(index + 2)
                    .readOnClient(0, packet);
            } finally {
                packet.release();
            }
        }
        check(
            fields.get(0)
                .isEnabled()
                && fields.get(1)
                    .isEnabled(),
            "MUI1 client wireless fields are disabled");
        java.lang.reflect.Field getter = com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget.class
            .getDeclaredField("getter");
        getter.setAccessible(true);
        String config = (String) ((java.util.function.Supplier<?>) getter.get(fields.get(1))).get();
        check(
            config.equals(ultimate ? "128" : Integer.toString(Integer.MAX_VALUE)),
            "MUI1 used local hatch lists to choose voltage/time");
        List<com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget> serverFields = new ArrayList<>();
        for (com.gtnewhorizons.modularui.api.widget.Widget widget : sent.getChildren())
            if (widget instanceof com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget)
                serverFields.add((com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget) widget);
        for (int index = 0; index < 2; index++) {
            String input = index == 0 ? "7e30" : ultimate ? "9" : "6.5536e4";
            PacketBuffer request = new PacketBuffer(Unpooled.buffer());
            try {
                com.gtnewhorizons.modularui.common.internal.network.NetworkUtils.writeStringSafe(request, input);
                serverFields.get(index)
                    .readOnServer(1, request);
            } finally {
                request.release();
            }
            String reply = (String) ((java.util.function.Supplier<?>) getter.get(serverFields.get(index))).get();
            PacketBuffer response = new PacketBuffer(Unpooled.buffer());
            try {
                response.writeBoolean(true);
                com.gtnewhorizons.modularui.common.internal.network.NetworkUtils.writeStringSafe(response, reply);
                fields.get(index)
                    .readOnClient(1, response);
                check(
                    fields.get(index)
                        .getText()
                        .equals(reply),
                    "MUI1 client did not display server configuration");
            } finally {
                response.release();
            }
        }
        WirelessRecipeState state = ((BigWirelessController) server).getWirelessRecipeState();
        check(
            state.getParallelSettingBig()
                .equals(
                    BigInteger.TEN.pow(30)
                        .multiply(BigInteger.valueOf(7))),
            "MUI1 parallel edit was clamped");
        check(
            ultimate ? state.getTargetDuration() == 9 : state.getVoltageSetting() == 65536,
            "MUI1 voltage/time edit was ignored");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Wireless power panel: " + message);
    }
}
