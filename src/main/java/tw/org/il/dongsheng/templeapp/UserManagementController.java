package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import tw.org.il.dongsheng.templeapp.model.AppUser;
import tw.org.il.dongsheng.templeapp.model.AppRole;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;

public class UserManagementController {

    @FXML private TextField keywordField, idField, usernameField, displayNameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleBox;
    @FXML private CheckBox enabledBox;
    @FXML private TableView<AppUser> userTable;
    @FXML private TableColumn<AppUser, String> idColumn, usernameColumn, displayNameColumn, roleColumn, enabledColumn;

    private SQLiteAuthRepository repository;

    @FXML
    private void initialize() {
        repository = new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance());
        try {
            repository.createTables();
        } catch (SQLException e) {
            AlertDialog.showError("使用者管理", "初始化使用者資料表失敗：" + e.getMessage());
        }

        try {
            roleBox.setItems(FXCollections.observableArrayList(
                    repository.findAllRoles().stream().map(AppRole::getRoleCode).toList()
            ));
        } catch (SQLException e) {
            AlertDialog.showError("使用者管理", "讀取角色失敗：" + e.getMessage());
        }
        roleBox.getSelectionModel().select(SQLiteAuthRepository.ROLE_USER);
        enabledBox.setSelected(true);
        idColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getId())));
        usernameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUsername()));
        displayNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDisplayName()));
        roleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoleCode()));
        enabledColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isEnabled() ? "Y" : "N"));
        userTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, user) -> setUser(user));
        onSearch();
    }

    @FXML
    private void onNew() {
        idField.clear();
        usernameField.clear();
        displayNameField.clear();
        passwordField.clear();
        roleBox.getSelectionModel().select(SQLiteAuthRepository.ROLE_USER);
        enabledBox.setSelected(true);
        userTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSearch() {
        try {
            userTable.setItems(FXCollections.observableArrayList(repository.searchUsers(keywordField.getText())));
        } catch (SQLException e) {
            AlertDialog.showError("使用者管理", "查詢失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onSave() {
        if (Util.isBlank(usernameField.getText()) || Util.isBlank(displayNameField.getText())) {
            AlertDialog.showInfo("使用者管理", "請輸入帳號與名稱");
            return;
        }
        Integer id = Util.parseInteger(idField.getText());
        if (id == null && Util.isBlank(passwordField.getText())) {
            AlertDialog.showInfo("使用者管理", "新增使用者請輸入密碼");
            return;
        }

        AppUser user = new AppUser(id, usernameField.getText().trim(), displayNameField.getText().trim(), roleBox.getValue(), enabledBox.isSelected());
        String changedBy = AuthSession.getCurrentUser() == null ? System.getProperty("user.name") : AuthSession.getCurrentUser().getUsername();
        try {
            if (id == null) {
                repository.saveUser(user, passwordField.getText(), changedBy);
                AlertDialog.showInfo("使用者管理", "新增使用者成功");
            } else {
                repository.updateUser(user, passwordField.getText(), changedBy);
                AlertDialog.showInfo("使用者管理", "修改使用者成功");
            }
            onSearch();
            setUser(user);
        } catch (SQLException e) {
            AlertDialog.showError("使用者管理", "儲存失敗：" + e.getMessage());
        }
    }

    private void setUser(AppUser user) {
        if (user == null) {
            return;
        }
        idField.setText(String.valueOf(user.getId()));
        usernameField.setText(user.getUsername());
        displayNameField.setText(user.getDisplayName());
        passwordField.clear();
        roleBox.setValue(user.getRoleCode());
        enabledBox.setSelected(user.isEnabled());
    }
}
