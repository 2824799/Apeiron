package com.silvia.apeiron.ae.reshuffle;

import java.lang.reflect.Constructor;
import java.math.BigInteger;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;

/** Lazily constructs AE2's private queue records; counts are assigned through transformed interfaces. */
public final class BigReshuffleObjects {

    private BigReshuffleObjects() {}

    private static final class Constructors {

        static final Constructor<?> PENDING = resolve("PendingInjection", IAEStack.class);
        static final Constructor<?> SOURCE = resolve("SourceContribution", IMEInventoryHandler.class, long.class);

        private static Constructor<?> resolve(final String name, final Class<?>... arguments) {
            try {
                final Constructor<?> result = Class.forName("appeng.helpers.ReshuffleTask$" + name)
                    .getDeclaredConstructor(arguments);
                result.setAccessible(true);
                return result;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot resolve AE reshuffle record " + name, error);
            }
        }
    }

    public static BigReshufflePending pending(final IAEStack<?> stack) {
        try {
            return (BigReshufflePending) Constructors.PENDING.newInstance(stack);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot create AE reshuffle queue record", error);
        }
    }

    public static BigReshuffleSource source(final IMEInventoryHandler source, final BigInteger amount) {
        try {
            final BigReshuffleSource result = (BigReshuffleSource) Constructors.SOURCE
                .newInstance(source, BigAEStackValues.saturatedLong(amount));
            result.setAmountBig(amount);
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot create AE reshuffle source record", error);
        }
    }
}
