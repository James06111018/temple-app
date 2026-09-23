package tw.org.il.dongsheng.templeapp;

import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TempleApplication extends Application {
    private boolean loginRecordFinished = false;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicReference<Stage> syncProgressStageRef = new AtomicReference<>();
    private final AtomicReference<Timeline> syncElapsedTimelineRef = new AtomicReference<>();
    private SyncService syncService;

    @Override
    public void start(Stage stage) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("tw.org.il.dongsheng.templeapp.strings");
        initializeAuthTables();
        ensureSyncSettings(stage);

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

        syncService = createSyncService();

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

    private record LoginResult(AppUser user, Integer loginRecordId) {
    }
}
