package com.silvia.apeiron.ae.stack;

import java.math.BigInteger;

import appeng.api.storage.data.IAEStack;

/** Common exact-count operations for all AE stack types supported by Apeiron. */
public final class BigAEStackValues {

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private BigAEStackValues() {}

    public static boolean isInfinite(IAEStack<?> stack) {
        return stack instanceof InfiniteAEStack && ((InfiniteAEStack) stack).isInfinite();
    }

    public static BigInteger get(final IAEStack<?> stack) {
        if (stack == null) return BigInteger.ZERO;
        if (stack instanceof BigAEStack) {
            return ((BigAEStack) stack).getStackSizeBig();
        }
        return BigInteger.valueOf(stack.getStackSize());
    }

    public static BigInteger getCountRequestable(final IAEStack<?> stack) {
        if (stack instanceof BigAERequestableStack) {
            return ((BigAERequestableStack) stack).getCountRequestableBig();
        }
        return BigInteger.valueOf(stack.getCountRequestable());
    }

    public static BigInteger getCountRequestableCrafts(final IAEStack<?> stack) {
        if (stack instanceof BigAERequestableStack) {
            return ((BigAERequestableStack) stack).getCountRequestableCraftsBig();
        }
        return BigInteger.valueOf(stack.getCountRequestableCrafts());
    }

    public static boolean isBig(final IAEStack<?> stack) {
        return stack instanceof BigAEStack && ((BigAEStack) stack).isStackSizeBig();
    }

    public static boolean isBigValue(final BigInteger value) {
        return !fitsLong(value);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static IAEStack<?> set(final IAEStack<?> stack, final BigInteger value) {
        if (stack instanceof BigAEStack) {
            return ((BigAEStack) stack).setStackSizeBig(value);
        }
        if (!fitsLong(value)) {
            throw new ArithmeticException("AE stack does not support an exact count: " + value);
        }
        return ((IAEStack) stack).setStackSize(value.longValue());
    }

    public static int compare(final IAEStack<?> left, final IAEStack<?> right) {
        if (isInfinite(left) || isInfinite(right)) return Boolean.compare(isInfinite(left), isInfinite(right));
        if (!isBig(left) && !isBig(right)) return Long.compare(left.getStackSize(), right.getStackSize());
        return get(left).compareTo(get(right));
    }

    @SuppressWarnings("unchecked")
    public static <T extends IAEStack> T copyWithSize(final T stack, final BigInteger value) {
        return (T) set(stack.copy(), value);
    }

    public static void addStorage(final IAEStack<?> target, final IAEStack<?> source) {
        if (isInfinite(target)) return;
        if (isInfinite(source)) {
            ((InfiniteAEStack) target).setInfinite(true);
            return;
        }
        if (isBig(source)) {
            set(target, get(target).add(get(source)));
        } else {
            target.incStackSize(source.getStackSize());
        }
    }

    public static void addRequestable(final IAEStack<?> target, final IAEStack<?> source) {
        if (target instanceof BigAERequestableStack) {
            BigAERequestableStack exact = (BigAERequestableStack) target;
            exact.incCountRequestableBig(getCountRequestable(source));
            exact.incCountRequestableCraftsBig(getCountRequestableCrafts(source));
        } else {
            target.setCountRequestable(Math.addExact(target.getCountRequestable(), source.getCountRequestable()));
            target.setCountRequestableCrafts(
                Math.addExact(target.getCountRequestableCrafts(), source.getCountRequestableCrafts()));
        }
    }

    public static BigInteger min(final IAEStack<?> left, final IAEStack<?> right) {
        if (isInfinite(left)) return get(right);
        if (isInfinite(right)) return get(left);
        return get(left).min(get(right));
    }

    public static BigInteger min(final BigInteger left, final BigInteger right) {
        return left.min(right);
    }

    public static boolean fitsLong(final BigInteger value) {
        return value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
    }

    public static long saturatedLong(final BigInteger value) {
        if (value.compareTo(LONG_MAX) > 0) return Long.MAX_VALUE;
        if (value.compareTo(LONG_MIN) < 0) return Long.MIN_VALUE;
        return value.longValue();
    }
}
