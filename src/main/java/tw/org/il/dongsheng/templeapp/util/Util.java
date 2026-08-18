package tw.org.il.dongsheng.templeapp.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.time.chrono.MinguoDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public final class Util {

    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private Util(){}

    public static String stringFormat(int str) {
        String result = String.format("%07d", str);
        return result;
    }

    public static String stringReplaceZero(String str) {
        String result = str.replaceFirst("^0+", "");
        return result;
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty() || s.trim().isBlank();
    }

    public static String emptyToDefault(String value, String defaultValue) {
        return isEmpty(value) ? defaultValue : value;
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    public static String trimLeadingZeros(String value) {
        String result = value.replaceFirst("^0+", "");
        return result.isEmpty() ? "0" : result;
    }

    public static Integer parseInteger(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static <T> ObservableList<T> toObservableList(Optional<T> optional) {
        return optional.map(FXCollections::observableArrayList)
                .orElseGet(FXCollections::observableArrayList);
    }

    public static <T> ObservableList<T> toObservableList(List<T> list) {
        return FXCollections.observableArrayList(list);
    }

    public static String currentRocDate() {
        LocalDate today = LocalDate.now();
        return String.format("%03d.%02d.%02d", today.getYear() - 1911, today.getMonthValue(), today.getDayOfMonth());
    }

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().replace('/', '.').replace('-', '.').split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return LocalDate.of(year < 1912 ? year + 1911 : year, month, day);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String convertToDbDateString(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DB_DATE_FORMATTER);
    }
}
