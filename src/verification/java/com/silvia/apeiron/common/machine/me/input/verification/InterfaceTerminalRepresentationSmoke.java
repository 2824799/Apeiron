package com.silvia.apeiron.common.machine.me.input.verification;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.container.ContainerNull;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;

/** Checks the actual client entry after AE has applied a decoded terminal update. */
public final class InterfaceTerminalRepresentationSmoke {

    private InterfaceTerminalRepresentationSmoke() {}

    private static final class Terminal extends GuiInterfaceTerminal {

        private Terminal() {
            super(new ContainerNull());
            // Verification runs during mod loading, before a player exists to open a container.
            mc = Minecraft.getMinecraft();
            fontRendererObj = mc.fontRenderer;
            width = 640;
            height = 480;
        }
    }

    public static void verify(List<PacketInterfaceTerminalUpdate.PacketEntry> commands)
        throws ReflectiveOperationException {
        GuiInterfaceTerminal gui = new Terminal();
        gui.postUpdate(commands, 0);
        Object masterList = field(gui, "masterList");
        Map<?, ?> entries = (Map<?, ?>) field(masterList, "list");
        for (PacketInterfaceTerminalUpdate.PacketEntry command : commands) {
            PacketInterfaceTerminalUpdate.PacketAdd packet = (PacketInterfaceTerminalUpdate.PacketAdd) command;
            Object entry = entries.get(packet.entryId);
            if (entry == null) throw new IllegalStateException("Interface terminal did not create the client entry");
            ItemStack self = (ItemStack) field(entry, "selfRep");
            // mouseClicked reads this same stack's display name when highlighting the provider.
            if (self == null || !ItemStack.areItemStacksEqual(self, packet.selfRep)
                || self.getDisplayName()
                    .isEmpty())
                throw new IllegalStateException("Interface terminal highlight has no device name");
            if (!(boolean) field(entry, "terminalVisible") || (int) field(entry, "rows") != packet.rows
                || (int) field(entry, "rowSize") != packet.rowSize)
                throw new IllegalStateException("Interface terminal changed the pattern assembly layout");
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Class<?> owner = target instanceof Terminal ? GuiInterfaceTerminal.class : target.getClass();
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
