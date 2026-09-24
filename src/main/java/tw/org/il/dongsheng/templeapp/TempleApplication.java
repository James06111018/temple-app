package tw.org.il.dongsheng.templeapp;

import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.util.Duration;
import tw.org.il.dongsheng.templeapp.model.AppUser;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.sync.SyncResult;
import tw.org.il.dongsheng.templeapp.sync.SyncService;
import tw.org.il.dongsheng.templeapp.sync.SyncServiceFactory;
import tw.org.il.dongsheng.templeapp.sync.SyncConfig;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.update.AppUpdate;
import tw.org.il.dongsheng.templeapp.update.AppUpdateService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;

public class TempleApplication extends Application {
    private boolean loginRecordFinished = false;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicBoolean updateCheckStarted = new AtomicBoolean(false);
    private final AtomicReference<Stage> syncProgressStageRef = new AtomicReference<>();
    private final AtomicReference<Timeline> syncElapsedTimelineRef = new AtomicReference<>();
    private SyncService syncService;

    @Override
    public void start(Stage stage) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("tw.org.il.dongsheng.templeapp.strings");
        initializeAuthTables();
        ensureSyncSettings(stage);
        syncService = createSyncService();

        if (!refreshAuthenticationBeforeLogin()) {
            Platform.exit();
            return;
        }

        if (!shouldSkipLogin()) {
            LoginResult loginResult = showLogin(stage);
            if (loginResult.user() == null) {
                javafx.application.Platform.exit();
                return;
            }
            AuthSession.setCurrentUser(loginResult.user());
            AuthSession.setLoginRecordId(loginResult.loginRecordId());
            AuthSession.setFunctionCodes(new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance())
                    .findFunctionCodesByRole(loginResult.user().getRoleCode()));
        } else {
            AuthSession.setCurrentUser(new AppUser(0, "test", "測試使用者", SQLiteAuthRepository.ROLE_ADMIN, true));
            AuthSession.setFunctionCodes(new HashSet<>(new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance())
                    .findFunctionCodesByRole(SQLiteAuthRepository.ROLE_ADMIN)));
        }

        FXMLLoader fxmlLoader = new FXMLLoader(TempleApplication.class.getResource("view-index.fxml"), bundle);
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root, 1200, 800, Color.WHITE);
        stage.setTitle(bundle.getString("app.title"));
        stage.setScene(scene);
        stage.setMaximized(true);
//        stage.setMinHeight(1000);
//        stage.setMinWidth(700);
        stage.setOnCloseRequest(event -> {
            event.consume();
            runShutdownSyncAndClose(stage);
        });
        stage.show();
        runInitialSync(stage);
    }

    @Override
    public void stop() {
        finishLoginRecord();
    }

    public static void main(String[] args) {
        System.setProperty("glass.accessible.force", "false");
        launch(args);
    }

    private void initializeAuthTables() {
        try {
            new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance()).createTables();
        } catch (SQLException e) {
            throw new RuntimeException("初始化登入資料表失敗", e);
        }
    }

    private boolean refreshAuthenticationBeforeLogin() {
        SQLiteAuthRepository authRepository = new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance());
        SyncResult result = syncService == null
                ? new SyncResult(false, "同步服務建立失敗。")
                : syncService.refreshAuthenticationFromCloud();
        if (result.isSuccess()) {
            return true;
        }

        try {
            if (!authRepository.findAllUsers().isEmpty()) {
                AlertDialog.showWarning(
                        "登入資料",
                        result.getMessage() + System.lineSeparator() + "將使用本機已快取的登入帳號。"
                );
                return true;
            }
        } catch (SQLException ex) {
            AlertDialog.showError("登入資料", "檢查本機登入資料失敗：" + ex.getMessage());
            return false;
        }

        AlertDialog.showError(
                "登入資料",
                result.getMessage() + System.lineSeparator()
                        + "此電腦尚無可用的本機帳號，請檢查同步設定與網路連線後重新啟動。"
        );
        return false;
    }

    private boolean shouldSkipLogin() {
        String systemProperty = System.getProperty("temple.skipLogin");
        if (systemProperty != null) {
            return Boolean.parseBoolean(systemProperty);
        }

        String environmentValue = System.getenv("TEMPLE_SKIP_LOGIN");
        if (environmentValue != null) {
            return Boolean.parseBoolean(environmentValue);
        }

        return AppConfig.isSkipLogin();
    }

    private LoginResult showLogin(Stage owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(TempleApplication.class.getResource("login.fxml"));
        Parent root = loader.load();
        Stage loginStage = new Stage();
        loginStage.setTitle("登入");
        loginStage.setScene(new Scene(root));
        loginStage.initModality(Modality.APPLICATION_MODAL);
        loginStage.initOwner(owner);
        loginStage.showAndWait();

        LoginController controller = loader.getController();
        return new LoginResult(controller.getAuthenticatedUser(), controller.getLoginRecordId());
    }

    private void ensureSyncSettings(Stage owner) throws IOException {
        Properties properties = SyncConfig.load();
        if (SyncConfig.hasRequiredSettings(properties)) {
            return;
        }

        FXMLLoader loader = new FXMLLoader(TempleApplication.class.getResource("sync-settings.fxml"));
        Parent root = loader.load();
        Stage settingsStage = new Stage();
        settingsStage.setTitle("同步設定");
        settingsStage.setScene(new Scene(root));
        settingsStage.initModality(Modality.APPLICATION_MODAL);
        settingsStage.initOwner(owner);
        settingsStage.showAndWait();

        SyncSettingsController controller = loader.getController();
        if (!controller.isSaved()) {
            javafx.application.Platform.exit();
            throw new IllegalStateException("Sync settings are required before starting the app.");
        }
    }

    private void finishLoginRecord() {
        if (loginRecordFinished) {
            return;
        }
        loginRecordFinished = true;
        try {
            new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance()).finishLoginRecord(AuthSession.getLoginRecordId());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void runInitialSync(Stage stage) {
        PauseTransition delay = new PauseTransition(Duration.millis(800));
        delay.setOnFinished(event -> runSyncInBackground(false, stage));
        delay.play();
    }

    private void runShutdownSyncAndClose(Stage stage) {
        runSyncInBackground(true, stage);
    }

    private void runSyncInBackground(boolean closeAfter, Stage stage) {
        if (syncService == null || !syncing.compareAndSet(false, true)) {
            if (closeAfter && stage != null) {
                Platform.runLater(stage::close);
            } else if (!closeAfter) {
                runUpdateCheck(stage);
            }
            return;
        }

        Platform.runLater(() -> showSyncProgress(stage, closeAfter));
        Thread syncThread = new Thread(() -> {
            try {
                SyncResult result = syncService.syncNow();
                if (!result.isSuccess()) {
                    Platform.runLater(() -> AlertDialog.showWarning("同步", result.getMessage()));
                }
            } catch (Exception e) {
                Platform.runLater(() -> AlertDialog.showWarning("同步", "自動同步失敗：" + e.getMessage()));
            } finally {
                syncing.set(false);
                Platform.runLater(this::hideSyncProgress);
                if (closeAfter && stage != null) {
                    Platform.runLater(() -> {
                        finishLoginRecord();
                        stage.setOnCloseRequest(null);
                        stage.close();
                    });
                } else if (!closeAfter) {
                    runUpdateCheck(stage);
                }
            }
        }, closeAfter ? "sync-shutdown" : "sync-startup");
        syncThread.setDaemon(true);
        syncThread.start();
    }

    private void showSyncProgress(Stage owner, boolean closing) {
        if (syncProgressStageRef.get() != null) {
            return;
        }
        Stage progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            progressStage.initOwner(owner);
        }
        progressStage.setResizable(false);
        progressStage.setAlwaysOnTop(true);
        progressStage.setTitle("同步中");

        Label title = new Label(closing ? "關閉前同步資料中..." : "登入後同步資料中...");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label message = new Label("請稍候，資料正在與雲端同步。");
        Label elapsed = new Label("已執行 0 秒");
        elapsed.setStyle("-fx-text-fill: #666666;");

        long startMillis = System.currentTimeMillis();
        Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            long seconds = Math.max(0, (System.currentTimeMillis() - startMillis) / 1000);
            elapsed.setText("已執行 " + seconds + " 秒");
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        syncElapsedTimelineRef.set(timeline);

        VBox box = new VBox(10, title, message, elapsed);
        box.setStyle("-fx-padding: 20; -fx-alignment: center; -fx-background-color: white;");
        Scene scene = new Scene(box, 320, 140);
        progressStage.setScene(scene);
        syncProgressStageRef.set(progressStage);
        progressStage.show();
    }

    private void hideSyncProgress() {
        Timeline timeline = syncElapsedTimelineRef.getAndSet(null);
        if (timeline != null) {
            timeline.stop();
        }
        Stage progressStage = syncProgressStageRef.getAndSet(null);
        if (progressStage != null) {
            progressStage.close();
        }
    }

    private SyncService createSyncService() {
        try {
            return SyncServiceFactory.create(SyncConfig.load());
        } catch (Exception e) {
            AlertDialog.showWarning("同步", "建立同步服務失敗：" + e.getMessage());
            return null;
        }
    }

    private void runUpdateCheck(Stage owner) {
        if (!updateCheckStarted.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                AppUpdateService updateService = new AppUpdateService();
                updateService.findAvailableUpdate(AppConfig.getVersion())
                        .ifPresent(update -> Platform.runLater(() -> askToInstallUpdate(owner, updateService, update)));
            } catch (Exception ignored) {
                // 更新檢查失敗不應妨礙離線或日常使用。
            }
        }, "app-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void askToInstallUpdate(Stage owner, AppUpdateService updateService, AppUpdate update) {
        boolean accepted = AlertDialog.showConfirm(
                "發現新版",
                "目前版本：" + AppConfig.getVersion() + System.lineSeparator()
                        + "最新版本：" + update.version() + System.lineSeparator() + System.lineSeparator()
                        + "是否立即下載並安裝？安裝前程式會自動關閉。"
        );
        if (!accepted) {
            return;
        }

        Stage downloadStage = createUpdateDownloadStage(owner, update.version());
        ProgressBar progressBar = (ProgressBar) downloadStage.getScene().lookup("#updateProgress");
        Label progressLabel = (Label) downloadStage.getScene().lookup("#updateProgressLabel");
        downloadStage.show();

        Thread downloadThread = new Thread(() -> {
            try {
                Path installer = updateService.download(update, (downloaded, total) -> Platform.runLater(() -> {
                    if (total > 0) {
                        progressBar.setProgress((double) downloaded / total);
                        progressLabel.setText(String.format("已下載 %.1f / %.1f MB",
                                downloaded / 1024.0 / 1024.0, total / 1024.0 / 1024.0));
                    } else {
                        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                        progressLabel.setText(String.format("已下載 %.1f MB", downloaded / 1024.0 / 1024.0));
                    }
                }));
                updateService.launchInstaller(installer);
                Platform.runLater(() -> {
                    downloadStage.close();
                    finishLoginRecord();
                    if (owner != null) {
                        owner.setOnCloseRequest(null);
                    }
                    Platform.exit();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    downloadStage.close();
                    AlertDialog.showError("更新失敗", "無法下載或啟動新版安裝程式：" + e.getMessage());
                });
            }
        }, "app-update-download");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private Stage createUpdateDownloadStage(Stage owner, String version) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("下載更新");
        stage.setResizable(false);
        stage.setOnCloseRequest(event -> event.consume());

        Label title = new Label("正在下載 TempleApp " + version);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setId("updateProgress");
        progressBar.setPrefWidth(360);
        Label progressLabel = new Label("正在連線至 GitHub...");
        progressLabel.setId("updateProgressLabel");

        VBox box = new VBox(12, title, progressBar, progressLabel);
        box.setStyle("-fx-padding: 22; -fx-alignment: center; -fx-background-color: white;");
        stage.setScene(new Scene(box, 420, 150));
        return stage;
    }

    private record LoginResult(AppUser user, Integer loginRecordId) {
    }
}
