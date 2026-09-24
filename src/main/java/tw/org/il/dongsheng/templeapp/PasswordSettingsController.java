package tw.org.il.dongsheng.templeapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.sync.SyncResult;
import tw.org.il.dongsheng.templeapp.sync.SyncService;
import tw.org.il.dongsheng.templeapp.sync.SyncServiceFactory;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

public class PasswordSettingsController {
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private Button changePasswordButton;
    @FXML private Label statusLabel;

    @FXML
    private void onChangePassword() {
        String oldPassword = oldPasswordField.getText();
        String newPassword = newPasswordField.getText();
        if (oldPassword == null || oldPassword.isEmpty()) {
            AlertDialog.showWarning("密碼設定", "請輸入原密碼。");
            return;
        }
        if (newPassword == null || !newPassword.matches("[A-Za-z0-9]{4,10}")) {
            AlertDialog.showWarning("密碼設定", "新密碼必須為 4 至 10 個英文字母或數字。");
            return;
        }
        if (newPassword.equals(oldPassword)) {
            AlertDialog.showWarning("密碼設定", "新密碼不可與原密碼相同。");
            return;
        }
        if (AuthSession.getCurrentUser() == null) {
            AlertDialog.showError("密碼設定", "目前沒有登入使用者。");
            return;
        }

        setRunning(true, "正在更新 Neon 密碼...");
        Thread thread = new Thread(() -> {
            SyncResult result;
            try {
                SyncService service = SyncServiceFactory.create(null);
                result = service.changePassword(
                        AuthSession.getCurrentUser().getUsername(),
                        oldPassword,
                        newPassword
                );
            } catch (Exception ex) {
                result = new SyncResult(false, "密碼變更失敗：" + ex.getMessage());
            }
            SyncResult finalResult = result;
            Platform.runLater(() -> {
                setRunning(false, finalResult.getMessage());
                if (finalResult.isSuccess()) {
                    AlertDialog.showInfo("密碼設定", finalResult.getMessage());
                    close();
                } else {
                    AlertDialog.showError("密碼設定", finalResult.getMessage());
                }
            });
        }, "password-change");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void setRunning(boolean running, String message) {
        oldPasswordField.setDisable(running);
        newPasswordField.setDisable(running);
        changePasswordButton.setDisable(running);
        statusLabel.setText(message);
    }

    private void close() {
        ((Stage) oldPasswordField.getScene().getWindow()).close();
    }
}
