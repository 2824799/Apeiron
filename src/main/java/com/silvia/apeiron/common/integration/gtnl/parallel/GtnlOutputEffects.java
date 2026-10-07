// SPDX-License-Identifier: GPL-3.0-only
// Preserves GT Not Leisure's TreeDiagram nanite and CrackerHub coil output rules (ABKQPO and contributors).
package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.science.gtnl.common.machine.multiblock.wireless.CrackerHub;
import com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram;
import com.silvia.apeiron.math.MiningOutputCounts;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.objects.XSTR;

/** One nanite draw is shared by every product in a recipe; capacity planning never advances randomness. */
final class GtnlOutputEffects {

    private final BigInteger parallels;
    private final BigInteger failed;
    private final BigInteger bonus;
    private final BigInteger coils;
    private final BigDecimal fluidCoils;

    GtnlOutputEffects(MTEMultiBlockBase machine, BigInteger parallels, boolean capacity) {
        this.parallels = parallels;
        if (machine instanceof TreeDiagram) {
            TreeDiagram tree = (TreeDiagram) machine;
            failed = capacity ? BigInteger.ZERO : sample(parallels, tree.failureBonus);
            bonus = capacity ? (tree.outputCoefficient > 0 ? parallels : BigInteger.ZERO)
                : sample(parallels, tree.outputCoefficient);
        } else {
            failed = BigInteger.ZERO;
            bonus = BigInteger.ZERO;
        }
        if (machine instanceof CrackerHub) {
            int tier = ((CrackerHub) machine).getMCoilLevel()
                .getTier() + 1;
            fluidCoils = BigDecimal.ONE.add(
                BigDecimal.valueOf(Math.floor(tier / 2.0))
                    .multiply(new BigDecimal("0.1")));
            coils = fluidCoils.toBigInteger();
        } else {
            coils = machine instanceof com.science.gtnl.common.machine.multiblock.PCBFactory ? BigInteger.valueOf(2)
                : BigInteger.ONE;
            fluidCoils = BigDecimal.ONE;
        }
    }

    BigInteger apply(BigInteger amount) {
        return nanites(amount).multiply(coils);
    }

    BigInteger applyFluid(BigInteger amount) {
        return new BigDecimal(nanites(amount)).multiply(fluidCoils)
            .toBigInteger();
    }

    private BigInteger nanites(BigInteger amount) {
        return amount.subtract(round(amount.multiply(failed), parallels.multiply(BigInteger.valueOf(4))))
            .add(round(amount.multiply(bonus), parallels.multiply(BigInteger.valueOf(2))));
    }

    private static BigInteger round(BigInteger value, BigInteger divisor) {
        return value.shiftLeft(1)
            .add(divisor)
            .divide(divisor.shiftLeft(1));
    }

    private static BigInteger sample(BigInteger count, double chance) {
        int scaled = (int) Math.round(Math.max(0, Math.min(1, chance)) * 1_000_000_000);
        return MiningOutputCounts.binomial(count, scaled, 1_000_000_000, XSTR.XSTR_INSTANCE);
    }
}
