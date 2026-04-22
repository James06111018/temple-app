package tw.org.il.dongsheng.templeapp.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;

public final class Util {

    private Util(){}

    public static String stringFormat(int id) {
        String result = String.format("%07d", id);
        return result;
    }

    public static <T> ObservableList<T> toObservableList(Optional<T> optional) {
        return optional.map(FXCollections::observableArrayList)
                .orElseGet(FXCollections::observableArrayList);
    }

    public static <T> ObservableList<T> toObservableList(List<T> list) {
        return FXCollections.observableArrayList(list);
    }
}
