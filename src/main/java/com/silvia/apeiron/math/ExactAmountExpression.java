package com.silvia.apeiron.math;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Exact rational arithmetic for decimal, scientific and arithmetic quantity input. */
public final class ExactAmountExpression {

    private final String text;
    private int cursor;

    private ExactAmountExpression(String text) {
        this.text = text.replace(",", "")
            .replaceAll("\\s+", "");
    }

    public static BigInteger parse(String text) {
        if (text == null) throw new NumberFormatException("Missing quantity");
        ExactAmountExpression parser = new ExactAmountExpression(text);
        Fraction value = parser.sum();
        if (parser.cursor != parser.text.length()) throw new NumberFormatException("Invalid quantity");
        BigInteger[] qr = value.num.abs()
            .divideAndRemainder(value.den);
        if (qr[1].shiftLeft(1)
            .compareTo(value.den) >= 0) qr[0] = qr[0].add(BigInteger.ONE);
        return value.num.signum() < 0 ? qr[0].negate() : qr[0];
    }

    private boolean take(char token) {
        if (cursor >= text.length() || text.charAt(cursor) != token) return false;
        cursor++;
        return true;
    }

    private Fraction sum() {
        Fraction value = product();
        while (true) {
            if (take('+')) value = value.add(product());
            else if (take('-')) value = value.add(product().negate());
            else return value;
        }
    }

    private Fraction product() {
        Fraction value = unary();
        while (true) {
            if (take('*')) value = value.multiply(unary());
            else if (take('/')) value = value.multiply(unary().inverse());
            else return value;
        }
    }

    private Fraction unary() {
        if (take('+')) return unary();
        if (take('-')) return unary().negate();
        Fraction value = atom();
        if (take('^')) {
            Fraction power = unary();
            if (!power.den.equals(BigInteger.ONE)) throw new NumberFormatException("Noninteger exponent");
            int exponent = power.num.intValueExact();
            if (exponent == Integer.MIN_VALUE) throw new NumberFormatException("Exponent too large");
            if (exponent < 0) {
                value = value.inverse();
                exponent = -exponent;
            }
            value = new Fraction(value.num.pow(exponent), value.den.pow(exponent));
        }
        return value;
    }

    private Fraction atom() {
        if (take('(')) {
            Fraction value = sum();
            if (!take(')')) throw new NumberFormatException("Missing closing parenthesis");
            return value;
        }
        int start = cursor;
        while (cursor < text.length() && (Character.isDigit(text.charAt(cursor)) || text.charAt(cursor) == '.'))
            cursor++;
        if (cursor < text.length() && (text.charAt(cursor) == 'e' || text.charAt(cursor) == 'E')) {
            cursor++;
            if (!take('+')) take('-');
            while (cursor < text.length() && Character.isDigit(text.charAt(cursor))) cursor++;
        }
        if (cursor == start) throw new NumberFormatException("Missing number");
        BigDecimal decimal = new BigDecimal(text.substring(start, cursor));
        return decimal.scale() < 0 ? new Fraction(
            decimal.unscaledValue()
                .multiply(BigInteger.TEN.pow(-decimal.scale())),
            BigInteger.ONE) : new Fraction(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()));
    }

    private static final class Fraction {

        private final BigInteger num;
        private final BigInteger den;

        private Fraction(BigInteger num, BigInteger den) {
            if (den.signum() == 0) throw new ArithmeticException("Division by zero");
            BigInteger gcd = num.gcd(den);
            this.num = den.signum() < 0 ? num.divide(gcd)
                .negate() : num.divide(gcd);
            this.den = den.abs()
                .divide(gcd);
        }

        private Fraction add(Fraction other) {
            return new Fraction(
                num.multiply(other.den)
                    .add(other.num.multiply(den)),
                den.multiply(other.den));
        }

        private Fraction multiply(Fraction other) {
            return new Fraction(num.multiply(other.num), den.multiply(other.den));
        }

        private Fraction negate() {
            return new Fraction(num.negate(), den);
        }

        private Fraction inverse() {
            return new Fraction(den, num);
        }
    }
}
