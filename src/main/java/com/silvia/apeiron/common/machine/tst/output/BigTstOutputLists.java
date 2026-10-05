package com.silvia.apeiron.common.machine.tst.output;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.FluidStackLong;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.GTCM_MultiMachineBase.ItemStackLong;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.output.BigOutputAmount;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import gregtech.api.util.GTUtility;

/** Keeps TST's native entry types while making every merge and serialized count exact. */
public final class BigTstOutputLists {

    private BigTstOutputLists() {}

    public static BigInteger amount(Object entry, long fallback) {
        return entry instanceof BigOutputAmount ? ((BigOutputAmount) entry).getOutputAmountBig()
            : BigInteger.valueOf(fallback);
    }

    public static BigInteger amount(ItemStackLong entry) {
        return amount(entry, entry.stackSize());
    }

    public static BigInteger amount(FluidStackLong entry) {
        return amount(entry, entry.amount());
    }

    public static void set(Object entry, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative output amount");
        ((BigOutputAmount) entry).setOutputAmountBig(amount);
    }

    public static void mergeItem(List<ItemStackLong> outputs, ItemStack type, BigInteger count) {
        if (count.signum() < 0) throw new IllegalArgumentException("Negative item output");
        if (type == null || count.signum() == 0) return;
        for (int i = 0; i < outputs.size(); i++) {
            final ItemStackLong entry = outputs.get(i);
            if (GTUtility.areStacksEqual(entry.itemStack(), type)) {
                final BigInteger merged = amount(entry).add(count);
                outputs.set(i, item(entry.itemStack(), merged));
                return;
            }
        }
        outputs.add(item(type, count));
    }

    public static void mergeFluid(List<FluidStackLong> outputs, FluidStack type, BigInteger count) {
        if (count.signum() < 0) throw new IllegalArgumentException("Negative fluid output");
        if (type == null || count.signum() == 0) return;
        for (int i = 0; i < outputs.size(); i++) {
            final FluidStackLong entry = outputs.get(i);
            if (entry.fluidStack()
                .isFluidEqual(type)) {
                outputs.set(i, fluid(entry.fluidStack(), amount(entry).add(count)));
                return;
            }
        }
        outputs.add(fluid(type, count));
    }

    private static ItemStackLong item(ItemStack type, BigInteger count) {
        final ItemStack identity = type.copy();
        identity.stackSize = 1;
        final ItemStackLong entry = new ItemStackLong(identity, BigAEStackValues.saturatedLong(count));
        set(entry, count);
        return entry;
    }

    private static FluidStackLong fluid(FluidStack type, BigInteger count) {
        final FluidStack identity = type.copy();
        identity.amount = 1;
        final FluidStackLong entry = new FluidStackLong(identity, BigAEStackValues.saturatedLong(count));
        set(entry, count);
        return entry;
    }

    public static NBTTagList saveItems(List<ItemStackLong> entries) {
        final NBTTagList result = new NBTTagList();
        for (ItemStackLong entry : entries) {
            if (entry.itemStack() == null || amount(entry).signum() == 0) continue;
            final NBTTagCompound tag = save(amount(entry));
            tag.setTag("Type", GTUtility.saveItem(GTUtility.copyAmountUnsafe(1, entry.itemStack())));
            result.appendTag(tag);
        }
        return result;
    }

    public static NBTTagList saveFluids(List<FluidStackLong> entries) {
        final NBTTagList result = new NBTTagList();
        for (FluidStackLong entry : entries) {
            if (entry.fluidStack() == null || amount(entry).signum() == 0) continue;
            final NBTTagCompound tag = save(amount(entry));
            final NBTTagCompound type = new NBTTagCompound();
            final FluidStack identity = entry.fluidStack()
                .copy();
            identity.amount = 1;
            identity.writeToNBT(type);
            tag.setTag("Type", type);
            result.appendTag(tag);
        }
        return result;
    }

    private static NBTTagCompound save(BigInteger count) {
        final NBTTagCompound tag = new NBTTagCompound();
        BigValueCodec.writeNBT(tag, "Count", "ExactCount", new AdaptiveInteger(count));
        return tag;
    }

    public static void restore(List<?> entries, NBTTagList counts) {
        if (entries.size() != counts.tagCount()) return;
        for (int i = 0; i < entries.size(); i++) {
            final BigInteger amount = BigValueCodec.readNBT(counts.getCompoundTagAt(i), "Count", "ExactCount")
                .toBigInteger();
            set(entries.get(i), amount);
        }
    }

    public static void restoreItems(List<ItemStackLong> entries, NBTTagList counts) {
        if (counts.tagCount() > 0 && counts.getCompoundTagAt(0)
            .hasKey("Type")) {
            entries.clear();
            for (int i = 0; i < counts.tagCount(); i++) {
                final NBTTagCompound tag = counts.getCompoundTagAt(i);
                final ItemStack type = GTUtility.loadItem(tag.getCompoundTag("Type"));
                final BigInteger count = BigValueCodec.readNBT(tag, "Count", "ExactCount")
                    .toBigInteger();
                mergeItem(entries, type, count);
            }
        } else restore(entries, counts);
    }

    public static void restoreFluids(List<FluidStackLong> entries, NBTTagList counts) {
        if (counts.tagCount() > 0 && counts.getCompoundTagAt(0)
            .hasKey("Type")) {
            entries.clear();
            for (int i = 0; i < counts.tagCount(); i++) {
                final NBTTagCompound tag = counts.getCompoundTagAt(i);
                final FluidStack type = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("Type"));
                final BigInteger count = BigValueCodec.readNBT(tag, "Count", "ExactCount")
                    .toBigInteger();
                mergeFluid(entries, type, count);
            }
        } else restore(entries, counts);
    }
}
