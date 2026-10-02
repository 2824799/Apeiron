package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Formats exact counts for compact terminal labels and full tooltips. */
public final class BigNumberFormatter {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final String[] BYTE_UNITS = { "B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB" };

    private BigNumberFormatter() {}

    /**
     * Uses one decimal place and an uppercase E for values outside the legacy long range.
     */
    public static String formatCompact(BigInteger value) {
        if (value == null) throw new NullPointerException("value");
        if (value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0) return value.toString();

        boolean negative = value.signum() < 0;
        BigInteger absolute = value.abs();
        int exponent = absolute.toString()
            .length() - 1;
        BigDecimal mantissa = new BigDecimal(absolute).movePointLeft(exponent)
            .setScale(1, RoundingMode.HALF_UP);
        if (mantissa.compareTo(BigDecimal.TEN) >= 0) {
            mantissa = BigDecimal.ONE.setScale(1);
            exponent++;
        }

        return (negative ? "-" : "") + mantissa.toPlainString() + "E" + exponent;
    }

    /** Uses US grouping separators while retaining every exact digit. */
    public static String formatExact(BigInteger value) {
        if (value == null) throw new NullPointerException("value");
        return NumberFormat.getIntegerInstance(Locale.US)
            .format(value);
    }

    /** Formats an exact byte count using AE2's binary units. */
    public static String formatBytes(BigInteger value) {
        if (value == null) throw new NullPointerException("value");
        if (value.signum() < 0) return "-" + formatBytes(value.negate());
        int unit = 0;
        BigDecimal scaled = new BigDecimal(value);
        while (scaled.compareTo(BigDecimal.valueOf(1024)) >= 0 && unit < BYTE_UNITS.length - 1) {
            scaled = scaled.divide(BigDecimal.valueOf(1024), 2, RoundingMode.HALF_UP);
            unit++;
        }
        return scaled.stripTrailingZeros()
            .toPlainString() + " "
            + BYTE_UNITS[unit];
    }
}
