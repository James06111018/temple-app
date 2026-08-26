package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.sync.SyncConfig;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SyncSettingsController {
    @FXML private TextField remoteBaseUrlField;
    @FXML private TextField jdbcUrlField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    private boolean saved;

    @FXML
    private void initialize() {
        Properties properties = SyncConfig.load();
        remoteBaseUrlField.setText(defaultIfBlank(properties.getProperty("sync.remote.baseUrl"), "https://ep-hidden-hall-azu6xplg-pooler.c-3.ap-southeast-1.aws.neon.tech"));
        jdbcUrlField.setText(defaultIfBlank(properties.getProperty("sync.remote.jdbcUrl"), "jdbc:postgresql://ep-hidden-hall-azu6xplg-pooler.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"));
        usernameField.setText(defaultIfBlank(properties.getProperty("sync.remote.username"), "neondb_owner"));
        passwordField.setText(properties.getProperty("sync.remote.password", ""));
    }

    @FXML
    private void onSave() {
        Properties properties = new Properties();
        properties.setProperty("sync.remote.baseUrl", emptyToBlank(remoteBaseUrlField.getText()));
        properties.setProperty("sync.remote.jdbcUrl", emptyToBlank(jdbcUrlField.getText()));
        properties.setProperty("sync.remote.username", emptyToBlank(usernameField.getText()));
        properties.setProperty("sync.remote.password", emptyToBlank(passwordField.getText()));

        if (!SyncConfig.hasRequiredSettings(properties)) {
            AlertDialog.showWarning("同步設定", "請至少填入 JDBC URL、帳號與密碼。");
            return;
        }

        SyncConfig.save(properties);
        saved = true;
        close();
    }

    @FXML
    private void onTestConnection() {
        Properties properties = buildProperties();
        if (!SyncConfig.hasRequiredSettings(properties)) {
            AlertDialog.showWarning("同步設定", "請至少填入 JDBC URL、帳號與密碼。");
            return;
        }

        try {
            Class.forName("org.postgresql.Driver");
            try (Connection connection = DriverManager.getConnection(
                    properties.getProperty("sync.remote.jdbcUrl"),
                    properties.getProperty("sync.remote.username"),
                    properties.getProperty("sync.remote.password"));
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select 1 as ok")) {
                if (resultSet.next() && resultSet.getInt("ok") == 1) {
                    AlertDialog.showInfo("同步設定", "連線測試成功。");
                } else {
                    AlertDialog.showWarning("同步設定", "連線成功，但測試查詢沒有回傳資料。");
                }
            }
        } catch (Exception ex) {
            AlertDialog.showError("同步設定", "連線測試失敗：" + ex.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        saved = false;
        close();
    }

    public boolean isSaved() {
        return saved;
    }

    private String emptyToBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    private Properties buildProperties() {
        Properties properties = new Properties();
        properties.setProperty("sync.remote.baseUrl", emptyToBlank(remoteBaseUrlField.getText()));
        properties.setProperty("sync.remote.jdbcUrl", emptyToBlank(jdbcUrlField.getText()));
        properties.setProperty("sync.remote.username", emptyToBlank(usernameField.getText()));
        properties.setProperty("sync.remote.password", emptyToBlank(passwordField.getText()));
        return properties;
    }

    private void close() {
        ((Stage) remoteBaseUrlField.getScene().getWindow()).close();
    }
}
