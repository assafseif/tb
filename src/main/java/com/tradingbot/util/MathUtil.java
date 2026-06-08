package com.tradingbot.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MathUtil {

    private MathUtil() {}

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException("Decimal places must be >= 0");
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static double percentChange(double from, double to) {
        if (from == 0) return 0;
        return ((to - from) / Math.abs(from)) * 100.0;
    }

    public static boolean isBetween(double value, double low, double high) {
        return value >= low && value <= high;
    }

    public static double safeDivide(double numerator, double denominator) {
        return denominator != 0 ? numerator / denominator : 0;
    }
}
