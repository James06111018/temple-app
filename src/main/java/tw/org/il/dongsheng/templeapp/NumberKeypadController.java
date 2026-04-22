package tw.org.il.dongsheng.templeapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class NumberKeypadController {

    @FXML
    private TextField displayField;

    private Consumer<String> onConfirmCallback;

    public void setOnConfirm(Consumer<String> callback) {
        this.onConfirmCallback = callback;
    }

    @FXML
    public void onNumber(ActionEvent event) {
        Button btn = (Button) event.getSource();
        displayField.appendText(btn.getText());
    }

    @FXML
    private void onClear() {
        displayField.clear();
    }

    @FXML
    private void onBackspace() {
        String text = displayField.getText();
        if (!text.isEmpty()) {
            displayField.setText(text.substring(0, text.length() - 1));
        }
    }

    @FXML
    private void onConfirm() {
        if (onConfirmCallback != null) {
            onConfirmCallback.accept(displayField.getText());
        }

        // 關閉視窗
        Stage stage = (Stage) displayField.getScene().getWindow();
        stage.close();
    }

}
