package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.AppUser;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.sql.SQLException;
import java.util.Optional;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    private SQLiteAuthRepository repository;
    private AppUser authenticatedUser;
    private Integer loginRecordId;

    @FXML
    private void initialize() {
        repository = new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance());
        try {
            repository.createTables();
        } catch (SQLException e) {
            AlertDialog.showError("登入", "初始化登入資料表失敗：" + e.getMessage());
        }
    }

    public AppUser getAuthenticatedUser() {
        return authenticatedUser;
    }

    public Integer getLoginRecordId() {
        return loginRecordId;
    }

    @FXML
    private void onLogin() {
        try {
            Optional<AppUser> user = repository.authenticate(usernameField.getText(), passwordField.getText());
            if (user.isEmpty()) {
                AlertDialog.showInfo("登入", "經辦人或密碼錯誤");
                return;
            }
            authenticatedUser = user.get();
            loginRecordId = repository.startLoginRecord(authenticatedUser);
            close();
        } catch (SQLException e) {
            AlertDialog.showError("登入", "登入失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        authenticatedUser = null;
        close();
    }

    @FXML
    private void onChooseUser() {
        try {
            ListView<AppUser> listView = new ListView<>(FXCollections.observableArrayList(
                    repository.findEnabledUsersByRole(SQLiteAuthRepository.ROLE_USER)
            ));
            listView.setPrefSize(180, 260);
            javafx.scene.Scene scene = new javafx.scene.Scene(listView);
            Stage stage = new Stage();
            stage.setTitle("經辦人");
            stage.setScene(scene);
            stage.initOwner(usernameField.getScene().getWindow());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            listView.setOnMouseClicked(event -> {
                AppUser selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null && event.getClickCount() == 2) {
                    usernameField.setText(selected.getUsername());
                    stage.close();
                    passwordField.requestFocus();
                }
            });
            stage.showAndWait();
        } catch (SQLException e) {
            AlertDialog.showError("登入", "讀取經辦人失敗：" + e.getMessage());
        }
    }

    private void close() {
        ((Stage) usernameField.getScene().getWindow()).close();
    }
}
