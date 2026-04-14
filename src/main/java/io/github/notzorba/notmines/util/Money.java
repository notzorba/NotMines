package io.github.notzorba.notmines.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000L);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal BILLION = BigDecimal.valueOf(1_000_000_000L);

    private Money() {
    }

    public static long parseMinor(final String input, final int scale) {
        try {
            return parseMajor(input)
                .setScale(scale, RoundingMode.HALF_UP)
                .movePointRight(scale)
                .longValueExact();
        } catch (final NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid money value: " + input, exception);
        }
    }

    private static BigDecimal parseMajor(final String input) {
        final String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            throw new NumberFormatException("Money value may not be blank.");
        }

        final String normalized = trimmed.replace(",", "").replace("_", "");
        final char suffix = Character.toLowerCase(normalized.charAt(normalized.length() - 1));

        final BigDecimal multiplier = switch (suffix) {
            case 'k' -> THOUSAND;
            case 'm' -> MILLION;
            case 'b' -> BILLION;
            default -> BigDecimal.ONE;
        };

        final String numericPart = multiplier == BigDecimal.ONE
            ? normalized
            : normalized.substring(0, normalized.length() - 1).trim();
        if (numericPart.isEmpty()) {
            throw new NumberFormatException("Money value is missing its numeric portion.");
        }

        return new BigDecimal(numericPart).multiply(multiplier);
    }

    public static double toMajor(final long amountMinor, final int scale) {
        return BigDecimal.valueOf(amountMinor, scale).doubleValue();
    }

    public static String formatPlainMinor(final long amountMinor, final int scale) {
        return BigDecimal.valueOf(amountMinor, scale)
            .setScale(scale, RoundingMode.UNNECESSARY)
            .toPlainString();
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
