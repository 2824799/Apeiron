package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;

import com.silvia.apeiron.ae.storage.BigCellInventory;
import com.silvia.apeiron.ae.storage.BigCellInventoryHandler;

import appeng.me.storage.CellInventoryHandler;

/** Bridges exact metrics from both item and fluid CellInventory implementations. */
@Mixin(value = CellInventoryHandler.class, remap = false)
public abstract class CellInventoryHandlerBigMixin implements BigCellInventoryHandler {

    private BigCellInventory apeiron$exactCell() {
        Object cell = ((CellInventoryHandler<?>) (Object) this).getCellInv();
        return cell instanceof BigCellInventory ? (BigCellInventory) cell : null;
    }

    @Override
    public BigInteger getStoredItemCountBig() {
        BigCellInventory exact = apeiron$exactCell();
        return exact == null ? BigInteger.valueOf(
            ((CellInventoryHandler<?>) (Object) this).getCellInv()
                .getStoredItemCount())
            : exact.getStoredItemCountBig();
    }

    @Override
    public BigInteger getTotalBytesBig() {
        BigCellInventory exact = apeiron$exactCell();
        return exact == null ? BigInteger.valueOf(((CellInventoryHandler<?>) (Object) this).getTotalBytes())
            : exact.getTotalBytesBig();
    }

    @Override
    public BigInteger getUsedBytesBig() {
        BigCellInventory exact = apeiron$exactCell();
        return exact == null ? BigInteger.valueOf(((CellInventoryHandler<?>) (Object) this).getUsedBytes())
            : exact.getUsedBytesBig();
    }

    @Override
    public BigInteger getFreeBytesBig() {
        BigCellInventory exact = apeiron$exactCell();
        return exact == null ? BigInteger.valueOf(((CellInventoryHandler<?>) (Object) this).getFreeBytes())
            : exact.getFreeBytesBig();
    }

    @Override
    public BigInteger getTotalTypesBig() {
        BigCellInventory exact = apeiron$exactCell();
        return exact == null ? BigInteger.valueOf(((CellInventoryHandler<?>) (Object) this).getTotalTypes())
            : exact.getTotalTypesBig();
    }

    @Override
    public BigInteger getUsedTypesBig() {
        BigCellInventory exact = apeiron$exactCell();
        return exact == null ? BigInteger.valueOf(((CellInventoryHandler<?>) (Object) this).getUsedTypes())
            : exact.getStoredTypesBig();
    }

    @Override
    public BigInteger getFreeTypesBig() {
        BigCellInventory exact = apeiron$exactCell();
        return exact == null ? BigInteger.valueOf(((CellInventoryHandler<?>) (Object) this).getFreeTypes())
            : exact.getRemainingTypesBig();
    }
}
