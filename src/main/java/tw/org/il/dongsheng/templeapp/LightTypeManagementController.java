package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;

public class LightTypeManagementController {
    @FXML private TableView<DictionaryItem> lightTypeTable;
    @FXML private TableColumn<DictionaryItem, String> codeColumn, nameColumn, sortColumn;
    @FXML private TableColumn<DictionaryItem, Boolean> enabledColumn;
    @FXML private TextField codeField, nameField, sortField;
    @FXML private CheckBox enabledBox;

    private SQLiteDictionaryRepository repository;
    private DictionaryItem selectedType;

    @FXML
    public void initialize() {
        codeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCode()));
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        sortColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSortOrder() == null ? "" : String.valueOf(data.getValue().getSortOrder())));
        enabledColumn.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().isEnabled()));
        enabledColumn.setCellFactory(CheckBoxTableCell.forTableColumn(enabledColumn));

        lightTypeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> setSelectedType(newVal));
    }

    public void setRepository(SQLiteDictionaryRepository repository) {
        this.repository = repository;
        reload();
    }

    @FXML
    private void onNew() {
        selectedType = null;
        codeField.clear();
        nameField.clear();
        sortField.clear();
        enabledBox.setSelected(true);
        lightTypeTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSave() {
        if (Util.isBlank(nameField.getText())) {
            AlertDialog.showWarning("燈種管理", "請輸入燈種名稱");
            return;
        }

        DictionaryItem type = selectedType == null ? new DictionaryItem() : selectedType;
        type.setCode(codeField.getText());
        type.setName(nameField.getText());
        type.setEnabled(enabledBox.isSelected());
        type.setSortOrder(Util.parseInteger(sortField.getText()));

        try {
            if (type.getId() == null) {
                repository.saveItem(SQLiteDictionaryRepository.TYPE_LIGHT, type, AuthSession.getCurrentOperatorName());
            } else {
                repository.updateItem(type, AuthSession.getCurrentOperatorName());
            }
            reload();
            AlertDialog.showInfo("燈種管理", "儲存成功");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void reload() {
        if (repository == null) {
            return;
        }
        try {
            lightTypeTable.setItems(FXCollections.observableArrayList(
                    repository.findAllItems().stream()
                            .filter(item -> "LIGHT".equals(item.getCategoryCode()))
                            .toList()
            ));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void setSelectedType(DictionaryItem type) {
        selectedType = type;
        if (type == null) {
            return;
        }
        codeField.setText(type.getCode());
        nameField.setText(type.getName());
        sortField.setText(type.getSortOrder() == null ? "" : String.valueOf(type.getSortOrder()));
        enabledBox.setSelected(type.isEnabled());
    }
}
