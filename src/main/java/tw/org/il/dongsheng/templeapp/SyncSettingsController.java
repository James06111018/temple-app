package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.sync.SyncConfig;
import tw.org.il.dongsheng.templeapp.sync.SyncResult;
import tw.org.il.dongsheng.templeapp.sync.SyncService;
import tw.org.il.dongsheng.templeapp.sync.SyncServiceFactory;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncSettingsController {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    @FXML private BorderPane rootPane;
    @FXML private TextField remoteBaseUrlField;
    @FXML private TextField jdbcUrlField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button downloadAllButton;
    @FXML private Button testConnectionButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Label syncStatusLabel;
    @FXML private TextArea syncProgressArea;

    private boolean saved;
    private boolean syncRunning;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);

    @FXML
    private void initialize() {
        Properties properties = SyncConfig.load();
        remoteBaseUrlField.setText(defaultIfBlank(properties.getProperty("sync.remote.baseUrl"), "https://ep-hidden-hall-azu6xplg-pooler.c-3.ap-southeast-1.aws.neon.tech"));
        jdbcUrlField.setText(defaultIfBlank(properties.getProperty("sync.remote.jdbcUrl"), "jdbc:postgresql://ep-hidden-hall-azu6xplg-pooler.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"));
        usernameField.setText(defaultIfBlank(properties.getProperty("sync.remote.username"), "neondb_owner"));
        passwordField.setText(properties.getProperty("sync.remote.password", ""));
        boolean admin = AuthSession.isAdmin();
        downloadAllButton.setVisible(admin);
        downloadAllButton.setManaged(admin);
        syncStatusLabel.setVisible(admin);
        syncStatusLabel.setManaged(admin);
        syncProgressArea.setVisible(admin);
        syncProgressArea.setManaged(admin);
        Platform.runLater(this::protectWindowWhileSyncing);
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
    private void onDownloadAll() {
        if (!AuthSession.isAdmin()) {
            AlertDialog.showWarning("同步設定", "只有 admin 可以從雲端下載完整資料。");
            return;
        }

        Properties properties = buildProperties();
        if (!SyncConfig.hasRequiredSettings(properties)) {
            AlertDialog.showWarning("同步設定", "請至少填入 JDBC URL、帳號與密碼。");
            return;
        }
        if (!AlertDialog.showConfirm("從雲端更新資料", "將先備份本機資料庫，再從雲端下載所有目前支援的資料；不會上傳本機資料。確定要繼續嗎？")) {
            return;
        }

        SyncConfig.save(properties);
        cancellationRequested.set(false);
        syncProgressArea.clear();
        setSyncRunning(true, "正在從雲端下載，請勿關閉程式...");
        appendProgress("準備從雲端下載完整資料");
        Thread syncThread = new Thread(() -> {
            try {
                appendProgress("備份本機資料庫...");
                java.nio.file.Path backupPath = SQLiteDatabaseManager.getInstance().createBackup();
                appendProgress("本機備份完成：" + backupPath);
                SyncService service = SyncServiceFactory.create(properties);
                SyncResult result = service.downloadAllFromCloud(this::appendProgress, cancellationRequested::get);
                Platform.runLater(() -> {
                    boolean cancelled = "同步已取消。".equals(result.getMessage());
                    setSyncRunning(false, result.isSuccess() ? "雲端資料下載完成。" : cancelled ? "下載已取消。" : "雲端資料下載失敗。");
                    if (result.isSuccess()) {
                        AlertDialog.showInfo("從雲端更新資料", result.getMessage());
                    } else if (cancelled) {
                        AlertDialog.showInfo("從雲端更新資料", "下載已在安全停止點取消。");
                    } else {
                        AlertDialog.showError("從雲端更新資料", result.getMessage());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setSyncRunning(false, "雲端資料下載失敗。");
                    AlertDialog.showError("從雲端更新資料", "雲端資料下載失敗：" + ex.getMessage());
                });
            }
        }, "cloud-download-all");
        syncThread.setDaemon(true);
        syncThread.start();
    }

    private void setSyncRunning(boolean running, String status) {
        syncRunning = running;
        downloadAllButton.setDisable(running);
        testConnectionButton.setDisable(running);
        saveButton.setDisable(running);
        remoteBaseUrlField.setDisable(running);
        jdbcUrlField.setDisable(running);
        usernameField.setDisable(running);
        passwordField.setDisable(running);
        syncStatusLabel.setText(status);
        cancelButton.setDisable(false);
        cancelButton.setText(running ? "取消同步" : "取消");
    }

    private void appendProgress(String message) {
        Platform.runLater(() -> {
            syncStatusLabel.setText(message);
            syncProgressArea.appendText("[" + LocalTime.now().format(TIME_FORMATTER) + "] " + message + System.lineSeparator());
            syncProgressArea.positionCaret(syncProgressArea.getLength());
        });
    }

    @FXML
    private void onCancel() {
        if (syncRunning) {
            requestCancellation();
            return;
        }
        saved = false;
        close();
    }

    private void requestCancellation() {
        if (cancellationRequested.compareAndSet(false, true)) {
            cancelButton.setDisable(true);
            appendProgress("已要求取消，等待目前資料表處理完成...");
        }
    }

    private void protectWindowWhileSyncing() {
        if (rootPane.getScene() == null || rootPane.getScene().getWindow() == null) {
            return;
        }
        rootPane.getScene().getWindow().setOnCloseRequest(event -> {
            if (syncRunning) {
                event.consume();
                requestCancellation();
            }
        });
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
