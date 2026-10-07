// SPDX-License-Identifier: GPL-3.0-only
// Overclock/sub-tick rules adapted from GT Not Leisure 0.2.7-rc2 (ABKQPO and contributors).
package com.silvia.apeiron.common.integration.gtnl.parallel;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.science.gtnl.utils.recipes.GTNLOverclockCalculator;
import com.silvia.apeiron.math.ExactOverclock;

/** Mirrors GTNL's OC and sub-tick rules without multiplying two longs or casting the final EU to long. */
final class GtnlOverclocks {

    final BigInteger eut;
    final int duration;
    final int overclocks;
    final int heatOverclocks;

    private GtnlOverclocks(BigInteger eut, int duration, int overclocks, int heatOverclocks) {
        this.eut = eut;
        this.duration = duration;
        this.overclocks = overclocks;
        this.heatOverclocks = heatOverclocks;
    }

    static GtnlOverclocks calculate(GTNLOverclockCalculator c, BigInteger lanes) {
        return calculate(c, lanes, false);
    }

    static GtnlOverclocks calculate(GTNLOverclockCalculator c, BigInteger lanes, boolean stopAtOneTick) {
        BigDecimal recipePower = ExactOverclock
            .power(c.recipeEUt, lanes, c.eutModifier, c.calculateHeatDiscountMultiplier());
        BigInteger amps = c.amperageOC ? BigInteger.valueOf(c.machineAmperage)
            : BigInteger.valueOf(c.machineAmperage)
                .min(lanes);
        BigDecimal available = new BigDecimal(
            BigInteger.valueOf(c.machineVoltage)
                .multiply(amps));
        double ticks = c.durationUnderOneTickSupplier == null
            ? c.duration * c.durationModifier * c.extraDurationModifier
            : c.getDurationUnderOneTickSupplier();
        if (c.noOverclock) return new GtnlOverclocks(ExactOverclock.ceil(recipePower), ticks(Math.ceil(ticks)), 0, 0);
        int oc = Math.max(0, Math.min(c.maxOverclocks, ExactOverclock.tiers(available, recipePower, 4)));
        if (!c.amperageOC) oc = Math.min(oc, Math.max(0, voltageTier(c.machineVoltage) - voltageTier(c.recipeEUt)));
        if (c.laserOC) {
            int regular = Math.max(
                0,
                Math.min(c.maxRegularOverclocks, ExactOverclock.tiers(available, recipePower, c.eutIncreasePerOC)));
            BigDecimal power = recipePower.multiply(
                ExactOverclock.decimal(c.eutIncreasePerOC)
                    .pow(regular));
            int laser = 0;
            while (true) {
                BigDecimal next = power.multiply(
                    ExactOverclock.decimal(c.eutIncreasePerOC)
                        .add(BigDecimal.valueOf(3L * (laser + 1), 1)));
                if (next.compareTo(available) > 0) break;
                power = next;
                if (++laser > 4096) throw new IllegalArgumentException("Unbounded laser OC");
            }
            if (stopAtOneTick) {
                int needed = required(ticks, c.durationDecreasePerOC);
                int keptRegular = Math.min(regular, needed);
                int keptLaser = Math.min(laser, Math.max(0, needed - keptRegular));
                power = recipePower.multiply(
                    ExactOverclock.decimal(c.eutIncreasePerOC)
                        .pow(keptRegular));
                for (int i = 1; i <= keptLaser; i++) power = power.multiply(
                    ExactOverclock.decimal(c.eutIncreasePerOC)
                        .add(BigDecimal.valueOf(3L * i, 1)));
                return new GtnlOverclocks(
                    ExactOverclock.ceil(power),
                    ticks(ticks / Math.pow(c.durationDecreasePerOC, keptRegular + keptLaser)),
                    keptRegular + keptLaser,
                    0);
            }
            return new GtnlOverclocks(
                ExactOverclock.ceil(power),
                ticks(ticks / Math.pow(c.durationDecreasePerOC, regular + laser)),
                regular + laser,
                0);
        }
        int heat = Math.max(0, Math.min(c.heatOC ? (c.machineHeat - c.recipeHeat) / 1800 : 0, oc));
        if (stopAtOneTick) {
            heat = Math.min(heat, required(ticks, c.durationDecreasePerHeatOC));
            oc = heat + Math.min(
                Math.max(0, oc - heat),
                required(ticks / Math.pow(c.durationDecreasePerHeatOC, heat), c.durationDecreasePerOC));
        }
        ticks /= Math.pow(c.durationDecreasePerHeatOC, heat);
        ticks /= Math.pow(c.durationDecreasePerOC, oc - heat);
        return new GtnlOverclocks(ExactOverclock.energy(recipePower, c.eutIncreasePerOC, oc), ticks(ticks), oc, heat);
    }

    static BigInteger maximum(GTNLOverclockCalculator c, int lanes) {
        if (c.hasDurationUnderOneTickSupplier()) {
            double ticks = c.getDurationUnderOneTickSupplier();
            return ticks < 1 ? BigDecimal.valueOf(lanes)
                .divide(ExactOverclock.decimal(ticks), 0, java.math.RoundingMode.DOWN)
                .toBigIntegerExact() : BigInteger.valueOf(lanes);
        }
        if (c.noOverclock) return BigInteger.valueOf(lanes);
        // GTNL deliberately excludes extraDurationModifier when granting sub-tick parallelism.
        double duration = c.duration * c.durationModifier;
        int overclocks;
        if (c.laserOC) {
            overclocks = calculate(c, BigInteger.valueOf(lanes)).overclocks;
            int needed = (int) Math.ceil(Math.log(duration) / Math.log(c.durationDecreasePerOC));
            if (overclocks < needed) return BigInteger.valueOf(lanes);
            double multiplier = Math.ceil(
                Math.pow(c.durationDecreasePerOC, Math.max(overclocks - needed, 0))
                    * Math.pow(c.durationDecreasePerOC, needed)
                    / duration);
            return ExactOverclock.multiplyCeil(BigInteger.valueOf(lanes), multiplier);
        }
        if (c.amperageOC) {
            BigDecimal power = new BigDecimal(
                BigInteger.valueOf(c.machineVoltage)
                    .multiply(BigInteger.valueOf(c.machineAmperage)));
            BigDecimal recipePower = ExactOverclock
                .power(c.recipeEUt, BigInteger.valueOf(lanes), c.eutModifier, c.calculateHeatDiscountMultiplier());
            overclocks = Math.max(0, Math.min(c.maxOverclocks, ExactOverclock.tiers(power, recipePower, 4)));
        } else {
            // The native multiplier uses voltage tiers, independently of the power limit applied to actual lanes.
            int machineTier = (int) Math.max(gregtech.api.util.GTUtility.log4ceil(c.machineVoltage / 8), 1);
            int recipeTier = (int) Math.max(gregtech.api.util.GTUtility.log4ceil(c.recipeEUt / 8), 1);
            overclocks = Math.max(0, Math.min(c.maxOverclocks, machineTier - recipeTier));
        }
        int heat = Math.max(0, Math.min(c.heatOC ? (c.machineHeat - c.recipeHeat) / 1800 : 0, overclocks));
        int neededHeat = (int) Math.ceil(Math.log(duration) / Math.log(c.durationDecreasePerHeatOC));
        double afterHeat = duration / Math.pow(c.durationDecreasePerHeatOC, heat);
        int neededRegular = (int) Math.ceil(Math.log(afterHeat) / Math.log(c.durationDecreasePerOC));
        int regular = overclocks - heat;
        double correction;
        if (heat >= neededHeat) correction = Math.pow(c.durationDecreasePerHeatOC, neededHeat) / duration;
        else if (regular >= neededRegular) correction = Math.pow(c.durationDecreasePerOC, neededRegular) / afterHeat;
        else return BigInteger.valueOf(lanes);
        double multiplier = Math.ceil(
            Math.pow(c.durationDecreasePerHeatOC, Math.max(heat - neededHeat, 0))
                * Math.pow(c.durationDecreasePerOC, Math.max(regular - neededRegular, 0))
                * correction);
        return ExactOverclock.multiplyCeil(BigInteger.valueOf(lanes), Math.max(1, multiplier));
    }

    private static int voltageTier(long eut) {
        return (int) Math.max(Math.ceil(Math.log((double) eut / 8) / Math.log(4)), 1);
    }

    private static int required(double ticks, double reduction) {
        if (ticks <= 1) return 0;
        return reduction > 1 ? Math.max(0, (int) Math.ceil(Math.log(ticks) / Math.log(reduction))) : Integer.MAX_VALUE;
    }

    private static int ticks(double value) {
        if (!Double.isFinite(value) || value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1, (int) value);
    }
}
