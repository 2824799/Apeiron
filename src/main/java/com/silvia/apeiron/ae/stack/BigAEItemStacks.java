package com.silvia.apeiron.ae.stack;

import java.io.IOException;
import java.math.BigInteger;

import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;

/** Access exact values through IAEItemStack and read the extended packet format. */
public final class BigAEItemStacks {

    /** Optional marker appended to AE's normal item-stack packet format. */
    public static final int PACKET_MAGIC = 0x41504952;

    private static final int STACK_SIZE_FLAG = 1;
    private static final int REQUESTABLE_FLAG = 1 << 1;
    private static final int REQUESTABLE_CRAFTS_FLAG = 1 << 2;

    private BigAEItemStacks() {}

    public static boolean isStackSizeBig(IAEItemStack stack) {
        return stack instanceof BigAEItemStack && ((BigAEItemStack) stack).isStackSizeBig();
    }

    public static IAEItemStack copyWithSize(IAEItemStack stack, BigInteger count) {
        IAEItemStack copy = stack.copy();
        if (copy instanceof BigAEItemStack) ((BigAEItemStack) copy).setStackSizeBig(count);
        else copy.setStackSize(count.longValueExact());
        return copy;
    }

    public static BigInteger stackSize(IAEItemStack stack) {
        return stack instanceof BigAEItemStack ? ((BigAEItemStack) stack).getStackSizeBig()
            : BigInteger.valueOf(stack.getStackSize());
    }

    public static BigInteger countRequestable(IAEItemStack stack) {
        return stack instanceof BigAEItemStack ? ((BigAEItemStack) stack).getCountRequestableBig()
            : BigInteger.valueOf(stack.getCountRequestable());
    }

    public static BigInteger countRequestableCrafts(IAEItemStack stack) {
        return stack instanceof BigAEItemStack ? ((BigAEItemStack) stack).getCountRequestableCraftsBig()
            : BigInteger.valueOf(stack.getCountRequestableCrafts());
    }

    public static void writePacketExtension(ByteBuf out, IAEItemStack stack) {
        if (!(stack instanceof BigAEItemStack)) return;

        BigAEItemStack exact = (BigAEItemStack) stack;
        int flags = 0;
        if (exact.isStackSizeBig()) flags |= STACK_SIZE_FLAG;
        if (exact.isCountRequestableBig()) flags |= REQUESTABLE_FLAG;
        if (exact.isCountRequestableCraftsBig()) flags |= REQUESTABLE_CRAFTS_FLAG;
        if (flags == 0) return;

        out.writeInt(PACKET_MAGIC);
        out.writeByte(flags);
        if ((flags & STACK_SIZE_FLAG) != 0) BigValueCodec.writePacket(out, exact.getStackSizeBig());
        if ((flags & REQUESTABLE_FLAG) != 0) BigValueCodec.writePacket(out, exact.getCountRequestableBig());
        if ((flags & REQUESTABLE_CRAFTS_FLAG) != 0) {
            BigValueCodec.writePacket(out, exact.getCountRequestableCraftsBig());
        }
    }

    public static void readPacketExtension(ByteBuf in, IAEItemStack stack) throws IOException {
        if (!(stack instanceof BigAEItemStack) || in.readableBytes() < Integer.BYTES + 1) return;
        if (in.getInt(in.readerIndex()) != PACKET_MAGIC) return;

        in.skipBytes(Integer.BYTES);
        int flags = in.readUnsignedByte();
        if ((flags & ~(STACK_SIZE_FLAG | REQUESTABLE_FLAG | REQUESTABLE_CRAFTS_FLAG)) != 0 || flags == 0) {
            throw new IOException("invalid Apeiron AE stack packet flags: " + flags);
        }

        BigAEItemStack exact = (BigAEItemStack) stack;
        if ((flags & STACK_SIZE_FLAG) != 0) exact.setStackSizeBig(BigValueCodec.readPacket(in));
        if ((flags & REQUESTABLE_FLAG) != 0) {
            exact.setCountRequestableBig(BigValueCodec.readPacket(in));
        }
        if ((flags & REQUESTABLE_CRAFTS_FLAG) != 0) {
            exact.setCountRequestableCraftsBig(BigValueCodec.readPacket(in));
        }
    }

    public static void addStorage(IAEItemStack target, IAEItemStack source) {
        if (target instanceof BigAEItemStack) {
            BigAEItemStack exact = (BigAEItemStack) target;
            if (source instanceof BigAEItemStack && ((BigAEItemStack) source).isStackSizeBig()) {
                exact.incStackSizeBig(((BigAEItemStack) source).getStackSizeBig());
            } else {
                target.incStackSize(source.getStackSize());
            }
        } else if (source instanceof BigAEItemStack && ((BigAEItemStack) source).isStackSizeBig()) {
            throw new ArithmeticException("target item stack cannot represent a big count");
        } else {
            target.setStackSize(Math.addExact(target.getStackSize(), source.getStackSize()));
        }
    }

    public static void addRequestable(IAEItemStack target, IAEItemStack source, boolean includeCrafts) {
        if (target instanceof BigAEItemStack) {
            BigAEItemStack exact = (BigAEItemStack) target;
            if (source instanceof BigAEItemStack && ((BigAEItemStack) source).isCountRequestableBig()) {
                exact.incCountRequestableBig(((BigAEItemStack) source).getCountRequestableBig());
            } else {
                target.incCountRequestable(source.getCountRequestable());
            }
            if (includeCrafts) {
                if (source instanceof BigAEItemStack && ((BigAEItemStack) source).isCountRequestableCraftsBig()) {
                    exact.incCountRequestableCraftsBig(((BigAEItemStack) source).getCountRequestableCraftsBig());
                } else {
                    exact.incCountRequestableCrafts(source.getCountRequestableCrafts());
                }
            }
        } else {
            if (source instanceof BigAEItemStack && (((BigAEItemStack) source).isCountRequestableBig()
                || includeCrafts && ((BigAEItemStack) source).isCountRequestableCraftsBig())) {
                throw new ArithmeticException("target item stack cannot represent a big request count");
            }
            target.setCountRequestable(Math.addExact(target.getCountRequestable(), source.getCountRequestable()));
            if (includeCrafts) {
                target.setCountRequestableCrafts(
                    Math.addExact(target.getCountRequestableCrafts(), source.getCountRequestableCrafts()));
            }
        }
    }

    public static IAEItemStack readFromBigPacket(ByteBuf in) throws IOException {
        IAEItemStack stack = AEItemStack.loadItemStackFromPacket(in);
        if (!(stack instanceof BigAEItemStack)) {
            throw new IOException("AEItemStack big-count extension is unavailable");
        }
        return stack;
    }
}
