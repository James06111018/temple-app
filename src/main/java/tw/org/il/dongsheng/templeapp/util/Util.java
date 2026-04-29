package tw.org.il.dongsheng.templeapp.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;

public final class Util {

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
}
