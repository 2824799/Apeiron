package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.config.CraftingSortOrder;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.core.AEConfig;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

/** Exercises the transformed confirmation GUI through real updates and sort button actions. */
@SuppressWarnings("unchecked")
public final class AECraftingConfirmationSortSmoke {

    private AECraftingConfirmationSortSmoke() {}

    private static final class Confirmation extends GuiCraftConfirm {

        private Confirmation() throws ReflectiveOperationException {
            super(new InventoryPlayer(null), host());
            mc = Minecraft.getMinecraft();
            fontRendererObj = mc.fontRenderer;
            width = 640;
            height = 480;
            // Mod-loading verification has no player for GuiContainer.initGui. Restore its sort
            // settings and create the controls used by postUpdate/actionPerformed directly.
            CraftingSortOrder mode = (CraftingSortOrder) AEConfig.instance.settings
                .getSetting(Settings.CRAFTING_SORT_BY);
            SortDir direction = (SortDir) AEConfig.instance.settings.getSetting(Settings.SORT_DIRECTION);
            field("sortMode").set(this, mode);
            field("sortDir").set(this, direction);
            field("sortingModeButton").set(this, new GuiImgButton(0, 0, Settings.CRAFTING_SORT_BY, mode));
            field("sortingDirectionButton").set(this, new GuiImgButton(0, 0, Settings.SORT_DIRECTION, direction));
            field("searchField").set(this, new MEGuiTextField(76, 12, "Search"));
        }

        private void click(String button) throws ReflectiveOperationException {
            actionPerformed((GuiButton) field(button).get(this));
        }
    }

    private static ITerminalHost host() {
        return (ITerminalHost) Proxy.newProxyInstance(
            AECraftingConfirmationSortSmoke.class.getClassLoader(),
            new Class<?>[] { ITerminalHost.class, IGuiItemObject.class },
            (proxy, method, args) -> null);
    }

    public static void verify() {
        final Enum<?> oldMode = AEConfig.instance.settings.getSetting(Settings.CRAFTING_SORT_BY);
        final Enum<?> oldDirection = AEConfig.instance.settings.getSetting(Settings.SORT_DIRECTION);
        try {
            for (BigInteger count : new BigInteger[] { BigInteger.ONE, BigInteger.TEN.pow(19),
                BigInteger.TEN.pow(60) }) {
                AEConfig.instance.settings.putSetting(Settings.CRAFTING_SORT_BY, CraftingSortOrder.AMOUNT);
                AEConfig.instance.settings.putSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
                Confirmation gui = new Confirmation();
                IAEStack<?> stored = sized(AEItemStack.create(new ItemStack(Items.diamond)), count);
                IAEStack<?> storedFluid = sized(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), count);
                IAEStack<?> crafted = sized(AEItemStack.create(new ItemStack(Items.emerald)), count);
                IAEStack<?> craftedMore = sized(
                    AEItemStack.create(new ItemStack(Items.gold_ingot)),
                    count.add(BigInteger.ONE));
                IAEStack<?> craftedFluid = sized(AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)), count);
                IAEStack<?> absent = sized(AEItemStack.create(new ItemStack(Items.iron_ingot)), count);
                ((BigAERequestableStack) crafted).setCountRequestableCraftsBig(count);
                ((BigAERequestableStack) craftedMore).setCountRequestableCraftsBig(count.add(BigInteger.ONE));
                ((BigAERequestableStack) craftedFluid).setCountRequestableCraftsBig(count);
                gui.postUpdate(Arrays.asList(stored, storedFluid), (byte) 0);
                gui.postUpdate(Arrays.asList(crafted, craftedMore, craftedFluid), (byte) 1);
                gui.postUpdate(Arrays.asList(absent), (byte) 2);
                gui.click("sortingModeButton");
                check(field("sortMode").get(gui) == CraftingSortOrder.CRAFTS, "amount button did not select crafts");
                verifyCrafts(gui, stored, crafted, craftedMore, absent);
                check(
                    AEConfig.instance.settings.getSetting(Settings.CRAFTING_SORT_BY) == CraftingSortOrder.CRAFTS,
                    "crafts selection was not persisted");

                // Reopening with CRAFTS must accept the first storage packet before pending arrives.
                gui = new Confirmation();
                gui.postUpdate(Arrays.asList(stored, storedFluid), (byte) 0);
                gui.postUpdate(Arrays.asList(crafted, craftedMore, craftedFluid), (byte) 1);
                gui.postUpdate(Arrays.asList(absent), (byte) 2);
                verifyCrafts(gui, stored, crafted, craftedMore, absent);
                for (int mode = 0; mode < CraftingSortOrder.values().length; mode++) {
                    for (int direction = 0; direction < SortDir.values().length; direction++) {
                        verifyOrder(gui, absent);
                        if (field("sortMode").get(gui) == CraftingSortOrder.CRAFTS)
                            verifyCrafts(gui, stored, crafted, craftedMore, absent);
                        gui.click("sortingDirectionButton");
                    }
                    gui.click("sortingModeButton");
                }
                // A later update that removes pending materials must also remain safe.
                gui.postUpdate(Arrays.asList(sized(crafted.copy(), BigInteger.ZERO)), (byte) 1);
                verifyOrder(gui, absent);
                check(
                    visual(gui).stream()
                        .noneMatch(stack -> stack.isSameType(crafted)),
                    "zero update kept a visual entry");
            }
            Apeiron.LOG.info(
                "Crafting confirmation sorting verification passed: all modes/directions, mixed items/fluids, missing entries, saved CRAFTS reopen and exact counts");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Crafting confirmation sorting verification failed", error);
        } finally {
            AEConfig.instance.settings.putSetting(Settings.CRAFTING_SORT_BY, oldMode);
            AEConfig.instance.settings.putSetting(Settings.SORT_DIRECTION, oldDirection);
        }
    }

    private static IAEStack<?> sized(IAEStack<?> stack, BigInteger count) {
        return BigAEStackValues.set(stack, count);
    }

    private static void verifyCrafts(Confirmation gui, IAEStack<?> stored, IAEStack<?> crafted, IAEStack<?> craftedMore,
        IAEStack<?> absent) throws ReflectiveOperationException {
        verifyOrder(gui, absent);
        Comparator<IAEStack<?>> comparator = (Comparator<IAEStack<?>>) field("comparator").get(gui);
        int direction = ((SortDir) field("sortDir").get(gui)).sortHint;
        check(comparator.compare(stored, crafted) * direction < 0, "storage-only entry did not have zero crafts");
        check(comparator.compare(crafted, craftedMore) * direction < 0, "craft counts were truncated or tied");
    }

    private static void verifyOrder(Confirmation gui, IAEStack<?> absent) throws ReflectiveOperationException {
        List<IAEStack<?>> visual = visual(gui);
        check(
            visual.get(0)
                .isSameType(absent),
            "missing materials were not first");
        Comparator<IAEStack<?>> comparator = (Comparator<IAEStack<?>>) field("comparator").get(gui);
        for (int left = 0; left < visual.size(); left++) {
            for (int right = left; right < visual.size(); right++) {
                int forward = comparator.compare(visual.get(left), visual.get(right));
                int backward = comparator.compare(visual.get(right), visual.get(left));
                check(forward <= 0 && Integer.signum(forward) == -Integer.signum(backward), "invalid sorted order");
            }
        }
    }

    private static List<IAEStack<?>> visual(Confirmation gui) throws ReflectiveOperationException {
        return (List<IAEStack<?>>) field("visual").get(gui);
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = GuiCraftConfirm.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
