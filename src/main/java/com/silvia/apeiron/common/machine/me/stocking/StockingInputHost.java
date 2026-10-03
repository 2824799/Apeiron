package com.silvia.apeiron.common.machine.me.stocking;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.me.helpers.IGridProxyable;

public interface StockingInputHost extends IGridProxyable, IActionHost {

    StockingInputLogic getStockingInput();

    @Override
    default IGridNode getActionableNode() {
        return getProxy().getNode();
    }
}
