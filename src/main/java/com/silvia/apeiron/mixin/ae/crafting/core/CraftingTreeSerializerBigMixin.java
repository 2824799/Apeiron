package com.silvia.apeiron.mixin.ae.crafting.core;

import java.io.IOException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.CraftingTreeSerializer;
import io.netty.buffer.ByteBuf;

/** AE's object table deduplicates stack types; each tree occurrence needs its own exact size. */
@Mixin(value = CraftingTreeSerializer.class, remap = false)
public abstract class CraftingTreeSerializerBigMixin {

    @Shadow
    public abstract void writeStack(IAEStack<?> stack);

    @Shadow
    public abstract IAEStack<?> readStack();

    @Shadow
    public abstract ByteBuf getBuffer();

    @Overwrite
    public void writeStackWithSize(IAEStack<?> stack) {
        writeStack(stack);
        getBuffer().writeLong(stack.getStackSize());
        BigValueCodec.writePacket(getBuffer(), BigAEStackValues.get(stack));
    }

    @Overwrite
    public IAEStack<?> readStackWithSize() {
        final IAEStack<?> type = readStack();
        getBuffer().readLong();
        try {
            return BigAEStackValues.copyWithSize(type, BigValueCodec.readPacket(getBuffer()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid exact crafting tree amount", e);
        }
    }
}
