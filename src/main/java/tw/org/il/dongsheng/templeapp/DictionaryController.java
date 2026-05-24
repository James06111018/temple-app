package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.util.List;

public class DictionaryController {
    @FXML private ComboBox<TypeOption> typeBox;
    @FXML private TableView<DictionaryItem> itemTable;
    @FXML private TableColumn<DictionaryItem, String> codeColumn, nameColumn, amountColumn, sortColumn;
    @FXML private TableColumn<DictionaryItem, Boolean> enabledColumn;
    @FXML private TextField codeField, nameField, amountField, sortField;
    @FXML private TextArea descriptionField;
    @FXML private CheckBox enabledBox;

    private SQLiteDictionaryRepository repository;
    private DictionaryItem selectedItem;

    @FXML
    public void initialize() {
        repository = new SQLiteDictionaryRepository(SQLiteDatabaseManager.getInstance());
        try {
            repository.migrateFromLegacy();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        typeBox.setItems(FXCollections.observableArrayList(
                new TypeOption(SQLiteDictionaryRepository.TYPE_LIGHT, "點燈"),
                new TypeOption(SQLiteDictionaryRepository.TYPE_DONATION_LIGHT, "信眾點燈款項"),
                new TypeOption(SQLiteDictionaryRepository.TYPE_DONATION_GHOST, "中元普渡款項")
        ));
        typeBox.getSelectionModel().selectFirst();

        codeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCode()));
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        amountColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAmount() == null ? "" : String.valueOf(data.getValue().getAmount())));
        sortColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSortOrder() == null ? "" : String.valueOf(data.getValue().getSortOrder())));
        enabledColumn.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().isEnabled()));
        enabledColumn.setCellFactory(CheckBoxTableCell.forTableColumn(enabledColumn));

        typeBox.valueProperty().addListener((obs, oldVal, newVal) -> reload());
        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> loadToForm(newVal));
        reload();
    }

    @FXML
    private void onNew() {
        selectedItem = null;
        itemTable.getSelectionModel().clearSelection();
        codeField.clear();
        nameField.clear();
        amountField.clear();
        sortField.clear();
        descriptionField.clear();
        enabledBox.setSelected(true);
    }

    @FXML
    private void onSave() {
        TypeOption type = typeBox.getValue();
        if (type == null) {
            return;
        }
        if (Util.isBlank(nameField.getText())) {
            AlertDialog.showWarning("詞彙設定", "請輸入名稱");
            return;
        }

        DictionaryItem item = selectedItem == null ? new DictionaryItem() : selectedItem;
        item.setCode(codeField.getText());
        item.setName(nameField.getText());
        item.setAmount(Util.parseInteger(amountField.getText()));
        item.setSortOrder(Util.parseInteger(sortField.getText()));
        item.setDescription(descriptionField.getText());
        item.setEnabled(enabledBox.isSelected());

        try {
            if (item.getId() == null) {
                repository.saveItem(type.value(), item, System.getProperty("user.name"));
            } else {
                repository.updateItem(item, System.getProperty("user.name"));
            }
            reload();
            AlertDialog.showInfo("詞彙設定", "儲存成功");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void reload() {
        TypeOption type = typeBox.getValue();
        if (type == null || repository == null) {
            return;
        }
        try {
            List<DictionaryItem> items = repository.findAllItems().stream()
                    .filter(item -> type.value().equals(typeByCategoryCode(item.getCategoryCode())))
                    .toList();
            itemTable.setItems(FXCollections.observableArrayList(items));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String typeByCategoryCode(String categoryCode) {
        return switch (categoryCode) {
            case "LIGHT" -> SQLiteDictionaryRepository.TYPE_LIGHT;
            case "DONATION_LIGHT" -> SQLiteDictionaryRepository.TYPE_DONATION_LIGHT;
            case "DONATION_GHOST" -> SQLiteDictionaryRepository.TYPE_DONATION_GHOST;
            default -> "";
        };
    }

    private void loadToForm(DictionaryItem item) {
        selectedItem = item;
        if (item == null) {
            return;
        }
        codeField.setText(item.getCode());
        nameField.setText(item.getName());
        amountField.setText(item.getAmount() == null ? "" : String.valueOf(item.getAmount()));
        sortField.setText(item.getSortOrder() == null ? "" : String.valueOf(item.getSortOrder()));
        descriptionField.setText(item.getDescription());
        enabledBox.setSelected(item.isEnabled());
    }

    private record TypeOption(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
