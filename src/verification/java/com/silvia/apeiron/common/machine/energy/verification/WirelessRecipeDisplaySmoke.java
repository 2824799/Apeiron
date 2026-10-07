package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.client.gui.machine.energy.WirelessRecipeWidgets;
import com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.RecipeDisplayNumbers;

import appeng.api.storage.data.IAEStack;
import io.netty.buffer.Unpooled;

public final class WirelessRecipeDisplaySmoke {

    private WirelessRecipeDisplaySmoke() {}

    public static void verify(WirelessRecipeState active) {
        try {
            check(
                !active.getOutputDisplay()
                    .isEmpty(),
                "active machine UI has no outputs");
            final BigInteger huge = BigInteger.TEN.pow(600)
                .add(BigInteger.valueOf(17));
            final BigMachineOutputQueue ledger = new BigMachineOutputQueue();
            ledger.addItem(new ItemStack(Items.emerald), huge);
            ledger.addFluid(new FluidStack(FluidRegistry.WATER, 1), huge.multiply(BigInteger.valueOf(2)));
            final WirelessRecipeState state = new WirelessRecipeState();
            final NBTTagCompound idle = new NBTTagCompound();
            WirelessWailaDisplay.write(idle, state, 10000);
            check(
                idle.getString("ApeironParallelSetting")
                    .equals(Integer.toString(Integer.MAX_VALUE))
                    && idle.getString("ApeironRunningParallels")
                        .equals("0")
                    && idle.getString("ApeironWirelessEUt")
                        .equals("0"),
                "idle default HUD is not INT MAX / zero");
            state.setParallelSettingBig(BigInteger.valueOf(2000));
            state.start(huge, huge.multiply(BigInteger.valueOf(8)), 40, ledger);
            final NBTTagCompound snapshot = state.writeDisplayNBT();
            final PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            final NBTTagCompound received;
            try {
                ByteBufAdapters.NBT.serialize(packet, snapshot);
                received = ByteBufAdapters.NBT.deserialize(packet);
                check(snapshot.equals(received), "GUI display packet lost exact output amounts");
            } finally {
                packet.release();
            }
            final BigMachineOutputQueue preview = new BigMachineOutputQueue();
            preview.load(received);
            final List<IAEStack<?>> outputs = preview.snapshotOutputs();
            check(outputs.size() == 2, "GUI dropped item or fluid output");
            for (IAEStack<?> output : outputs) {
                final BigInteger amount = BigAEStackValues.get(output);
                check(
                    WirelessRecipeWidgets.tooltip(output, 40)
                        .contains(BigNumberFormatter.formatExact(amount)),
                    "GUI tooltip truncated output");
                check(
                    WirelessRecipeWidgets.amountLine(output, 40)
                        .contains(RecipeDisplayNumbers.rate(amount, 40, 20)),
                    "GUI output rate was truncated");
                output.reset();
            }
            check(
                state.writeDisplayNBT()
                    .equals(snapshot),
                "display modified active output ledger");
            WirelessRecipeWidgets.modern(preview.snapshotOutputs(), () -> 40);
            WirelessRecipeWidgets.legacy(preview.snapshotOutputs(), () -> 40);
            final NBTTagCompound waila = new NBTTagCompound();
            WirelessWailaDisplay.write(waila, state, 9000);
            final BigInteger actual = RecipeDisplayNumbers.effectiveEUt(state.getEUtBig(), 9000);
            check(
                new BigInteger(waila.getString("ApeironActualWirelessEUt")).equals(actual),
                "Waila differs from actual debit");
            List<String> tip = WirelessWailaDisplay
                .updateOmni(Arrays.asList("§d功耗总额 1 EU", "§d§k6§r§d机器功率 1 EU/t", "其他信息", "MAX-Tier 0.8 A MAX"), waila);
            check(
                tip.get(0)
                    .contains(BigNumberFormatter.formatCompact(actual.multiply(BigInteger.valueOf(40))))
                    && tip.get(1)
                        .contains(BigNumberFormatter.formatCompact(actual))
                    && tip.get(2)
                        .equals("其他信息"),
                "OmniOcular upper power rows were capped");
            check(
                tip.get(3)
                    .contains(RecipeDisplayNumbers.rate(actual, Integer.MAX_VALUE, 1)),
                "OmniOcular current used one native parallel");
            List<String> nativeLimit = java.util.Collections.singletonList("§b并行限制 §e12");
            check(
                WirelessWailaDisplay.plainText(
                    WirelessWailaDisplay.updateOmni(nativeLimit, waila)
                        .get(0))
                    .contains(BigNumberFormatter.formatCompact(BigInteger.valueOf(2000))),
                "OmniOcular kept the native parallel limit on a wireless machine");
            final List<String> nativeTip = new ArrayList<>();
            waila.setLong("energyUsage", 8);
            nativeTip.add(
                net.minecraft.util.StatCollector.translateToLocalFormatted(
                    "GT5U.waila.energy.use",
                    com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber(8),
                    gregtech.api.util.GTUtility.getColoredTierNameFromVoltage(8)));
            WirelessWailaDisplay.updateNative(nativeTip, waila);
            check(
                nativeTip.get(0)
                    .contains(BigNumberFormatter.formatCompact(actual)),
                "native Waila power was not replaced");
            check(
                nativeTip.stream()
                    .anyMatch(
                        line -> WirelessWailaDisplay.plainText(line)
                            .contains(BigNumberFormatter.formatCompact(huge))),
                "native Waila outputs missing");
            state.complete();
            WirelessWailaDisplay.write(waila, state, 9000);
            check(
                waila.getString("ApeironRunningParallels")
                    .equals("0")
                    && waila.getString("ApeironWirelessEUt")
                        .equals("0")
                    && !waila.hasKey("ApeironActualWirelessEUt")
                    && !waila.hasKey("ApeironWirelessOutputRows"),
                "completed recipe retained active HUD numbers or products");
            check(
                WirelessWailaDisplay.plainText(
                    WirelessWailaDisplay.updateOmni(nativeLimit, waila)
                        .get(0))
                    .contains(BigNumberFormatter.formatCompact(BigInteger.valueOf(2000))),
                "idle wireless HUD reverted to the native limit");
            state.setParallelSettingBig(BigInteger.ZERO);
            WirelessWailaDisplay.write(waila, state, 9000);
            check(
                waila.getString("ApeironParallelSetting")
                    .equals("∞"),
                "HUD overwrote explicit unlimited setting");
            check(
                state.getOutputDisplay()
                    .isEmpty()
                    && !state.writeDisplayNBT()
                        .getBoolean("running"),
                "completed recipe stayed in active display");
            check(
                state.pending()
                    .getItemAmountBig()
                    .equals(huge)
                    && state.pending()
                        .getFluidAmountBig()
                        .equals(huge.multiply(BigInteger.valueOf(2))),
                "display or completion lost output");
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Wireless display verification failed", error);
        }
        Apeiron.LOG.info(
            "Wireless recipe display: exact item/fluid output snapshots, GUI codecs, rates and Waila energy verification passed");
    }

    public static void verifyController(gregtech.api.metatileentity.implementations.MTEMultiBlockBase machine) {
        com.silvia.apeiron.api.machine.parallel.BigWirelessController controller = (com.silvia.apeiron.api.machine.parallel.BigWirelessController) machine;
        WirelessRecipeState state = controller.getWirelessRecipeState();
        NBTTagCompound tag = new NBTTagCompound();
        machine.getWailaNBTData(detachedViewer(), null, tag, null, 0, 0, 0);
        check(
            tag.getString("ApeironRunningParallels")
                .equals(
                    BigNumberFormatter.formatCompact(state.isRunning() ? state.getParallelsBig() : BigInteger.ZERO)),
            "transformed HUD retained stale parallels");
        check(
            controller.getCurrentParallelsBig()
                .equals(state.isRunning() ? state.getParallelsBig() : BigInteger.ZERO),
            "controller reports completed parallels as current");
        if (!state.isRunning()) check(
            controller.getRecipeEUtBig()
                .signum() == 0
                && controller.getRecipeTotalEUBig()
                    .signum() == 0,
            "idle controller reports completed energy as current");
        List<String> lines = new ArrayList<>();
        String oldLimit = net.minecraft.util.StatCollector
            .translateToLocalFormatted("GT5U.multiblock.parallelism_override", 12, 60);
        tag.setInteger("powerPanelMaxParallel", 12);
        tag.setInteger("maxParallelRecipes", 60);
        lines.add(oldLimit);
        WirelessWailaDisplay.updateNative(lines, tag);
        check(
            !lines.contains(oldLimit) && lines.stream()
                .anyMatch(line -> line.contains(tag.getString("ApeironParallelSetting"))),
            "native HUD retained contradictory parallel limit");
    }

    private static net.minecraft.entity.player.EntityPlayerMP detachedViewer() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field singleton = unsafeClass.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            net.minecraft.entity.player.EntityPlayerMP viewer = (net.minecraft.entity.player.EntityPlayerMP) unsafeClass
                .getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), net.minecraft.entity.player.EntityPlayerMP.class);
            java.lang.reflect.Field id = net.minecraft.entity.Entity.class.getDeclaredField("entityUniqueID");
            id.setAccessible(true);
            id.set(viewer, java.util.UUID.fromString("01000000-0000-0000-0000-000000000000"));
            return viewer;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot create detached Waila viewer", error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
