package com.silvia.apeiron.api.machine.tst;

import java.math.BigInteger;
import java.util.Map;

import com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID;

public interface BigTstItemGiver {

    Map<TST_ItemID, BigInteger> getItemAmountsBig();
}
