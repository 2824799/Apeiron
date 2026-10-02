package com.silvia.apeiron.ae.terminal;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Deque;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.storage.data.IAEStack;

/** Keeps AE storage-cell tooltip values exact across its legacy long formatting calls. */
public final class BigCellTooltipNumbers {

    private static final ThreadLocal<Deque<BigInteger>> NUMBERS = new ThreadLocal<Deque<BigInteger>>() {

        @Override
        protected Deque<BigInteger> initialValue() {
            return new ArrayDeque<>();
        }
    };
    private static final ThreadLocal<BigInteger> STACK = new ThreadLocal<>();

    private BigCellTooltipNumbers() {}

    public static long captureNumber(final BigInteger value) {
        NUMBERS.get()
            .addLast(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static String formatNumber(final NumberFormat formatter, final long legacy) {
        final Deque<BigInteger> values = NUMBERS.get();
        final BigInteger value = values.pollFirst();
        if (values.isEmpty()) NUMBERS.remove();
        return value == null || BigAEStackValues.fitsLong(value) ? formatter.format(legacy)
            : BigNumberFormatter.formatExact(value);
    }

    public static long captureStack(final IAEStack<?> stack) {
        final BigInteger value = BigAEStackValues.get(stack);
        STACK.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static String formatStack(final long legacy) {
        final BigInteger value = STACK.get();
        STACK.remove();
        return value == null || BigAEStackValues.fitsLong(value)
            ? appeng.util.ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy)
            : BigNumberFormatter.formatCompact(value);
    }
}
