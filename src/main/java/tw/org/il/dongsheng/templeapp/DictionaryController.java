package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class DictionaryController {
    @FXML private ComboBox<TypeOption> paymentTypeBox;

    @FXML private TableView<DictionaryItem> categoryTable, summaryTable, deleteReasonTable, supplementReasonTable;
    @FXML private TableColumn<DictionaryItem, String> categoryTypeColumn, categoryCodeColumn,
            categoryNameColumn, categoryDirectionColumn, categoryDefaultAmountColumn,
            categoryEnabledColumn, categorySortColumn;
    @FXML private TableColumn<DictionaryItem, String> summaryCodeColumn, summaryNameColumn,
            summaryDescriptionColumn;
    @FXML private TableColumn<DictionaryItem, String> deleteReasonCodeColumn, deleteReasonNameColumn;
    @FXML private TableColumn<DictionaryItem, String> supplementReasonCodeColumn, supplementReasonNameColumn;

    private SQLiteDictionaryRepository repository;

    @FXML
    public void initialize() {
        repository = new SQLiteDictionaryRepository(SQLiteDatabaseManager.getInstance());
        try {
            repository.migrateFromLegacy();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        paymentTypeBox.setItems(FXCollections.observableArrayList(
                new TypeOption("", ""),
                new TypeOption(SQLiteDictionaryRepository.TYPE_LIGHT, "燈別"),
                new TypeOption(SQLiteDictionaryRepository.TYPE_DONATION_LIGHT, "信眾點燈"),
                new TypeOption(SQLiteDictionaryRepository.TYPE_DONATION_GHOST, "中元普渡")
        ));
        paymentTypeBox.getSelectionModel().selectFirst();

        setupColumns();
        paymentTypeBox.valueProperty().addListener((obs, oldVal, newVal) -> reloadCategories());

        reloadAll();
    }

    private void setupColumns() {
        categoryTypeColumn.setCellValueFactory(data -> text(categoryTypeLabel(data.getValue())));
        categoryCodeColumn.setCellValueFactory(data -> text(data.getValue().getCode()));
        categoryNameColumn.setCellValueFactory(data -> text(data.getValue().getName()));
        categoryDirectionColumn.setCellValueFactory(data -> text(data.getValue().getDirection()));
        categoryDefaultAmountColumn.setCellValueFactory(data -> text(data.getValue().getDefaultAmount() == null ? "" : String.valueOf(data.getValue().getDefaultAmount())));
        categoryEnabledColumn.setCellValueFactory(data -> text(data.getValue().isEnabled() ? "V" : ""));
        categorySortColumn.setCellValueFactory(data -> text(data.getValue().getSortOrder() == null ? "" : String.valueOf(data.getValue().getSortOrder())));
        summaryCodeColumn.setCellValueFactory(data -> text(data.getValue().getCode()));
        summaryNameColumn.setCellValueFactory(data -> text(data.getValue().getName()));
        summaryDescriptionColumn.setCellValueFactory(data -> text(data.getValue().getDescription()));
        deleteReasonCodeColumn.setCellValueFactory(data -> text(data.getValue().getCode()));
        deleteReasonNameColumn.setCellValueFactory(data -> text(data.getValue().getName()));
        supplementReasonCodeColumn.setCellValueFactory(data -> text(data.getValue().getCode()));
        supplementReasonNameColumn.setCellValueFactory(data -> text(data.getValue().getName()));
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    @FXML
    private void onNewCategory() {
        TypeOption type = paymentTypeBox.getValue();
        if (type == null || type.value().isBlank()) {
            AlertDialog.showInfo("詞彙設定", "新增前請先選擇類型");
            return;
        }
        editItem(dialogTitle(type.value()), type.value(), null, null, usesPaymentFields(type.value()));
    }

    @FXML
    private void onEditCategory() {
        DictionaryItem selected = categoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertDialog.showInfo("詞彙設定", "請先選擇款項類別");
            return;
        }
        String type = typeByCategoryCode(selected.getCategoryCode());
        editItem(dialogTitle(type), type, null, selected, usesPaymentFields(type));
    }

    @FXML
    private void onNewSummary() {
        editItem("摘要", SQLiteDictionaryRepository.TYPE_DONATION_SUMMARY, null, null, false);
    }

    @FXML
    private void onEditSummary() {
        DictionaryItem selected = summaryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertDialog.showInfo("詞彙設定", "請先選擇摘要");
            return;
        }
        editItem("摘要", SQLiteDictionaryRepository.TYPE_DONATION_SUMMARY, null, selected, false);
    }

    @FXML
    private void onNewDeleteReason() {
        editItem("刪除原因", SQLiteDictionaryRepository.TYPE_DELETE_REASON, null, null, false);
    }

    @FXML
    private void onEditDeleteReason() {
        DictionaryItem selected = deleteReasonTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertDialog.showInfo("詞彙設定", "請先選擇刪除原因");
            return;
        }
        editItem("刪除原因", SQLiteDictionaryRepository.TYPE_DELETE_REASON, null, selected, false);
    }

    @FXML
    private void onNewSupplementReason() {
        editItem("補據原因", SQLiteDictionaryRepository.TYPE_SUPPLEMENT_REASON, null, null, false);
    }

    @FXML
    private void onEditSupplementReason() {
        DictionaryItem selected = supplementReasonTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertDialog.showInfo("詞彙設定", "請先選擇補據原因");
            return;
        }
        editItem("補據原因", SQLiteDictionaryRepository.TYPE_SUPPLEMENT_REASON, null, selected, false);
    }

    private void editItem(String title, String type, Integer parentItemId, DictionaryItem item, boolean usesPaymentFields) {
        Optional<DictionaryItem> result = showItemDialog(title, item, usesPaymentFields);
        if (result.isEmpty()) {
            return;
        }

        DictionaryItem edited = result.get();
        edited.setParentItemId(parentItemId);

        try {
            if (edited.getId() == null) {
                repository.saveItem(type, edited, AuthSession.getCurrentOperatorName());
            } else {
                repository.updateItem(edited, AuthSession.getCurrentOperatorName());
            }
            reloadAll();
            selectEditedItem(type, edited.getId(), parentItemId);
            AlertDialog.showInfo("詞彙設定", "儲存成功");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<DictionaryItem> showItemDialog(String title, DictionaryItem item, boolean usesPaymentFields) {
        Dialog<DictionaryItem> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Window window = categoryTable.getScene() == null ? null : categoryTable.getScene().getWindow();
        if (window != null) {
            dialog.initOwner(window);
        }

        TextField codeField = new TextField(item == null ? "" : safe(item.getCode()));
        TextField nameField = new TextField(item == null ? "" : safe(item.getName()));
        ComboBox<String> directionBox = new ComboBox<>(FXCollections.observableArrayList("+", "-"));
        directionBox.setValue(item == null || Util.isBlank(item.getDirection()) ? "+" : item.getDirection());
        TextField amountField = new TextField(item == null || item.getDefaultAmount() == null ? "" : String.valueOf(item.getDefaultAmount()));
        TextField sortField = new TextField(item == null || item.getSortOrder() == null ? "" : String.valueOf(item.getSortOrder()));
        CheckBox enabledBox = new CheckBox();
        enabledBox.setSelected(item == null || item.isEnabled());
        TextArea descriptionField = new TextArea(item == null ? "" : safe(item.getDescription()));
        descriptionField.setPrefRowCount(3);

        GridPane form = new GridPane();
        form.setPadding(new Insets(12));
        form.setHgap(8);
        form.setVgap(8);
        boolean light = title.contains("燈種");
        form.addRow(0, new Label(light ? "代碼" : usesPaymentFields ? "分類" : "編號"), codeField);
        form.addRow(1, new Label(light ? "燈種名稱" : usesPaymentFields ? "款項名稱" : "內容"), nameField);
        if (usesPaymentFields) {
            form.addRow(2, new Label("收(+)付(-)"), directionBox);
            form.addRow(3, new Label("預設金額"), amountField);
        }
        form.addRow(usesPaymentFields ? 4 : 2, new Label("排序"), sortField);
        form.addRow(usesPaymentFields ? 5 : 3, new Label(light ? "顯示" : "啟用"), enabledBox);
        form.addRow(usesPaymentFields ? 6 : 4, new Label("說明"), descriptionField);
        dialog.getDialogPane().setContent(form);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (Util.isBlank(nameField.getText())) {
                AlertDialog.showWarning("詞彙設定", "請輸入內容");
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            DictionaryItem edited = item == null ? new DictionaryItem() : item;
            edited.setCode(codeField.getText());
            edited.setName(nameField.getText());
            Integer defaultAmount = usesPaymentFields ? Util.parseInteger(amountField.getText()) : 0;
            edited.setDirection(usesPaymentFields ? directionBox.getValue() : "+");
            edited.setAmount(defaultAmount);
            edited.setDefaultAmount(defaultAmount);
            edited.setSortOrder(Util.parseInteger(sortField.getText()));
            edited.setEnabled(enabledBox.isSelected());
            edited.setDescription(descriptionField.getText());
            return edited;
        });

        return dialog.showAndWait();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void reloadAll() {
        reloadCategories();
        reloadSummaries();
        reloadReasons();
    }

    private void reloadCategories() {
        TypeOption type = paymentTypeBox.getValue();
        updateCategoryColumns(type == null ? "" : type.value());
        if (type == null) {
            categoryTable.getItems().clear();
            return;
        }
        try {
            categoryTable.setItems(FXCollections.observableArrayList(findCategoryItems(type.value())));
            if (!categoryTable.getItems().isEmpty()) {
                categoryTable.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateCategoryColumns(String type) {
        boolean lightOnly = SQLiteDictionaryRepository.TYPE_LIGHT.equals(type);
        boolean paymentOnly = usesPaymentFields(type);
        categoryCodeColumn.setText(lightOnly ? "代碼" : "分類");
        categoryNameColumn.setText(lightOnly ? "燈種名稱" : "款項名稱");
        categoryDirectionColumn.setVisible(paymentOnly || type.isBlank());
        categoryDefaultAmountColumn.setVisible(paymentOnly || type.isBlank());
        categoryEnabledColumn.setVisible(lightOnly);
        categorySortColumn.setVisible(lightOnly);
    }

    private List<DictionaryItem> findCategoryItems(String type) throws SQLException {
        if (!type.isBlank()) {
            return repository.findItemsByType(type);
        }
        return repository.findAllItems().stream()
                .filter(item -> isCategoryType(typeByCategoryCode(item.getCategoryCode())))
                .toList();
    }

    private void reloadSummaries() {
        try {
            summaryTable.setItems(FXCollections.observableArrayList(
                    repository.findItemsByType(SQLiteDictionaryRepository.TYPE_DONATION_SUMMARY)
            ));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String categoryTypeLabel(DictionaryItem category) {
        if (category == null) {
            return "";
        }
        return switch (typeByCategoryCode(category.getCategoryCode())) {
            case SQLiteDictionaryRepository.TYPE_LIGHT -> "燈別";
            case SQLiteDictionaryRepository.TYPE_DONATION_LIGHT -> "信眾點燈";
            case SQLiteDictionaryRepository.TYPE_DONATION_GHOST -> "中元普渡";
            default -> "";
        };
    }

    private void reloadReasons() {
        try {
            deleteReasonTable.setItems(FXCollections.observableArrayList(repository.findItemsByType(SQLiteDictionaryRepository.TYPE_DELETE_REASON)));
            supplementReasonTable.setItems(FXCollections.observableArrayList(repository.findItemsByType(SQLiteDictionaryRepository.TYPE_SUPPLEMENT_REASON)));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void selectEditedItem(String type, Integer id, Integer parentItemId) {
        if (id == null) {
            return;
        }
        TypeOption selectedType = paymentTypeBox.getValue();
        if ((selectedType != null && selectedType.value().isBlank() && isCategoryType(type))
                || (selectedType != null && type.equals(selectedType.value()))) {
            selectById(categoryTable, id);
        } else if (type.equals(SQLiteDictionaryRepository.TYPE_DONATION_SUMMARY)) {
            selectById(summaryTable, id);
        } else if (type.equals(SQLiteDictionaryRepository.TYPE_DELETE_REASON)) {
            selectById(deleteReasonTable, id);
        } else if (type.equals(SQLiteDictionaryRepository.TYPE_SUPPLEMENT_REASON)) {
            selectById(supplementReasonTable, id);
        }
    }

    private void selectById(TableView<DictionaryItem> table, Integer id) {
        table.getItems().stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .ifPresent(item -> table.getSelectionModel().select(item));
    }

    private boolean isCategoryType(String type) {
        return SQLiteDictionaryRepository.TYPE_LIGHT.equals(type)
                || SQLiteDictionaryRepository.TYPE_DONATION_LIGHT.equals(type)
                || SQLiteDictionaryRepository.TYPE_DONATION_GHOST.equals(type);
    }

    private boolean includeAmount(String type) {
        return SQLiteDictionaryRepository.TYPE_DONATION_LIGHT.equals(type)
                || SQLiteDictionaryRepository.TYPE_DONATION_GHOST.equals(type);
    }

    private boolean usesPaymentFields(String type) {
        return SQLiteDictionaryRepository.TYPE_LIGHT.equals(type) || includeAmount(type);
    }

    private String dialogTitle(String type) {
        return switch (type) {
            case SQLiteDictionaryRepository.TYPE_LIGHT -> "燈種管理";
            case SQLiteDictionaryRepository.TYPE_DONATION_LIGHT -> "信眾點燈款項類別";
            case SQLiteDictionaryRepository.TYPE_DONATION_GHOST -> "中元普渡款項類別";
            default -> "款項類別";
        };
    }

    private String typeByCategoryCode(String categoryCode) {
        return switch (categoryCode) {
            case "LIGHT" -> SQLiteDictionaryRepository.TYPE_LIGHT;
            case "DONATION_LIGHT" -> SQLiteDictionaryRepository.TYPE_DONATION_LIGHT;
            case "DONATION_GHOST" -> SQLiteDictionaryRepository.TYPE_DONATION_GHOST;
            default -> "";
        };
    }

    private record TypeOption(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
