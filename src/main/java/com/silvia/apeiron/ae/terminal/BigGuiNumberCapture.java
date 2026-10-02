package com.silvia.apeiron.ae.terminal;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Locale;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.storage.data.IAEStack;
import appeng.util.ReadableNumberConverter;

/** Bridges AE's long-only GUI calls to exact values without changing its public GUI APIs. */
public final class BigGuiNumberCapture {

    private static final ThreadLocal<Boolean> STACK_INFINITE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TOTAL_INFINITE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PENDING_INFINITE = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> STACK = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> CRAFTS = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> REQUESTABLE = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> REPORT = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> TOTAL = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> PENDING_SET = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> AMOUNT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> OPTIMIZER_WIDE_CALLS = new ThreadLocal<>();
    private static final ThreadLocal<Integer> OPTIMIZER_MULTIPLIER = new ThreadLocal<>();
    private static final ThreadLocal<Integer> REQUESTABLE_FORMATS = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger[]> NETWORK_VALUES = new ThreadLocal<>();
    private static final ThreadLocal<Integer> NETWORK_MODE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> NETWORK_BYTE_CURSOR = new ThreadLocal<>();
    private static final ThreadLocal<Integer> NETWORK_INTEGER_CURSOR = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> SCAN_TYPES_USED = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> SCAN_TYPES_TOTAL = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> SCAN_BYTES_USED = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> SCAN_BYTES_TOTAL = new ThreadLocal<>();

    private BigGuiNumberCapture() {}

    public static long captureStack(final IAEStack<?> stack) {
        STACK_INFINITE.set(BigAEStackValues.isInfinite(stack));
        final BigInteger value = BigAEStackValues.get(stack);
        STACK.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static long captureCrafts(final IAEStack<?> stack) {
        final BigInteger value = BigAEStackValues.getCountRequestableCrafts(stack);
        CRAFTS.set(value);
        OPTIMIZER_WIDE_CALLS.set(0);
        REQUESTABLE_FORMATS.set(0);
        OPTIMIZER_MULTIPLIER.remove();
        return BigAEStackValues.saturatedLong(value);
    }

    public static long captureRequestable(final IAEStack<?> stack) {
        final BigInteger value = BigAEStackValues.getCountRequestable(stack);
        REQUESTABLE.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static boolean hasPendingCrafts() {
        return CRAFTS.get() != null;
    }

    public static String formatWideStack(final long legacy) {
        if (Boolean.TRUE.equals(takeFlag(STACK_INFINITE))) {
            STACK.remove();
            return "∞";
        }
        final BigInteger value = take(STACK);
        return value == null || BigAEStackValues.fitsLong(value)
            ? ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy)
            : BigNumberFormatter.formatCompact(value);
    }

    public static String formatWideCrafts(final long legacy) {
        final BigInteger value = CRAFTS.get();
        return value == null || BigAEStackValues.fitsLong(value)
            ? ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy)
            : BigNumberFormatter.formatCompact(value);
    }

    public static String formatWideAny(final long legacy) {
        final BigInteger crafts = take(CRAFTS);
        if (crafts != null) {
            return BigAEStackValues.fitsLong(crafts) ? ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy)
                : BigNumberFormatter.formatCompact(crafts);
        }
        return formatWideStack(legacy);
    }

    public static String formatExactStack(final long legacy) {
        if (Boolean.TRUE.equals(takeFlag(STACK_INFINITE))) {
            STACK.remove();
            return "∞";
        }
        final BigInteger value = take(STACK);
        return value == null || BigAEStackValues.fitsLong(value) ? NumberFormat.getNumberInstance(Locale.US)
            .format(legacy) : BigNumberFormatter.formatExact(value);
    }

    public static String formatExactCrafts(final long legacy) {
        final BigInteger value = take(CRAFTS);
        return value == null || BigAEStackValues.fitsLong(value) ? NumberFormat.getNumberInstance(Locale.US)
            .format(legacy) : BigNumberFormatter.formatExact(value);
    }

    public static String formatExactAny(final long legacy) {
        final BigInteger crafts = take(CRAFTS);
        if (crafts != null) {
            return BigAEStackValues.fitsLong(crafts) ? NumberFormat.getNumberInstance(Locale.US)
                .format(legacy) : BigNumberFormatter.formatExact(crafts);
        }
        return formatExactStack(legacy);
    }

    public static String formatWideRequestable(final long legacy) {
        final BigInteger value = REQUESTABLE.get();
        return value == null || BigAEStackValues.fitsLong(value)
            ? ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy)
            : BigNumberFormatter.formatCompact(value);
    }

    public static String formatExactRequestable(final long legacy) {
        final BigInteger value = take(REQUESTABLE);
        return value == null || BigAEStackValues.fitsLong(value) ? NumberFormat.getNumberInstance(Locale.US)
            .format(legacy) : BigNumberFormatter.formatExact(value);
    }

    /** Formats GuiOptimizePatterns' first wide value and records its multiplier shift. */
    public static String formatOptimizerWide(final long legacy) {
        final BigInteger crafts = CRAFTS.get();
        if (crafts == null) {
            return ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy);
        }
        final int calls = OPTIMIZER_WIDE_CALLS.get() == null ? 0 : OPTIMIZER_WIDE_CALLS.get();
        OPTIMIZER_WIDE_CALLS.set(calls + 1);
        if (calls == 0) {
            return BigAEStackValues.fitsLong(crafts) ? ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy)
                : BigNumberFormatter.formatCompact(crafts);
        }

        final int multiplier = powerOfTwoExponent(legacy);
        if (multiplier >= 0) OPTIMIZER_MULTIPLIER.set(multiplier);
        return ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy);
    }

    /** Computes the optimizer multiplier without multiplying saturated long values. */
    public static int getBitMultiplier(final long currentLegacy, final long perLegacy, final long maximumLegacy) {
        final BigInteger current = take(CRAFTS);
        final BigInteger per = take(REQUESTABLE);
        if (current == null || per == null) {
            return appeng.container.implementations.ContainerOptimizePatterns
                .getBitMultiplier(currentLegacy, perLegacy, maximumLegacy);
        }
        if (current.signum() <= 0 || per.signum() <= 0 || maximumLegacy <= 0) return 0;
        final BigInteger crafted = current.multiply(per);
        BigInteger divisor = per;
        int multiplier = 0;
        final BigInteger maximum = BigInteger.valueOf(maximumLegacy);
        while (ceilDivide(crafted, divisor).compareTo(maximum) > 0 && multiplier < 62) {
            divisor = divisor.shiftLeft(1);
            multiplier++;
        }
        return multiplier;
    }

    /** Formats GuiOptimizePatterns' requestable values, including its shifted new-output line. */
    public static String formatOptimizerRequestable(final long legacy) {
        final BigInteger value = REQUESTABLE.get();
        int call = REQUESTABLE_FORMATS.get() == null ? 0 : REQUESTABLE_FORMATS.get();
        REQUESTABLE_FORMATS.set(call + 1);
        BigInteger exact = value;
        if (call > 0) {
            final Integer shift = OPTIMIZER_MULTIPLIER.get();
            if (shift != null) exact = exact.shiftLeft(shift);
        }
        if (call > 1) {
            REQUESTABLE.remove();
            REQUESTABLE_FORMATS.remove();
        }
        return exact == null || BigAEStackValues.fitsLong(exact) ? NumberFormat.getNumberInstance(Locale.US)
            .format(legacy) : BigNumberFormatter.formatExact(exact);
    }

    public static String formatPower(final long legacy, final boolean isRate) {
        final BigInteger value = take(REQUESTABLE);
        if (value == null || BigAEStackValues.fitsLong(value)) {
            return appeng.util.Platform.formatPowerLong(legacy, isRate);
        }
        return BigNumberFormatter.formatCompact(value) + (isRate ? "/t" : "");
    }

    /** Starts exact Network Status formatting. Modes: 0 consume, 1 item, 2 fluid, 3 essentia. */
    public static void beginNetworkDisplay(final int mode) {
        NETWORK_VALUES.set(new BigInteger[15]);
        NETWORK_MODE.set(mode);
        NETWORK_BYTE_CURSOR.set(0);
        NETWORK_INTEGER_CURSOR.set(0);
    }

    public static long captureNetworkBytes(final int slot, final BigInteger value, final long legacy) {
        BigInteger[] values = NETWORK_VALUES.get();
        if (values == null) {
            values = new BigInteger[15];
            NETWORK_VALUES.set(values);
        }
        values[slot] = value;
        return Double.doubleToLongBits(value.doubleValue());
    }

    public static long captureNetworkInteger(final int slot, final BigInteger value) {
        BigInteger[] values = NETWORK_VALUES.get();
        if (values == null) {
            values = new BigInteger[15];
            NETWORK_VALUES.set(values);
        }
        values[slot] = value;
        return BigAEStackValues.saturatedLong(value);
    }

    public static String formatNetworkBytes(final double legacy) {
        BigInteger[] values = NETWORK_VALUES.get();
        Integer cursor = NETWORK_BYTE_CURSOR.get();
        int index = cursor == null ? 0 : cursor;
        NETWORK_BYTE_CURSOR.set(index + 1);
        if (values == null) return appeng.util.Platform.formatByteDouble(legacy);
        final int[] slots = { 0, 1, 2, 3, 4, 5 };
        BigInteger exact = index < slots.length ? values[slots[index]] : null;
        return exact == null ? appeng.util.Platform.formatByteDouble(legacy) : BigNumberFormatter.formatBytes(exact);
    }

    public static String formatNetworkInteger(final long legacy) {
        BigInteger[] values = NETWORK_VALUES.get();
        Integer mode = NETWORK_MODE.get();
        Integer cursor = NETWORK_INTEGER_CURSOR.get();
        int index = cursor == null ? 0 : cursor;
        NETWORK_INTEGER_CURSOR.set(index + 1);
        if (values == null || mode == null) return Long.toString(legacy);
        final int[][] slots = { { 6, 7, 8 }, { 6, 7, 8 }, { 9, 10, 11 }, { 12, 13, 14 } };
        int[] selected = slots[Math.max(0, Math.min(mode, slots.length - 1))];
        BigInteger exact = index < selected.length ? values[selected[index]] : null;
        return exact == null ? Long.toString(legacy) : BigNumberFormatter.formatExact(exact);
    }

    public static void clearScanValues() {
        SCAN_TYPES_USED.remove();
        SCAN_TYPES_TOTAL.remove();
        SCAN_BYTES_USED.remove();
        SCAN_BYTES_TOTAL.remove();
    }

    public static long captureScanTypesUsed(final BigInteger value) {
        SCAN_TYPES_USED.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static long captureScanTypesTotal(final BigInteger value) {
        SCAN_TYPES_TOTAL.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static long captureScanBytesUsed(final BigInteger value) {
        SCAN_BYTES_USED.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static long captureScanBytesTotal(final BigInteger value) {
        SCAN_BYTES_TOTAL.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static String formatScanInteger(final long legacy) {
        BigInteger value = SCAN_TYPES_USED.get();
        if (value != null) {
            SCAN_TYPES_USED.remove();
            return BigNumberFormatter.formatExact(value);
        }
        value = SCAN_TYPES_TOTAL.get();
        if (value != null) {
            SCAN_TYPES_TOTAL.remove();
            return BigNumberFormatter.formatExact(value);
        }
        return Long.toString(legacy);
    }

    public static String formatScanBytes(final double legacy) {
        BigInteger value = SCAN_BYTES_USED.get();
        if (value != null) {
            SCAN_BYTES_USED.remove();
            return BigNumberFormatter.formatBytes(value);
        }
        value = SCAN_BYTES_TOTAL.get();
        if (value != null) {
            SCAN_BYTES_TOTAL.remove();
            return BigNumberFormatter.formatBytes(value);
        }
        return appeng.util.Platform.formatByteDouble(legacy);
    }

    public static String formatScanTypes(final long usedLegacy, final long totalLegacy) {
        BigInteger used = SCAN_TYPES_USED.get();
        BigInteger total = SCAN_TYPES_TOTAL.get();
        if (used != null) SCAN_TYPES_USED.remove();
        if (total != null) SCAN_TYPES_TOTAL.remove();
        return (used == null ? Long.toString(usedLegacy) : BigNumberFormatter.formatExact(used)) + " / "
            + (total == null ? Long.toString(totalLegacy) : BigNumberFormatter.formatExact(total));
    }

    public static Object[] formatScanTypeArgs(final Object[] args) {
        final long used = args != null && args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).longValue()
            : 0L;
        final long total = args != null && args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).longValue()
            : 0L;
        return new Object[] { formatScanInteger(used), formatScanInteger(total) };
    }

    public static long captureAmount(final BigInteger value) {
        AMOUNT.set(value);
        return BigAEStackValues.saturatedLong(value);
    }

    public static String formatWideAmount(final long legacy) {
        final BigInteger value = AMOUNT.get();
        return value == null || BigAEStackValues.fitsLong(value)
            ? ReadableNumberConverter.INSTANCE.toWideReadableForm(legacy)
            : BigNumberFormatter.formatCompact(value);
    }

    public static String formatExactAmount(final long legacy) {
        final BigInteger value = take(AMOUNT);
        return value == null || BigAEStackValues.fitsLong(value) ? NumberFormat.getNumberInstance(Locale.US)
            .format(legacy) : BigNumberFormatter.formatExact(value);
    }

    public static double captureReport(final BigInteger value, final double legacy) {
        REPORT.set(value);
        return legacy;
    }

    public static double captureReport(final IAEStack<?> stack, final double legacy) {
        return captureReport(BigAEStackValues.get(stack), legacy);
    }

    public static String formatReport(final double legacy) {
        BigInteger value = take(REPORT);
        if (value == null) value = take(STACK);
        return value == null || BigAEStackValues.fitsLong(value) ? appeng.util.Platform.fmt(legacy)
            : BigNumberFormatter.formatCompact(value);
    }

    public static long captureReportLong(final BigInteger value, final long legacy) {
        REPORT.set(value);
        return legacy;
    }

    public static void clear() {
        STACK_INFINITE.remove();
        TOTAL_INFINITE.remove();
        PENDING_INFINITE.remove();
        STACK.remove();
        CRAFTS.remove();
        REQUESTABLE.remove();
        REPORT.remove();
        TOTAL.remove();
        PENDING_SET.remove();
        AMOUNT.remove();
        OPTIMIZER_WIDE_CALLS.remove();
        OPTIMIZER_MULTIPLIER.remove();
        REQUESTABLE_FORMATS.remove();
        NETWORK_VALUES.remove();
        NETWORK_MODE.remove();
        NETWORK_BYTE_CURSOR.remove();
        NETWORK_INTEGER_CURSOR.remove();
        clearScanValues();
    }

    public static String longString(final long legacy) {
        final BigInteger value = take(STACK);
        return value == null || BigAEStackValues.fitsLong(value) ? Long.toString(legacy)
            : BigNumberFormatter.formatCompact(value);
    }

    public static void beginTotal() {
        TOTAL_INFINITE.set(false);
        PENDING_INFINITE.remove();
        TOTAL.set(BigInteger.ZERO);
        PENDING_SET.remove();
    }

    public static long addToTotal(final IAEStack<?> stack) {
        if (BigAEStackValues.isInfinite(stack)) TOTAL_INFINITE.set(true);
        final BigInteger value = BigAEStackValues.get(stack);
        final BigInteger total = TOTAL.get();
        TOTAL.set((total == null ? BigInteger.ZERO : total).add(value));
        return BigAEStackValues.saturatedLong(value);
    }

    public static long finishTotal(final long legacy) {
        PENDING_INFINITE.set(takeFlag(TOTAL_INFINITE));
        final BigInteger total = TOTAL.get();
        TOTAL.remove();
        final BigInteger exact = total == null ? BigInteger.valueOf(legacy) : total;
        PENDING_SET.set(exact);
        return BigAEStackValues.saturatedLong(exact);
    }

    public static IAEStack<?> setPendingTotal(final IAEStack<?> stack, final long legacy) {
        if (Boolean.TRUE.equals(takeFlag(PENDING_INFINITE))) {
            PENDING_SET.remove();
            ((com.silvia.apeiron.ae.stack.InfiniteAEStack) stack).setInfinite(true);
            return stack;
        }
        final BigInteger exact = PENDING_SET.get();
        PENDING_SET.remove();
        return exact == null ? stack.setStackSize(legacy) : BigAEStackValues.set(stack, exact);
    }

    public static IAEStack<?> setCapturedStack(final IAEStack<?> stack, final long legacy) {
        if (Boolean.TRUE.equals(takeFlag(STACK_INFINITE))) {
            STACK.remove();
            ((com.silvia.apeiron.ae.stack.InfiniteAEStack) stack).setInfinite(true);
            return stack;
        }
        final BigInteger exact = take(STACK);
        return exact == null ? stack.setStackSize(legacy) : BigAEStackValues.set(stack, exact);
    }

    private static Boolean takeFlag(ThreadLocal<Boolean> flag) {
        Boolean value = flag.get();
        flag.remove();
        return value;
    }

    private static BigInteger take(final ThreadLocal<BigInteger> local) {
        final BigInteger value = local.get();
        local.remove();
        return value;
    }

    private static int powerOfTwoExponent(final long value) {
        if (value <= 0 || (value & (value - 1)) != 0) return -1;
        return Long.numberOfTrailingZeros(value);
    }

    private static BigInteger ceilDivide(final BigInteger numerator, final BigInteger denominator) {
        return numerator.add(denominator)
            .subtract(BigInteger.ONE)
            .divide(denominator);
    }
}
