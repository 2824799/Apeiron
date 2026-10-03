package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/** Decimal and scientific notation without a floating-point conversion. */
public final class ScientificInteger {

    private static final Pattern NUMBER = Pattern.compile("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private ScientificInteger() {}

    public static BigInteger nonNegative(String text) {
        String value = text.trim();
        if (value.length() > 4096 || !NUMBER.matcher(value)
            .matches()) throw new NumberFormatException("Invalid integer");
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.signum() == 0) return BigInteger.ZERO;
        return positive(value);
    }

    public static String format(BigInteger value) {
        String decimal = value.toString();
        return decimal.length() <= 24 ? decimal
            : new BigDecimal(value).stripTrailingZeros()
                .toString();
    }

    public static BigInteger positive(String text) {
        String value = text.trim();
        if (value.length() > 4096 || !NUMBER.matcher(value)
            .matches()) throw new NumberFormatException("Invalid integer");
        BigDecimal decimal = new BigDecimal(value);
        if ((long) decimal.precision() - decimal.scale() > 32767) throw new NumberFormatException("Integer too large");
        if (decimal.signum() <= 0 || decimal.scale() > decimal.precision())
            throw new NumberFormatException("Expected positive integer");
        BigInteger result = decimal.toBigIntegerExact();
        if (result.signum() <= 0) throw new NumberFormatException("Expected positive integer");
        return result;
    }
}
