package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import tw.org.il.dongsheng.templeapp.model.AppFunction;
import tw.org.il.dongsheng.templeapp.model.AppRole;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleManagementController {

    @FXML private TextField roleCodeField, roleNameField;
    @FXML private TableView<AppRole> roleTable;
    @FXML private TableColumn<AppRole, String> roleCodeColumn, roleNameColumn;
    @FXML private TableView<FunctionSelection> functionTable;
    @FXML private TableColumn<FunctionSelection, Boolean> selectedColumn;
    @FXML private TableColumn<FunctionSelection, String> functionCodeColumn, functionNameColumn;

    private SQLiteAuthRepository repository;

    @FXML
    private void initialize() {
        repository = new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance());
        if (!AuthSession.isAdmin()) {
            AlertDialog.showInfo("角色管理", "只有 admin 可以維護角色");
            roleTable.setDisable(true);
            functionTable.setDisable(true);
            roleCodeField.setDisable(true);
            roleNameField.setDisable(true);
            return;
        }
        roleCodeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoleCode()));
        roleNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoleName()));
        selectedColumn.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        selectedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectedColumn));
        functionCodeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().function().getFunctionCode()));
        functionNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().function().getFunctionName()));
        functionTable.setEditable(true);
        roleTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, role) -> setRole(role));
        loadRoles();
    }

    @FXML
    private void onNew() {
        roleTable.getSelectionModel().clearSelection();
        roleCodeField.clear();
        roleNameField.clear();
        loadFunctions(Set.of());
    }

    @FXML
    private void onSave() {
        if (Util.isBlank(roleCodeField.getText()) || Util.isBlank(roleNameField.getText())) {
            AlertDialog.showInfo("角色管理", "請輸入角色代碼與名稱");
            return;
        }
        String code = roleCodeField.getText().trim().toUpperCase();
        Set<String> selectedFunctions = functionTable.getItems().stream()
                .filter(FunctionSelection::isSelected)
                .map(item -> item.function().getFunctionCode())
                .collect(Collectors.toSet());
        try {
            repository.saveRole(new AppRole(code, roleNameField.getText().trim()));
            repository.grantRoleFunctions(code, selectedFunctions);
            AlertDialog.showInfo("角色管理", "儲存角色成功");
            loadRoles();
        } catch (SQLException e) {
            AlertDialog.showError("角色管理", "儲存角色失敗：" + e.getMessage());
        }
    }

    private void loadRoles() {
        try {
            repository.createTables();
            roleTable.setItems(FXCollections.observableArrayList(repository.findAllRoles()));
            loadFunctions(Set.of());
        } catch (SQLException e) {
            AlertDialog.showError("角色管理", "讀取角色失敗：" + e.getMessage());
        }
    }

    private void setRole(AppRole role) {
        if (role == null) {
            return;
        }
        roleCodeField.setText(role.getRoleCode());
        roleNameField.setText(role.getRoleName());
        try {
            loadFunctions(repository.findFunctionCodesByRole(role.getRoleCode()));
        } catch (SQLException e) {
            AlertDialog.showError("角色管理", "讀取角色功能失敗：" + e.getMessage());
        }
    }

    private void loadFunctions(Set<String> selectedCodes) {
        try {
            functionTable.setItems(FXCollections.observableArrayList(
                    repository.findAllFunctions().stream()
                            .map(function -> new FunctionSelection(function, selectedCodes.contains(function.getFunctionCode())))
                            .toList()
            ));
        } catch (SQLException e) {
            AlertDialog.showError("角色管理", "讀取功能失敗：" + e.getMessage());
        }
    }

    public record FunctionSelection(AppFunction function, BooleanProperty selectedProperty) {
        public FunctionSelection(AppFunction function, boolean selected) {
            this(function, new SimpleBooleanProperty(selected));
        }

        public boolean isSelected() {
            return selectedProperty.get();
        }
    }
}
