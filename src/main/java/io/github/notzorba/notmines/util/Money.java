package io.github.notzorba.notmines.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {
    private Money() {
    }

    public static long parseMinor(final String input, final int scale) {
        try {
            return new BigDecimal(input.trim())
                .setScale(scale, RoundingMode.HALF_UP)
                .movePointRight(scale)
                .longValueExact();
        } catch (final NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid money value: " + input, exception);
        }
    }

    public static double toMajor(final long amountMinor, final int scale) {
        return BigDecimal.valueOf(amountMinor, scale).doubleValue();
    }

    public static long applyMultiplier(final long amountMinor, final double multiplier) {
        return BigDecimal.valueOf(amountMinor)
            .multiply(BigDecimal.valueOf(multiplier))
            .setScale(0, RoundingMode.DOWN)
            .longValue();
    }

    public static String formatMultiplier(final double multiplier) {
        final DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(multiplier);
    }

    public static String formatPercent(final double value) {
        final DecimalFormat format = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }
}
