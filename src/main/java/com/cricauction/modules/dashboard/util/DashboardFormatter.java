package com.cricauction.modules.dashboard.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DashboardFormatter {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter AUCTION_DATE_ONLY_LOCAL =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MATCH_SCHEDULE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE • h:mm a", Locale.ENGLISH).withZone(DEFAULT_ZONE);

    private DashboardFormatter() {}

    public static String formatBudget(BigDecimal amount) {
        if (amount == null) {
            return "₹0";
        }
        double val = amount.doubleValue();
        if (val >= 10_000_000) { // >= 1 Crore
            double cr = val / 10_000_000.0;
            return cr % 1.0 == 0 ? String.format(Locale.ENGLISH, "₹%.0f Cr", cr)
                                 : String.format(Locale.ENGLISH, "₹%.1f Cr", cr);
        } else if (val >= 100_000) { // >= 1 Lakh
            double l = val / 100_000.0;
            return l % 1.0 == 0 ? String.format(Locale.ENGLISH, "₹%.0fL", l)
                               : String.format(Locale.ENGLISH, "₹%.1fL", l);
        } else if (val >= 1_000) { // >= 1 Thousand
            double k = val / 1_000.0;
            return k % 1.0 == 0 ? String.format(Locale.ENGLISH, "₹%.0fK", k)
                               : String.format(Locale.ENGLISH, "₹%.1fK", k);
        } else {
            return "₹" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
    }

    public static String formatAuctionDateOnly(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return AUCTION_DATE_ONLY_LOCAL.format(localDate);
    }

    public static Instant toAuctionInstant(LocalDate auctionDate, String auctionTime) {
        if (auctionDate == null) {
            return null;
        }
        LocalTime time = LocalTime.of(19, 0); // default 7 PM
        if (auctionTime != null && !auctionTime.isBlank()) {
            try {
                String[] parts = auctionTime.split(":");
                int hour = Integer.parseInt(parts[0].trim());
                int min = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                time = LocalTime.of(hour, min);
            } catch (Exception ignored) {}
        }
        return LocalDateTime.of(auctionDate, time).atZone(DEFAULT_ZONE).toInstant();
    }

    public static String formatMatchSchedule(Instant instant) {
        if (instant == null) {
            return null;
        }
        return MATCH_SCHEDULE_FORMATTER.format(instant);
    }
}
