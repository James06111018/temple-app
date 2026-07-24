package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import tw.org.il.dongsheng.templeapp.model.AppFunction;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;

public class FunctionManagementController {

    @FXML private TextField functionCodeField, functionNameField;
    @FXML private CheckBox enabledBox;
    @FXML private TableView<AppFunction> functionTable;
    @FXML private TableColumn<AppFunction, String> functionCodeColumn, functionNameColumn, enabledColumn;

    private SQLiteAuthRepository repository;

    @FXML
    private void initialize() {
        repository = new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance());
        if (!AuthSession.isAdmin()) {
            AlertDialog.showInfo("功能管理", "只有 admin 可以維護功能");
            functionTable.setDisable(true);
            functionCodeField.setDisable(true);
            functionNameField.setDisable(true);
            enabledBox.setDisable(true);
            return;
        }
        functionCodeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFunctionCode()));
        functionNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFunctionName()));
        enabledColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isEnabled() ? "Y" : "N"));
        functionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, function) -> setFunction(function));
        enabledBox.setSelected(true);
        loadFunctions();
    }

    @FXML
    private void onNew() {
        functionTable.getSelectionModel().clearSelection();
        functionCodeField.clear();
        functionNameField.clear();
        enabledBox.setSelected(true);
    }

    @FXML
    private void onSave() {
        if (Util.isBlank(functionCodeField.getText()) || Util.isBlank(functionNameField.getText())) {
            AlertDialog.showInfo("功能管理", "請輸入功能代碼與名稱");
            return;
        }
        try {
            repository.saveFunction(new AppFunction(
                    functionCodeField.getText().trim().toUpperCase(),
                    functionNameField.getText().trim(),
                    enabledBox.isSelected()
            ));
            AlertDialog.showInfo("功能管理", "儲存功能成功");
            loadFunctions();
        } catch (SQLException e) {
            AlertDialog.showError("功能管理", "儲存功能失敗：" + e.getMessage());
        }
    }

    private void loadFunctions() {
        try {
            repository.createTables();
            functionTable.setItems(FXCollections.observableArrayList(repository.findAllFunctions()));
        } catch (SQLException e) {
            AlertDialog.showError("功能管理", "讀取功能失敗：" + e.getMessage());
        }
    }

    private void setFunction(AppFunction function) {
        if (function == null) {
            return;
        }
        functionCodeField.setText(function.getFunctionCode());
        functionNameField.setText(function.getFunctionName());
        enabledBox.setSelected(function.isEnabled());
    }
}
