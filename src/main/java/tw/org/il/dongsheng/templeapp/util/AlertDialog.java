package tw.org.il.dongsheng.templeapp.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public final class AlertDialog {

    private AlertDialog(){}

    public static void showInfo(String title, String msg) {
        String showTitle = "訊息";
        if(!Util.isEmpty(title)) showTitle = title;
        showAlert(Alert.AlertType.INFORMATION, showTitle, msg);
    }

    /**
     * 顯示錯誤訊息
     */
    public static void showError(String title, String message) {
        String showTitle = "錯誤";
        if(!Util.isEmpty(title)) showTitle = title;
        showAlert(Alert.AlertType.ERROR, showTitle, message);
    }

    public static void showWarning(String title, String message) {
        String showTitle = "警告";
        if(!Util.isEmpty(title)) showTitle = title;
        showAlert(Alert.AlertType.WARNING, showTitle, message);
    }

    public static boolean showConfirm(String title, String message) {
        String showTitle = "確認";
        if(!Util.isEmpty(title)) showTitle = title;

        ButtonType yesButton = new ButtonType("是(Y)");
        ButtonType noButton = new ButtonType("否(N)");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, yesButton, noButton);
        alert.setTitle(showTitle);
        alert.setHeaderText(null);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yesButton;
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
