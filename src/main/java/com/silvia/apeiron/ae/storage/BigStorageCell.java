package com.silvia.apeiron.ae.storage;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

/** Optional extension for an IStorageCell item whose byte capacity exceeds long. */
public interface BigStorageCell {

    BigInteger getBytesBig(ItemStack cell);
}
