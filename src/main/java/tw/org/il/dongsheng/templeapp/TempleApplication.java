package tw.org.il.dongsheng.templeapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.AppUser;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.ResourceBundle;

public class TempleApplication extends Application {
    private boolean loginRecordFinished = false;

    @Override
    public void start(Stage stage) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("tw.org.il.dongsheng.templeapp.strings");
        initializeAuthTables();

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
        stage.setOnCloseRequest(event -> finishLoginRecord());
        stage.show();
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

    private record LoginResult(AppUser user, Integer loginRecordId) {
    }
}
