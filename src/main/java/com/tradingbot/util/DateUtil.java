package com.tradingbot.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateUtil {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateUtil() {}

    public static LocalDateTime fromEpochMillis(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    public static LocalDateTime fromEpochSeconds(long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    public static String format(LocalDateTime dt) {
        return dt != null ? dt.format(DISPLAY_FMT) : "";
    }

    public static LocalDateTime startOfToday() {
        return LocalDateTime.now().toLocalDate().atStartOfDay();
    }
}
