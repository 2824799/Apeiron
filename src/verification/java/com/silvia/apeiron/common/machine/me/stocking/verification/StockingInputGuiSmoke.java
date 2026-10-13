package com.silvia.apeiron.common.machine.me.stocking.verification;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.client.gui.machine.me.input.InfiniteStorageInputGui;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternStackCodec;
import com.silvia.apeiron.common.machine.me.stocking.StockingFilter;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.implementations.MTEHatch;
import io.netty.buffer.Unpooled;

/** Real ModularUI fields and packets, independent of the client's absent server-side ME connection. */
public final class StockingInputGuiSmoke {

    private StockingInputGuiSmoke() {}

    private static final class Panel {

        private final InfiniteStorageInputGui gui;
        private final StockingInputLogic logic;
        private final PanelSyncManager sync;
        private final ModularPanel panel;

        private Panel(int offset, boolean client) throws ReflectiveOperationException {
            ApeironMachineTile tile = new ApeironMachineTile();
            tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
            MTEHatch machine = (MTEHatch) tile.getMetaTileEntity();
            logic = ((StockingInputHost) machine).getStockingInput();
            gui = new InfiniteStorageInputGui(machine);
            sync = new PanelSyncManager(new ModularSyncManager(client), true);
            Method build = InfiniteStorageInputGui.class.getDeclaredMethod("configPanel", PanelSyncManager.class);
            build.setAccessible(true);
            panel = (ModularPanel) build.invoke(gui, sync);
        }

        private StringSyncValue value(String key) {
            return sync.findSyncHandler("apeiron_stocking_" + key, StringSyncValue.class);
        }
    }

    public static void verify() {
        try {
            for (int offset : new int[] { ApeironMachines.STORAGE_INPUT_BUS_OFFSET,
                ApeironMachines.STORAGE_INPUT_HATCH_OFFSET, ApeironMachines.STORAGE_INPUT_ASSEMBLY_OFFSET }) {
                Panel server = new Panel(offset, false), client = new Panel(offset, true);
                check(client.logic.getDisplayed(0) == null, "client fixture unexpectedly has ME stock");
                submit(server, client, "minimum", "1e40");
                submit(server, client, "maximum", "2e40");
                submit(server, client, "type_limit", "1");
                submit(server, client, "refresh", "20");
                check(
                    server.logic.getFilter()
                        .getMinimum()
                        .equals(BigInteger.TEN.pow(40))
                        && server.logic.getFilter()
                            .getMaximumText()
                            .equals("2e40")
                        && server.logic.getFilter()
                            .getTypeLimit() == 1
                        && server.logic.getFilter()
                            .getRefreshTicks() == 20,
                    "server ignored new filter edits");
                submit(server, client, "maximum", "");
                submit(server, client, "type_limit", "361");
                submit(server, client, "type_limit", "0");
                check(
                    server.logic.getFilter()
                        .getTypeLimit() == 1,
                    "server accepted invalid type limit");
                submit(server, client, "type_limit", "360");
                IntSyncValue priority = server.sync.findSyncHandler("apeiron_stocking_priority", IntSyncValue.class);
                sendPriority(priority, StockingFilter.Priority.LEAST.ordinal());
                check(
                    server.logic.getFilter()
                        .getPriority() == StockingFilter.Priority.LEAST,
                    "priority packet was ignored");
                sendPriority(priority, 99);
                check(
                    server.logic.getFilter()
                        .getPriority() == StockingFilter.Priority.LEAST,
                    "invalid priority packet changed selection");
                server.logic.begin();
                submit(server, client, "type_limit", "1");
                sendPriority(priority, StockingFilter.Priority.MOST.ordinal());
                check(
                    server.logic.getFilter()
                        .getTypeLimit() == 360
                        && server.logic.getFilter()
                            .getPriority() == StockingFilter.Priority.LEAST,
                    "filter edits changed a running recipe session");
                server.logic.end();
                ModularScreen screen = new ModularScreen("apeiron", client.panel);
                screen.getContext()
                    .setSettings(new UISettings());
                new GuiScreenWrapper(screen);
                screen.onResize(460, 270);
                check(
                    client.panel.getArea().width == 406 && client.panel.getArea().height == 246,
                    "settings panel lost its landscape size");
                List<IWidget> widgets = new ArrayList<>();
                collect(client.panel, widgets);
                int fields = 0;
                for (IWidget widget : widgets) if (widget instanceof TextFieldWidget) {
                    fields++;
                    check(
                        client.panel.getArea()
                            .contains(widget.getArea()),
                        "input field outside settings panel");
                }
                check(fields == 8 && client.panel.disablePanelsBelow(), "settings omitted filters or modal behavior");
                verifyStockPreview(client);
                screen.getPanelManager()
                    .closeAll();
                screen.getPanelManager()
                    .dispose();
            }
            Class<?> markClass = Class.forName(InfiniteStorageInputGui.class.getName() + "$StockingMarkSlot");
            java.lang.reflect.Constructor<?> constructor = markClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            PhantomItemSlot slot = (PhantomItemSlot) constructor.newInstance();
            check(
                !slot.onMouseScroll(UpOrDown.UP, 1) && !slot.onMouseScroll(UpOrDown.DOWN, 1),
                "mark slot consumed wheel input instead of letting the grid scroll");
            captureSettings();
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Stocking UI verification failed", error);
        }
        Apeiron.LOG.info(
            "Stocking UI verification passed: all three blocks, landscape layout, server/client filter packets, guarded edits, normal tooltips and NEI ingredient previews");
    }

    /** Optional native rendering for visual review in the isolated client profile. */
    private static void captureSettings() throws ReflectiveOperationException, java.io.IOException {
        String capture = System.getenv("APEIRON_STOCKING_UI_CAPTURE");
        if (capture == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.client.resources.LanguageManager languages = mc.getLanguageManager();
        net.minecraft.client.resources.Language previous = languages.getCurrentLanguage();
        try {
            for (String locale : new String[] { "en_US", "zh_CN" }) {
                for (net.minecraft.client.resources.Language language : languages.getLanguages())
                    if (language.getLanguageCode()
                        .equals(locale)) languages.setCurrentLanguage(language);
                languages.onResourceManagerReload(mc.getResourceManager());
                Panel panel = new Panel(ApeironMachines.STORAGE_INPUT_ASSEMBLY_OFFSET, true);
                ModularScreen screen = new ModularScreen("apeiron", panel.panel);
                screen.getContext()
                    .setSettings(new UISettings());
                new GuiScreenWrapper(screen);
                screen.onResize(460, 270);
                render(screen, new java.io.File(capture, "stocking-settings-" + locale + ".png"));
                screen.getPanelManager()
                    .closeAll();
                screen.getPanelManager()
                    .dispose();
            }
        } finally {
            languages.setCurrentLanguage(previous);
            languages.onResourceManagerReload(mc.getResourceManager());
        }
    }

    private static void render(ModularScreen screen, java.io.File output) throws java.io.IOException {
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glLoadIdentity();
        org.lwjgl.opengl.GL11.glOrtho(0, 460, 270, 0, -10000, 10000);
        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glLoadIdentity();
        net.minecraft.client.shader.Framebuffer framebuffer = new net.minecraft.client.shader.Framebuffer(
            920,
            540,
            true);
        try {
            framebuffer.bindFramebuffer(true);
            org.lwjgl.opengl.GL11.glClearColor(0.15f, 0.15f, 0.15f, 1);
            org.lwjgl.opengl.GL11
                .glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            screen.getContext()
                .updateState(-1000, -1000, 0);
            screen.onFrameUpdate();
            screen.drawScreen();
            java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(920 * 540 * 4);
            org.lwjgl.opengl.GL11.glReadPixels(
                0,
                0,
                920,
                540,
                org.lwjgl.opengl.GL11.GL_RGBA,
                org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,
                pixels);
            java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(
                920,
                540,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 540; y++) for (int x = 0; x < 920; x++) {
                int pixel = (y * 920 + x) * 4;
                result.setRGB(
                    x,
                    539 - y,
                    0xFF000000 | (pixels.get(pixel) & 255) << 16
                        | (pixels.get(pixel + 1) & 255) << 8
                        | pixels.get(pixel + 2) & 255);
            }
            output.getParentFile()
                .mkdirs();
            javax.imageio.ImageIO.write(result, "png", output);
        } finally {
            framebuffer.deleteFramebuffer();
            net.minecraft.client.Minecraft.getMinecraft()
                .getFramebuffer()
                .bindFramebuffer(true);
            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            org.lwjgl.opengl.GL11.glPopMatrix();
            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            org.lwjgl.opengl.GL11.glPopMatrix();
            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }

    private static void verifyStockPreview(Panel panel) throws ReflectiveOperationException {
        ItemStack diamond = new ItemStack(Items.diamond);
        diamond.setStackDisplayName("Stocking tooltip marker");
        IAEStack<?>[] stacks = { BigAEStackValues.copyWithSize(AEItemStack.create(diamond), BigInteger.TEN.pow(60)),
            BigAEStackValues
                .copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), BigInteger.TEN.pow(70)) };
        NBTTagList cells = new NBTTagList();
        for (int i = 0; i < stacks.length; i++) {
            NBTTagCompound cell = new NBTTagCompound();
            cell.setInteger("slot", i);
            cell.setTag("available", BigPatternStackCodec.write(stacks[i]));
            cells.appendTag(cell);
        }
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setTag("slots", cells);
        Method read = InfiniteStorageInputGui.class.getDeclaredMethod("readSnapshot", NBTTagCompound.class);
        read.setAccessible(true);
        read.invoke(panel.gui, snapshot);
        Method stock = InfiniteStorageInputGui.class.getDeclaredMethod("stockWidget", int.class);
        stock.setAccessible(true);
        for (int index = 0; index < stacks.length; index++) {
            ParentWidget<?> cell = (ParentWidget<?>) stock.invoke(panel.gui, index);
            IWidget top = cell.getChildren()
                .get(
                    cell.getChildren()
                        .size() - 1);
            check(
                top instanceof RecipeViewerIngredientProvider && !(top instanceof PhantomItemSlot),
                "stock overlay hides NEI lookup or permits inventory transfers");
            ItemStack ingredient = ((RecipeViewerIngredientProvider) top).getStackForRecipeViewer();
            check(
                ingredient != null && ingredient.stackSize == 1
                    && (index != 0 || ingredient.getDisplayName()
                        .equals("Stocking tooltip marker")),
                "preview item lost its NBT or leaked a truncated amount");
            ((Widget<?>) top).getTooltip()
                .buildTooltip();
            check(
                !((Widget<?>) top).getTooltip()
                    .isEmpty(),
                "stock tooltip is empty");
        }
    }

    private static void submit(Panel server, Panel client, String key, String text) throws java.io.IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new StringSyncValue(() -> text).write(packet);
            server.value(key)
                .readOnServer(0, packet);
            packet.clear();
            server.value(key)
                .updateCacheFromSource(false);
            server.value(key)
                .write(packet);
            client.value(key)
                .read(packet);
            check(
                client.value(key)
                    .getStringValue()
                    .equals(
                        server.value(key)
                            .getStringValue()),
                "filter response did not synchronize to the client");
        } finally {
            packet.release();
        }
    }

    private static void sendPriority(IntSyncValue server, int value) throws java.io.IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new IntSyncValue(() -> value).write(packet);
            server.readOnServer(0, packet);
        } finally {
            packet.release();
        }
    }

    private static void collect(IWidget root, List<IWidget> result) {
        result.add(root);
        for (IWidget child : root.getChildren()) collect(child, result);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Stocking UI: " + message);
    }
}
