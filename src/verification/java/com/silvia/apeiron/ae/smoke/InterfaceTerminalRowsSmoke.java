package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.opengl.GL11;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.terminal.InterfaceTerminalRows;
import com.silvia.apeiron.client.gui.InterfaceTerminalFilterButton;
import com.silvia.apeiron.client.gui.InterfaceTerminalFilterMode;

import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.ContainerNull;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStackType;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.FMLEventChannel;
import io.netty.buffer.ByteBuf;

/** Native client grouping, rendering and inventory-action packets, with no server connection. */
public final class InterfaceTerminalRowsSmoke {

    private InterfaceTerminalRowsSmoke() {}

    private static final class Terminal extends GuiInterfaceTerminal {

        private Terminal() {
            super(new ContainerNull());
            mc = Minecraft.getMinecraft();
            fontRendererObj = mc.fontRenderer;
            width = 640;
            height = 480;
        }

        private void clickFilter(GuiButton button) {
            actionPerformed(button);
        }
    }

    private static final class CaptureNetwork extends NetworkHandler {

        private AppEngPacket captured;

        private CaptureNetwork() {
            super("ApeironRowsTest");
        }

        @Override
        public void sendToServer(AppEngPacket packet) {
            release();
            captured = packet;
        }

        private void release() {
            if (captured != null) {
                captured.getProxy()
                    .payload()
                    .release();
                captured = null;
            }
        }
    }

    public static void verify() {
        InterfaceTerminalFilterMode original = InterfaceTerminalFilterMode.current();
        NetworkHandler previous = NetworkHandler.instance;
        CaptureNetwork network = new CaptureNetwork();
        try {
            NetworkHandler.instance = network;
            InterfaceTerminalFilterMode.ALL.select();
            Terminal gui = new Terminal();
            Field viewHeight = field(GuiInterfaceTerminal.class, "viewHeight");
            viewHeight.setInt(gui, 150);
            Object master = read(gui, "masterList");
            GuiImgButton filter = (GuiImgButton) read(gui, "guiButtonHideFull");
            check(filter instanceof InterfaceTerminalFilterButton, "third-state button not installed");
            add(gui, 1, "Assembly", 40, 9, 360, 359);
            Object entry = entry(master, 1);
            InterfaceTerminalRows rows = (InterfaceTerminalRows) entry;
            check(
                rows.apeiron$visibleRowCount() == 40 && rows.apeiron$physicalSlot(8) == 8,
                "all mode compressed rows");
            int originalHeight = height(master);
            gui.clickFilter(filter);
            check(
                InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_INTERFACES,
                "first cycle skipped native mode");
            check(height(master) == originalHeight, "free-interface mode compressed rows");
            gui.clickFilter(filter);
            check(
                InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_ROWS,
                "second cycle missed row mode");
            check(
                rows.apeiron$visibleRowCount() == 1 && rows.apeiron$physicalSlot(0) == 351,
                "last-row gap stayed below full rows");
            check(
                height(master) == originalHeight - 39 * 18,
                "scrollbar retained full-row height: original=" + originalHeight
                    + ", compact="
                    + height(master)
                    + ", entry="
                    + rows.apeiron$displayHeight());
            check(
                filter.getMessage()
                    .contains("空缺的行")
                    || filter.getMessage()
                        .contains("rows with free slots"),
                "row tooltip missing");
            Terminal reopened = new Terminal();
            check(
                InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_ROWS
                    && read(reopened, "guiButtonHideFull") instanceof InterfaceTerminalFilterButton,
                "reopening lost row mode");

            // Original slot 352 is an occupied neighbour of the last free slot, now displayed in row zero.
            renderEntry(gui, entry);
            check((int) read(entry, "hoveredSlotIdx") == 352, "render hover addressed virtual slot");
            check(
                ItemStack.areItemStacksEqual(gui.getHoveredStack(), inventory(entry).getStackInSlot(352)),
                "tooltip addressed hidden row");
            for (int button = 0; button < 3; button++) {
                boolean clicked = (boolean) method(entry, "mouseClicked", int.class, int.class, int.class)
                    .invoke(entry, 32, 5, button);
                check(
                    clicked && network.captured instanceof PacketInventoryAction,
                    "native click did not create action packet");
                ByteBuf payload = network.captured.getProxy()
                    .payload()
                    .duplicate();
                payload.readInt();
                PacketInventoryAction decoded = new PacketInventoryAction(payload);
                check(
                    (int) read(decoded, "slot") == 352 && (long) read(decoded, "id") == 1L,
                    "packet addressed virtual slot");
                check(
                    read(decoded, "action")
                        == (button < 2 ? InventoryAction.PICKUP_OR_SET_DOWN : InventoryAction.CREATIVE_DUPLICATE),
                    "row projection changed native click action");
            }

            // Partial updates must immediately remove newly full rows and expose newly emptied distant rows.
            set(entry, 359, new ItemStack(Items.diamond));
            dirty(master);
            check(rows.apeiron$visibleRowCount() == 0 && height(master) == 0, "newly full interface stayed visible");
            set(entry, 10, null);
            dirty(master);
            check(
                rows.apeiron$visibleRowCount() == 1 && rows.apeiron$physicalSlot(0) == 9,
                "partial update retained old row map");
            check(height(master) > 0, "newly free interface stayed hidden");

            add(gui, 2, "Ordinary interface", 1, 9, 9, 8);
            Object ordinary = entry(master, 2);
            check(((InterfaceTerminalRows) ordinary).apeiron$visibleRowCount() == 1, "ordinary interface row hidden");
            add(gui, 3, "Ten columns", 36, 10, 360, 105);
            InterfaceTerminalRows tenColumns = (InterfaceTerminalRows) entry(master, 3);
            check(
                tenColumns.apeiron$visibleRowCount() == 1 && tenColumns.apeiron$physicalSlot(9) == 109,
                "alternative provider width mis-mapped");

            add(gui, 4, "Empty assembly", 40, 9, 360, -2);
            Object emptyEntry = entry(master, 4);
            InterfaceTerminalRows emptyRows = (InterfaceTerminalRows) emptyEntry;
            check(
                emptyRows.apeiron$visibleRowCount() == 1 && emptyRows.apeiron$physicalSlot(0) == 0,
                "empty assembly displayed more than its first row");
            for (int slot = 0; slot < 9; slot++) {
                set(emptyEntry, slot, new ItemStack(Items.diamond));
                dirty(master);
                check(
                    emptyRows.apeiron$visibleRowCount() == 1
                        && emptyRows.apeiron$physicalSlot(0) == (slot == 8 ? 9 : 0),
                    "progressive insertion exposed the next empty row too early or too late");
            }

            gui.clickFilter(filter);
            check(
                InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.ALL
                    && rows.apeiron$visibleRowCount() == 40,
                "third cycle did not restore all rows");
            InterfaceTerminalFilterMode.cycle(true);
            check(
                InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_ROWS,
                "backwards cycle missed row mode");
            InterfaceTerminalFilterMode.cycle(true);
            check(
                InterfaceTerminalFilterMode.current() == InterfaceTerminalFilterMode.FREE_INTERFACES,
                "backwards cycle missed interface mode");
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Interface terminal free-row verification failed", error);
        } finally {
            original.select();
            NetworkHandler.instance = previous;
            network.release();
            FMLCommonHandler.instance()
                .bus()
                .unregister(network);
            try {
                ((FMLEventChannel) field(NetworkHandler.class, "ec").get(network)).unregister(network);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not dispose interface-terminal packet capture", error);
            }
        }
        Apeiron.LOG.info(
            "Interface terminal free-row verification passed: three modes, 360-slot and ordinary providers, native rendering/hover/action packets, scroll height, partial updates and reopen");
    }

    private static void add(Terminal gui, long id, String name, int rows, int width, int slots, int free)
        throws ReflectiveOperationException {
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < slots; slot++) items.appendTag(
            slot == free || free == -2 ? new NBTTagCompound()
                : new ItemStack(Items.diamond).writeToNBT(new NBTTagCompound()));
        PacketInterfaceTerminalUpdate packet = new PacketInterfaceTerminalUpdate();
        packet.addNewEntry(id, name, true)
            .setItems(rows, width, slots, items)
            .setReps(new ItemStack(Items.redstone), null)
            .setTerminalVisible(true)
            .setSupportedStackTypes(new IAEStackType<?>[] { AEItemStackType.ITEM_STACK_TYPE });
        @SuppressWarnings("unchecked")
        List<PacketInterfaceTerminalUpdate.PacketEntry> commands = (List<PacketInterfaceTerminalUpdate.PacketEntry>) field(
            PacketInterfaceTerminalUpdate.class,
            "commands").get(packet);
        gui.postUpdate(commands, 0);
    }

    private static void renderEntry(Terminal gui, Object entry) throws ReflectiveOperationException {
        Object section = read(entry, "section");
        field(section.getClass(), "visible").setBoolean(section, true);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 640, 480, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            Method draw = GuiInterfaceTerminal.class
                .getDeclaredMethod("drawEntry", entry.getClass(), int.class, int.class, int.class, int.class);
            draw.setAccessible(true);
            check((int) draw.invoke(gui, entry, 0, 0, 32, 5) == 19, "native renderer reserved hidden rows");
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glPopAttrib();
        }
    }

    private static Object entry(Object master, long id) throws ReflectiveOperationException {
        return ((Map<?, ?>) read(master, "list")).get(id);
    }

    private static int height(Object master) throws ReflectiveOperationException {
        return (int) method(master, "getHeight").invoke(master);
    }

    private static void dirty(Object master) throws ReflectiveOperationException {
        method(master, "markDirty").invoke(master);
    }

    private static appeng.tile.inventory.AppEngInternalInventory inventory(Object entry)
        throws ReflectiveOperationException {
        return (appeng.tile.inventory.AppEngInternalInventory) read(entry, "inv");
    }

    private static void set(Object entry, int slot, ItemStack stack) throws ReflectiveOperationException {
        method(entry, "setItemInSlot", ItemStack.class, int.class).invoke(entry, stack, slot);
    }

    private static Method method(Object target, String name, Class<?>... args) throws ReflectiveOperationException {
        Method method = target.getClass()
            .getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        return field(target instanceof Terminal ? GuiInterfaceTerminal.class : target.getClass(), name).get(target);
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Interface terminal rows: " + message);
    }
}
