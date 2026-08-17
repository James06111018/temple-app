package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.MeritCategory;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteMeritCategoryRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;

public class MeritCategoryManagementController {
    @FXML private TableView<MeritCategory> categoryTable;
    @FXML private TableColumn<MeritCategory, String> codeColumn;
    @FXML private TableColumn<MeritCategory, String> nameColumn;
    @FXML private TableColumn<MeritCategory, String> statusColumn;
    @FXML private CheckBox includeDeletedBox;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;
    @FXML private Button restoreButton;

    private SQLiteMeritCategoryRepository repository;
    private MeritCategory selectedCategory;

    @FXML
    public void initialize() {
        codeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCode()));
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isDeleted() ? "已刪除" : "使用中"));
        categoryTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> selectCategory(newValue));
    }

    public void setRepository(SQLiteMeritCategoryRepository repository) {
        this.repository = repository;
        reload();
    }

    @FXML
    private void onIncludeDeletedChanged() {
        reload();
    }

    @FXML
    private void onNew() {
        selectedCategory = null;
        codeField.clear();
        nameField.clear();
        categoryTable.getSelectionModel().clearSelection();
        updateButtons();
        codeField.requestFocus();
    }

    @FXML
    private void onSave() {
        String code = codeField.getText() == null ? "" : codeField.getText().trim();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (Util.isBlank(code) || Util.isBlank(name)) {
            AlertDialog.showWarning("分類管理", "請輸入代號及分類名稱");
            return;
        }

        MeritCategory category = selectedCategory == null ? new MeritCategory() : selectedCategory;
        category.setCode(code);
        category.setName(name);
        try {
            repository.save(category, AuthSession.getCurrentOperatorName());
            reload();
            AlertDialog.showInfo("分類管理", "儲存成功");
        } catch (SQLException e) {
            if (isUniqueConstraintError(e)) {
                AlertDialog.showWarning("分類管理", "此代號已存在，請使用其他代號");
                return;
            }
            AlertDialog.showError("分類管理", "儲存失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        if (selectedCategory == null || selectedCategory.isDeleted()) {
            return;
        }
        if (!AlertDialog.showConfirm("分類管理", "確定刪除分類「" + selectedCategory.getName() + "」？")) {
            return;
        }
        try {
            repository.softDelete(selectedCategory.getId(), AuthSession.getCurrentOperatorName());
            reload();
        } catch (SQLException e) {
            AlertDialog.showError("分類管理", "刪除失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onRestore() {
        if (selectedCategory == null || !selectedCategory.isDeleted()) {
            return;
        }
        try {
            repository.restore(selectedCategory.getId(), AuthSession.getCurrentOperatorName());
            reload();
        } catch (SQLException e) {
            AlertDialog.showError("分類管理", "還原失敗：" + e.getMessage());
        }
    }

    private void reload() {
        if (repository == null) {
            return;
        }
        try {
            categoryTable.setItems(FXCollections.observableArrayList(
                    repository.findAll(includeDeletedBox.isSelected())));
            onNew();
        } catch (SQLException e) {
            AlertDialog.showError("分類管理", "讀取分類失敗：" + e.getMessage());
        }
    }

    private void selectCategory(MeritCategory category) {
        selectedCategory = category;
        if (category != null) {
            codeField.setText(category.getCode());
            nameField.setText(category.getName());
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean hasSelection = selectedCategory != null;
        boolean deleted = hasSelection && selectedCategory.isDeleted();
        saveButton.setDisable(deleted);
        deleteButton.setDisable(!hasSelection || deleted);
        restoreButton.setDisable(!deleted);
        codeField.setDisable(deleted);
        nameField.setDisable(deleted);
    }

    private boolean isUniqueConstraintError(SQLException exception) {
        return exception.getMessage() != null
                && exception.getMessage().toLowerCase().contains("unique constraint");
    }
}
