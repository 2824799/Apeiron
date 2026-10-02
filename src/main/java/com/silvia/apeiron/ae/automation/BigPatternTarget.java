package com.silvia.apeiron.ae.automation;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.util.InventoryAdaptor;

/** An already verified face accepting a processing pattern. */
public final class BigPatternTarget {

    public final TileEntity te;
    public final ForgeDirection side;
    public final InventoryAdaptor ad;

    public BigPatternTarget(TileEntity tile, ForgeDirection side, InventoryAdaptor adaptor) {
        this.te = tile;
        this.side = side;
        this.ad = adaptor;
    }
}
