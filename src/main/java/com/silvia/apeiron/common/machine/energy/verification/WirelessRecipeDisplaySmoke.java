package com.silvia.apeiron.common.machine.energy.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
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
                        line -> EnumChatFormatting.getTextWithoutFormattingCodes(line)
                            .contains(BigNumberFormatter.formatCompact(huge))),
                "native Waila outputs missing");
            state.complete();
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
