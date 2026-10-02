package com.silvia.apeiron.common.machine.me.input.storage;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

/** The channel and exact amount are explicit, independently of AE's legacy serialized count. */
public final class BigPatternStackCodec {

    private BigPatternStackCodec() {}

    public static NBTTagCompound write(IAEStack<?> stack) {
        NBTTagCompound tag = new NBTTagCompound();
        stack.writeToNBT(tag);
        tag.setBoolean("fluid", stack instanceof IAEFluidStack);
        BigValueCodec.writeNBT(
            tag,
            "ApeironInputAmount",
            "ApeironInputAmountBig",
            new AdaptiveInteger(BigAEStackValues.get(stack)));
        return tag;
    }

    public static IAEStack<?> read(NBTTagCompound tag) {
        IAEStack<?> stack = tag.getBoolean("fluid") ? AEFluidStack.loadFluidStackFromNBT(tag)
            : AEItemStack.loadItemStackFromNBT(tag);
        if (stack == null) return null;
        BigAEStackValues.set(
            stack,
            BigValueCodec.readNBT(tag, "ApeironInputAmount", "ApeironInputAmountBig")
                .toBigInteger());
        return stack;
    }
}
