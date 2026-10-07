package com.silvia.apeiron.ae.smoke;

import java.io.IOException;
import java.math.BigInteger;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEItemStack;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigMEInventories;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.exceptions.AppEngException;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.ItemCellInventory;
import appeng.me.storage.ItemCellInventoryHandler;
import appeng.me.storage.MEPassThrough;
import appeng.me.storage.NullInventory;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import appeng.util.item.HashBasedItemList;
import appeng.util.item.ItemList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Optional in-game check of the transformed AE2 class, enabled by APEIRON_VERIFY_STACK=1. */
public final class AEItemStackSmoke {

    private AEItemStackSmoke() {}

    public static void verify() {
        IAEItemStack original = AEItemStack.create(new ItemStack(Items.diamond));
        check(original instanceof BigAEItemStack, "AEItemStack mixin was not applied");

        BigAEItemStack exact = (BigAEItemStack) original;
        BigInteger huge = BigInteger.ONE.shiftLeft(128)
            .add(BigInteger.valueOf(37L));
        exact.setStackSizeBig(huge);
        exact.setCountRequestableBig(huge.add(BigInteger.ONE));
        exact.setCountRequestableCraftsBig(huge.add(BigInteger.TEN));
        check(original.getStackSize() == Long.MAX_VALUE, "legacy stack size was not clamped");

        IAEItemStack copy = original.copy();
        check(
            BigAEItemStacks.stackSize(copy)
                .equals(huge),
            "copy lost exact stack size");
        check(
            BigAEItemStacks.countRequestable(copy)
                .equals(huge.add(BigInteger.ONE)),
            "copy lost request count");

        NBTTagCompound tag = new NBTTagCompound();
        original.writeToNBT(tag);
        IAEItemStack restored = AEItemStack.loadItemStackFromNBT(tag);
        check(
            BigAEItemStacks.stackSize(restored)
                .equals(huge),
            "NBT lost exact stack size");
        check(
            BigAEItemStacks.countRequestableCrafts(restored)
                .equals(huge.add(BigInteger.TEN)),
            "NBT lost craft request count");

        IAEItemStack summand = original.copy();
        copy.add(summand);
        check(
            BigAEItemStacks.stackSize(copy)
                .equals(huge.shiftLeft(1)),
            "AEItemStack.add lost big count");

        ItemList sorted = new ItemList(true);
        sorted.addStorage(original);
        sorted.addStorage(original);
        check(
            BigAEItemStacks.stackSize(sorted.findPrecise(original))
                .equals(huge.shiftLeft(1)),
            "ItemList.addStorage lost big count");
        HashBasedItemList hashed = new HashBasedItemList();
        hashed.addStorage(original);
        hashed.addStorage(original);
        check(
            BigAEItemStacks.stackSize(hashed.findPrecise(original))
                .equals(huge.shiftLeft(1)),
            "HashBasedItemList.addStorage lost big count");

        ItemList requestable = new ItemList(true);
        requestable.addRequestable(original);
        requestable.addRequestable(original);
        IAEItemStack requests = requestable.findPrecise(original);
        check(
            BigAEItemStacks.countRequestable(requests)
                .equals(
                    huge.add(BigInteger.ONE)
                        .shiftLeft(1)),
            "ItemList.addRequestable lost big request count");
        check(
            BigAEItemStacks.countRequestableCrafts(requests)
                .equals(
                    huge.add(BigInteger.TEN)
                        .shiftLeft(1)),
            "ItemList.addRequestable lost big craft request count");

        try {
            ItemStack cellItem = AEApi.instance()
                .definitions()
                .items()
                .cell1k()
                .maybeStack(1)
                .get();
            ItemCellInventory cell = new ItemCellInventory(cellItem, null);
            check(cell instanceof BigIMEInventory, "ItemCellInventory big-count mixin was not applied");
            MEPassThrough<IAEItemStack> adapter = new MEPassThrough<>(cell, AEItemStackType.ITEM_STACK_TYPE);
            ItemCellInventoryHandler handler = new ItemCellInventoryHandler(adapter);
            check(adapter instanceof BigIMEInventory, "MEPassThrough big-count mixin was not applied");
            check(handler instanceof BigIMEInventory, "MEInventoryHandler big-count mixin was not applied");
            BaseActionSource source = new BaseActionSource();
            IAEItemStack simulatedRemainder = BigMEInventories
                .injectItemsBig(handler, original, Actionable.SIMULATE, source);
            check(cell.getStoredItemCount() == 0, "injection simulation changed cell contents");
            IAEItemStack actualRemainder = BigMEInventories
                .injectItemsBig(handler, original, Actionable.MODULATE, source);
            check(
                BigAEItemStacks.stackSize(simulatedRemainder)
                    .equals(BigAEItemStacks.stackSize(actualRemainder)),
                "injection simulation disagreed with actual remainder");
            check(
                BigAEItemStacks.stackSize(actualRemainder)
                    .add(BigInteger.valueOf(cell.getStoredItemCount()))
                    .equals(huge),
                "injection did not conserve item count");
            handler.setBaseAccess(AccessRestriction.READ);
            check(
                BigMEInventories.injectItemsBig(handler, original, Actionable.MODULATE, source) == original,
                "read-only handler accepted a big injection");
            handler.setBaseAccess(AccessRestriction.WRITE);
            check(
                BigMEInventories.extractItemsBig(handler, original, Actionable.MODULATE, source) == null,
                "write-only handler allowed a big extraction");
            handler.setBaseAccess(AccessRestriction.READ_WRITE);
            ItemCellInventory reloadedCell = new ItemCellInventory(cellItem, null);
            ItemCellInventoryHandler reloadedHandler = new ItemCellInventoryHandler(reloadedCell);
            check(reloadedCell.getStoredItemCount() == cell.getStoredItemCount(), "cell reload lost stored count");
            IAEItemStack simulatedOutput = BigMEInventories
                .extractItemsBig(reloadedHandler, original, Actionable.SIMULATE, source);
            check(reloadedCell.getStoredItemCount() == cell.getStoredItemCount(), "extraction simulation changed cell");
            IAEItemStack actualOutput = BigMEInventories
                .extractItemsBig(reloadedHandler, original, Actionable.MODULATE, source);
            check(
                BigAEItemStacks.stackSize(actualOutput)
                    .equals(BigAEItemStacks.stackSize(simulatedOutput)),
                "extraction simulation disagreed with actual output");
            check(
                BigAEItemStacks.stackSize(actualOutput)
                    .equals(BigInteger.valueOf(cell.getStoredItemCount())),
                "big extraction lost or created items");
            check(reloadedCell.getStoredItemCount() == 0, "big extraction did not empty the cell");
            IAEItemStack small = original.copy();
            small.setStackSize(5L);
            check(reloadedHandler.injectItems(small, Actionable.MODULATE, source) == null, "legacy injection failed");
            IAEItemStack smallRequest = small.copy();
            smallRequest.setStackSize(3L);
            check(
                reloadedHandler.extractItems(smallRequest, Actionable.MODULATE, source)
                    .getStackSize() == 3L,
                "legacy extraction failed");
            check(reloadedCell.getStoredItemCount() == 2L, "legacy input/output changed the wrong amount");
            try {
                BigMEInventories
                    .injectItemsBig(new NullInventory<IAEItemStack>(), original, Actionable.SIMULATE, source);
                throw new IllegalStateException("unsupported backend silently accepted a big injection");
            } catch (UnsupportedOperationException expected) {
                // A backend without an exact implementation must reject the request before truncation.
            }
        } catch (AppEngException error) {
            throw new IllegalStateException("item cell big-count verification failed", error);
        }

        ByteBuf buffer = Unpooled.buffer();
        try {
            exact.writeToBigPacket(buffer);
            IAEItemStack packetCopy = BigAEItemStacks.readFromBigPacket(buffer);
            check(
                BigAEItemStacks.stackSize(packetCopy)
                    .equals(huge),
                "big packet lost exact stack size");
        } catch (IOException error) {
            throw new IllegalStateException("big packet round trip failed", error);
        } finally {
            buffer.release();
        }

        ByteBuf genericBuffer = Unpooled.buffer();
        try {
            IAEItemStack smallPacket = AEItemStack.create(new ItemStack(Items.diamond));
            smallPacket.setStackSize(3L);
            IAEStack.writeToPacketGeneric(genericBuffer, smallPacket);
            IAEStack.writeToPacketGeneric(genericBuffer, original);
            IAEStack first = IAEStack.fromPacketGeneric(genericBuffer);
            IAEStack second = IAEStack.fromPacketGeneric(genericBuffer);
            check(first != null && first.getStackSize() == 3L, "small generic packet was not preserved");
            check(
                second != null && BigAEItemStacks.stackSize((IAEItemStack) second)
                    .equals(huge),
                "generic AE packet lost exact stack size");
        } catch (IOException error) {
            throw new IllegalStateException("generic AE packet round trip failed", error);
        } finally {
            genericBuffer.release();
        }

        copy.reset();
        check(
            BigAEItemStacks.stackSize(copy)
                .signum() == 0,
            "reset kept big count");
        original.setStackSize(7L);
        check(
            BigAEItemStacks.stackSize(original)
                .equals(BigInteger.valueOf(7L)),
            "legacy setter kept big count");
        Apeiron.LOG.info("AEItemStack big-count runtime verification passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
