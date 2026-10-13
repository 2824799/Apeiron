package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Method;
import java.math.BigInteger;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigPlasmaMixerController;
import com.silvia.apeiron.math.ScientificInteger;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.gui.modularui.multiblock.MTETranscendentPlasmaMixerGui;
import gregtech.common.tileentities.machines.multi.MTETranscendentPlasmaMixer;
import io.netty.buffer.Unpooled;

public final class PlasmaMixerPanelSmoke {

    private PlasmaMixerPanelSmoke() {}

    private static final class Panel {

        final MTETranscendentPlasmaMixer machine = new MTETranscendentPlasmaMixer("apeiron.verify.plasma_panel");
        final PanelSyncManager sync;
        final ModularPanel panel;

        Panel(boolean client) throws ReflectiveOperationException {
            machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
            sync = new PanelSyncManager(new ModularSyncManager(client), true);
            MTETranscendentPlasmaMixerGui gui = new MTETranscendentPlasmaMixerGui(machine);
            Method register = MTETranscendentPlasmaMixerGui.class
                .getDeclaredMethod("registerSyncValues", PanelSyncManager.class);
            register.setAccessible(true);
            register.invoke(gui, sync);
            Method open = MTETranscendentPlasmaMixerGui.class
                .getDeclaredMethod("openParallelSelectPanel", PanelSyncManager.class, ModularPanel.class);
            open.setAccessible(true);
            panel = (ModularPanel) open.invoke(gui, sync, new ModularPanel("parent"));
        }

        StringSyncValue value(String name) {
            return sync.findSyncHandler(name, StringSyncValue.class);
        }

        BigPlasmaMixerController settings() {
            return (BigPlasmaMixerController) (Object) machine;
        }

        TextFieldWidget field(String name) {
            return find(panel, name);
        }
    }

    private static TextFieldWidget find(IWidget widget, String name) {
        if (widget instanceof TextFieldWidget && widget.isName(name)) return (TextFieldWidget) widget;
        for (IWidget child : widget.getChildren()) {
            TextFieldWidget result = find(child, name);
            if (result != null) return result;
        }
        return null;
    }

    public static void verify() {
        try {
            Panel server = new Panel(false), client = new Panel(true);
            copy(server.value("apeiron_plasma_parallel"), client.value("apeiron_plasma_parallel"));
            copy(server.value("apeiron_plasma_duration"), client.value("apeiron_plasma_duration"));
            check(
                ScientificInteger.nonNegative(
                    client.value("apeiron_plasma_parallel")
                        .getStringValue())
                    .equals(BigInteger.valueOf(Integer.MAX_VALUE))
                    && client.value("apeiron_plasma_duration")
                        .getStringValue()
                        .equals("1"),
                "defaults did not synchronize");
            submit(server, client, "apeiron_plasma_parallel", "1e60");
            check(
                server.settings()
                    .getPlasmaMixerParallel()
                    .equals(BigInteger.TEN.pow(60)),
                "large parallel clamped");
            submit(server, client, "apeiron_plasma_parallel", "0");
            check(
                server.settings()
                    .getParallelLimitBig()
                    .isUnlimited(),
                "zero did not remove setting limit");
            submit(server, client, "apeiron_plasma_duration", "1e1");
            check(
                server.settings()
                    .getPlasmaMixerDuration() == 10,
                "time edit rejected");
            check(
                ScientificInteger.positive(
                    client.field("apeiron_plasma_duration")
                        .getValidator()
                        .apply("0"))
                    .intValueExact() == 10,
                "zero time accepted");
            send(server.value("apeiron_plasma_parallel"), "-1");
            send(server.value("apeiron_plasma_duration"), "1e50");
            check(
                server.settings()
                    .getPlasmaMixerParallel()
                    .signum() == 0
                    && server.settings()
                        .getPlasmaMixerDuration() == 10,
                "invalid packet changed settings");
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Plasma mixer panel fixture failed", error);
        }
        Apeiron.LOG.info(
            "Plasma mixer panel verification passed: native popup, exact/default/zero settings, scientific input and server validation");
    }

    private static void submit(Panel server, Panel client, String key, String text) throws java.io.IOException {
        String valid = client.field(key)
            .getValidator()
            .apply(text);
        check(valid.equals(text), "valid input rejected");
        send(server.value(key), valid);
        copy(server.value(key), client.value(key));
    }

    private static void send(StringSyncValue target, String text) throws java.io.IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new StringSyncValue(() -> text).write(packet);
            target.readOnServer(0, packet);
        } finally {
            packet.release();
        }
    }

    private static void copy(StringSyncValue source, StringSyncValue target) throws java.io.IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            source.write(packet);
            target.read(packet);
        } finally {
            packet.release();
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Plasma mixer panel: " + message);
    }
}
