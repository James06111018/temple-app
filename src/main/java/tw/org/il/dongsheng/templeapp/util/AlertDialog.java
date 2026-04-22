package tw.org.il.dongsheng.templeapp.util;

import javafx.scene.control.Alert;

public final class AlertDialog {

    private AlertDialog(){}

    public static void showInfo(String title, String msg) {
        String showTitle = "訊息";
        if(!title.isEmpty()) showTitle = title;
        showAlert(Alert.AlertType.INFORMATION, showTitle, msg);
    }

    /**
     * 顯示錯誤訊息
     */
    public static void showError(String title, String message) {
        String showTitle = "錯誤";
        if(!title.isEmpty()) showTitle = title;
        showAlert(Alert.AlertType.ERROR, showTitle, message);
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
