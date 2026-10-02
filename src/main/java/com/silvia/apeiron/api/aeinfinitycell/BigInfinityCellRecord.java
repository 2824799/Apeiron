package com.silvia.apeiron.api.aeinfinitycell;

import java.math.BigInteger;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/** Exact access to the existing UUID-backed record without changing its on-disk format. */
public interface BigInfinityCellRecord {

    boolean canStoreStackBig(IAEStack<?> input);

    boolean addStackBig(IAEStack<?> input, BigInteger amount);

    BigInteger extractStackBig(IAEStack<?> request, BigInteger amount, boolean modulate);

    BigInteger getAmountBig(IAEStack<?> request);

    BigInteger getStoredUnitsBig(IAEStackType<?> type);

    IItemList<?> getAvailableStacksBig(IAEStackType<?> type, IItemList<?> out);
}
