package tw.org.il.dongsheng.templeapp.sync;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes timestamp strings exchanged between SQLite and PostgreSQL. */
public final class SyncTimestamp {
    private static final DateTimeFormatter SQLITE_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ROC_DATE = Pattern.compile(
            "^(\\d{2,3})[./-](\\d{1,2})[./-](\\d{1,2})(?:[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?)?$"
    );

    private SyncTimestamp() {
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().replace(' ', 'T');
        LocalDateTime rocDateTime = parseRocDateTime(value.trim());
        if (rocDateTime != null) {
            return rocDateTime;
        }
        try {
            return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // SQLite values normally have no offset.
        }

        try {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // Retain support for the app's original second-precision format.
        }

        return LocalDateTime.parse(value.trim(), SQLITE_SECONDS);
    }

    private static LocalDateTime parseRocDateTime(String value) {
        Matcher matcher = ROC_DATE.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        int rocYear = Integer.parseInt(matcher.group(1));
        int year = rocYear + 1911;
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = groupAsInt(matcher, 4);
        int minute = groupAsInt(matcher, 5);
        int second = groupAsInt(matcher, 6);
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    private static int groupAsInt(Matcher matcher, int group) {
        String value = matcher.group(group);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
