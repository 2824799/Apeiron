package com.silvia.apeiron.ae.stack;

import java.io.IOException;

import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEStack;
import io.netty.buffer.ByteBuf;

/** Generic exact stack-size packet suffix for non-item AE stacks. */
public final class BigAEStackPackets {

    public static final int PACKET_MAGIC = 0x41504753;

    private BigAEStackPackets() {}

    public static void write(final ByteBuf out, final IAEStack<?> stack) {
        int flags = 0;
        if (stack instanceof BigAEStack && ((BigAEStack) stack).isStackSizeBig()) flags |= 1;
        if (stack instanceof BigAERequestableStack && ((BigAERequestableStack) stack).isCountRequestableBig()) flags |= 2;
        if (stack instanceof BigAERequestableStack
            && ((BigAERequestableStack) stack).isCountRequestableCraftsBig()) flags |= 4;
        if (flags == 0) return;
        out.writeInt(PACKET_MAGIC);
        out.writeByte(flags);
        if ((flags & 1) != 0) BigValueCodec.writePacket(out, ((BigAEStack) stack).getStackSizeBig());
        if ((flags & 2) != 0) BigValueCodec.writePacket(out, ((BigAERequestableStack) stack).getCountRequestableBig());
        if ((flags & 4) != 0) {
            BigValueCodec.writePacket(out, ((BigAERequestableStack) stack).getCountRequestableCraftsBig());
        }
    }

    public static void read(final ByteBuf in, final IAEStack<?> stack) throws IOException {
        if (!(stack instanceof BigAEStack) || in.readableBytes() < Integer.BYTES + 1) return;
        if (in.getInt(in.readerIndex()) != PACKET_MAGIC) return;
        in.skipBytes(Integer.BYTES);
        final int flags = in.readUnsignedByte();
        if ((flags & ~7) != 0 || flags == 0) throw new IOException("invalid Apeiron generic AE stack flags");
        if ((flags & 1) != 0 && stack instanceof BigAEStack) {
            ((BigAEStack) stack).setStackSizeBig(BigValueCodec.readPacket(in));
        }
        if ((flags & 2) != 0 && stack instanceof BigAERequestableStack) {
            ((BigAERequestableStack) stack).setCountRequestableBig(BigValueCodec.readPacket(in));
        }
        if ((flags & 4) != 0 && stack instanceof BigAERequestableStack) {
            ((BigAERequestableStack) stack).setCountRequestableCraftsBig(BigValueCodec.readPacket(in));
        }
    }
}
