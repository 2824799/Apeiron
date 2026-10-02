package com.silvia.apeiron.common.integration.aeinfinitycell;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEStack;

/** Shared exact-copy and NBT extensions for the cell's additional AE stack types. */
public final class InfinityStackCounts {

    private InfinityStackCounts() {}

    public static void copy(final IAEStack<?> source, final IAEStack<?> target) {
        BigAEStackValues.set(target, BigAEStackValues.get(source));
        if (target instanceof BigAERequestableStack) {
            ((BigAERequestableStack) target).setCountRequestableBig(BigAEStackValues.getCountRequestable(source));
            ((BigAERequestableStack) target)
                .setCountRequestableCraftsBig(BigAEStackValues.getCountRequestableCrafts(source));
        }
    }

    public static void write(final IAEStack<?> stack, final NBTTagCompound tag, final String countKey) {
        BigValueCodec.writeNBT(tag, countKey, "ApeironCnt", new AdaptiveInteger(BigAEStackValues.get(stack)));
        if (stack instanceof BigAERequestableStack) {
            BigValueCodec
                .writeNBT(tag, "Req", "ApeironReq", new AdaptiveInteger(BigAEStackValues.getCountRequestable(stack)));
            BigValueCodec.writeNBT(
                tag,
                "ReqMade",
                "ApeironReqMade",
                new AdaptiveInteger(BigAEStackValues.getCountRequestableCrafts(stack)));
        }
    }

    public static void read(final IAEStack<?> stack, final NBTTagCompound tag, final String countKey) {
        if (stack == null) return;
        if (tag.hasKey("ApeironCnt")) BigAEStackValues.set(
            stack,
            BigValueCodec.readNBT(tag, countKey, "ApeironCnt")
                .toBigInteger());
        if (stack instanceof BigAERequestableStack) {
            if (tag.hasKey("ApeironReq")) ((BigAERequestableStack) stack).setCountRequestableBig(
                BigValueCodec.readNBT(tag, "Req", "ApeironReq")
                    .toBigInteger());
            if (tag.hasKey("ApeironReqMade")) ((BigAERequestableStack) stack).setCountRequestableCraftsBig(
                BigValueCodec.readNBT(tag, "ReqMade", "ApeironReqMade")
                    .toBigInteger());
        }
    }
}
