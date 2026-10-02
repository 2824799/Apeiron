package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.util.PatternMultiplierHelper;

/** Keeps pattern optimizer limits and NBT multiplications exact for Apeiron quantities. */
@Mixin(value = PatternMultiplierHelper.class, remap = false)
public abstract class PatternMultiplierHelperBigMixin {

    @Overwrite
    public static int getMaxBitMultiplier(final ICraftingPatternDetails details) {
        int max = 62;
        for (final IAEStack<?> stack : details.getAEInputs()) max = Math.min(max, apeiron$multiplierLimit(stack));
        for (final IAEStack<?> stack : details.getAEOutputs()) max = Math.min(max, apeiron$multiplierLimit(stack));
        return max;
    }

    @Overwrite
    public static int getMaxBitDivider(final ICraftingPatternDetails details) {
        int max = 62;
        for (final IAEStack<?> stack : details.getAEInputs()) max = Math.min(max, apeiron$dividerLimit(stack));
        for (final IAEStack<?> stack : details.getAEOutputs()) max = Math.min(max, apeiron$dividerLimit(stack));
        return max;
    }

    @Overwrite
    public static void applyModification(final ItemStack stack, int bitMultiplier) {
        if (bitMultiplier == 0 || stack == null || stack.stackTagCompound == null) return;
        final boolean dividing = bitMultiplier < 0;
        if (dividing) bitMultiplier = -bitMultiplier;
        final NBTTagCompound encoded = stack.stackTagCompound;
        apeiron$modifyList(encoded.getTagList("in", NBT.TAG_COMPOUND), dividing, bitMultiplier);
        apeiron$modifyList(encoded.getTagList("out", NBT.TAG_COMPOUND), dividing, bitMultiplier);
    }

    private static int apeiron$multiplierLimit(final IAEStack<?> stack) {
        final BigInteger value = BigAEStackValues.get(stack);
        if (value.signum() <= 0) return 62;
        return Math.max(0, 62 - (value.bitLength() - 1));
    }

    private static int apeiron$dividerLimit(final IAEStack<?> stack) {
        final BigInteger value = BigAEStackValues.get(stack);
        if (value.signum() <= 0) return 62;
        final int lowest = value.getLowestSetBit();
        return Math.min(62, lowest < 0 ? 62 : lowest);
    }

    private static void apeiron$modifyList(final NBTTagList list, final boolean dividing, final int shift) {
        for (int index = 0; index < list.tagCount(); index++) {
            final NBTTagCompound tag = list.getCompoundTagAt(index);
            if (tag.hasNoTags()) continue;
            if (tag.hasKey("Cnt", NBT.TAG_LONG) || tag.hasKey("ApeironCnt", NBT.TAG_BYTE_ARRAY)) {
                final BigInteger value = BigValueCodec.readNBT(tag, "Cnt", "ApeironCnt")
                    .toBigInteger();
                final BigInteger modified = dividing ? value.shiftRight(shift) : value.shiftLeft(shift);
                BigValueCodec.writeNBT(tag, "Cnt", "ApeironCnt", new AdaptiveInteger(modified));
            }
            if (tag.hasKey("Count")) {
                final int value = tag.getInteger("Count");
                tag.setInteger("Count", dividing ? value >> shift : value << shift);
            }
        }
    }
}
