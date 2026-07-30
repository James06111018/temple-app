package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.model.LightNumberRecord;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightNumberRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;

public class LightDataManagementController {
    @FXML private ComboBox<String> filterTypeBox, statusBox, taiSuiTypeBox, lightTypeBox;
    @FXML private TabPane managementTabs;
    @FXML private Tab taiSuiTab, lightTab, numberTab, spareTab;
    @FXML private HBox managementWorkspace;
    @FXML private Label filterTypeLabel, queryNumberLabel, managementTitleLabel;
    @FXML private Label editTypeLabel, editNumberLabel;
    @FXML private Label recordCountLabel;
    @FXML private TextField queryNumberField, editTypeField, startNumberField, endNumberField;
    @FXML private TableView<LightNumberRecord> recordTable;
    @FXML private TableColumn<LightNumberRecord, String> recordNumberColumn;
    @FXML private TableColumn<LightNumberRecord, String> memberIdColumn, principalNameColumn, statusColumn;
    @FXML private Button annualClearButton;

    private final SQLiteLightNumberRepository lightNumberRepository =
            new SQLiteLightNumberRepository(SQLiteDatabaseManager.getInstance());
    private List<String> configuredLightPrefixes = List.of();

    @FXML
    public void initialize() {
        try {
            lightNumberRepository.createTable();
        } catch (SQLException e) {
            throw new IllegalStateException("建立燈號管理資料表失敗", e);
        }
        initializeRecordTable();
        configuredLightPrefixes = loadConfiguredLightPrefixes();
        statusBox.setItems(FXCollections.observableArrayList(
                "",
                "N - 未使用",
                "A - 已登記",
                "D - 已刪除"
        ));
        taiSuiTypeBox.setItems(FXCollections.observableArrayList("太"));
        lightTypeBox.setItems(FXCollections.observableArrayList(configuredLightPrefixes));
        taiSuiTypeBox.getSelectionModel().selectFirst();
        if (!lightTypeBox.getItems().isEmpty()) {
            lightTypeBox.getSelectionModel().selectFirst();
        }
        filterTypeBox.valueProperty().addListener(
                (observable, oldType, newType) -> editTypeField.setText(newType == null ? "" : newType)
        );

        managementTabs.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldTab, newTab) -> applyTab(newTab));
        managementTabs.getSelectionModel().select(taiSuiTab);
        applyTab(taiSuiTab);
    }

    @FXML
    private void onPlaceholderAction() {
        // 畫面先完成，後續再接實際管理功能。
    }

    @FXML
    private void onSearch() {
        String lightType = selectedLightType();
        if (Util.isBlank(lightType)) {
            AlertDialog.showWarning("燈號查詢", "請先選擇燈別");
            return;
        }

        try {
            List<LightNumberRecord> records = lightNumberRepository.find(
                    currentManagementType(),
                    lightType,
                    queryNumberField.getText(),
                    selectedStatusCode()
            );
            recordTable.setItems(FXCollections.observableArrayList(records));
            recordCountLabel.setText("總筆數：" + records.size());
        } catch (SQLException e) {
            AlertDialog.showError("燈號查詢", "查詢失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onAdd() {
        String lightType = editTypeField.getText() == null ? "" : editTypeField.getText().trim();
        Integer start = parseSerialNumber(startNumberField.getText());
        Integer end = Util.isBlank(endNumberField.getText())
                ? start
                : parseSerialNumber(endNumberField.getText());
        if (Util.isBlank(lightType) || !filterTypeBox.getItems().contains(lightType)) {
            AlertDialog.showWarning("新增燈號", "請先選擇有效的燈別");
            return;
        }
        if (start == null || end == null || start < 1 || end < start || end > 99999) {
            AlertDialog.showWarning("新增燈號", "請輸入 1 至 99999 的正確起訖燈號");
            return;
        }

        try {
            int inserted = lightNumberRepository.addRange(
                    currentManagementType(),
                    lightType,
                    start,
                    end,
                    AuthSession.getCurrentOperatorName()
            );
            startNumberField.clear();
            endNumberField.clear();
            onSearch();
            int requested = end - start + 1;
            String duplicateMessage = inserted == requested
                    ? ""
                    : "，另有 " + (requested - inserted) + " 筆已存在";
            AlertDialog.showInfo("新增燈號", "新增 " + inserted + " 筆成功" + duplicateMessage);
        } catch (SQLException e) {
            AlertDialog.showError("新增燈號", "新增失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        List<LightNumberRecord> selected = List.copyOf(
                recordTable.getSelectionModel().getSelectedItems()
        );
        if (selected.isEmpty()) {
            AlertDialog.showWarning("刪除燈號", "請先選擇要刪除的燈號");
            return;
        }
        if (!AlertDialog.showConfirm("刪除提示", "確定要刪除選取的 " + selected.size() + " 筆燈號嗎？")) {
            return;
        }
        updateSelectedStatuses(
                selected.stream().map(LightNumberRecord::getId).toList(),
                false
        );
    }

    @FXML
    private void onRestoreDeleted() {
        List<Integer> ids = recordTable.getSelectionModel().getSelectedItems().stream()
                .filter(record -> SQLiteLightNumberRepository.STATUS_DELETED.equals(record.getStatus()))
                .map(LightNumberRecord::getId)
                .toList();
        if (ids.isEmpty()) {
            AlertDialog.showWarning("已刪除轉未使用", "請先選擇狀態為已刪除的燈號");
            return;
        }
        updateSelectedStatuses(ids, true);
    }

    @FXML
    private void onDeleteEndingFour() {
        bulkDeleteFour(
                "確定要刪除尾數為 4 的燈號嗎？",
                false
        );
    }

    @FXML
    private void onDeleteContainingFour() {
        bulkDeleteFour(
                "確定要刪除號碼中含有 4 的燈號嗎？",
                true
        );
    }

    private void initializeRecordTable() {
        recordNumberColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getDisplayNumber())
        );
        memberIdColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getDisplayMemberId())
        );
        principalNameColumn.setCellValueFactory(
                data -> new SimpleStringProperty(valueOrEmpty(data.getValue().getPrincipalName()))
        );
        statusColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getDisplayStatus())
        );
        recordTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private List<String> loadConfiguredLightPrefixes() {
        SQLiteDictionaryRepository repository =
                new SQLiteDictionaryRepository(SQLiteDatabaseManager.getInstance());
        try {
            repository.migrateFromLegacy();
            LinkedHashSet<String> prefixes = new LinkedHashSet<>();
            for (DictionaryItem item
                    : repository.findEnabledItemsByType(SQLiteDictionaryRepository.TYPE_LIGHT)) {
                String name = item.getName() == null ? "" : item.getName().trim();
                if (name.isEmpty() || "安太歲".equals(name)) {
                    continue;
                }
                prefixes.add(name.substring(0, name.offsetByCodePoints(0, 1)));
            }
            return List.copyOf(prefixes);
        } catch (SQLException e) {
            throw new IllegalStateException("讀取詞彙設定的燈別失敗", e);
        }
    }

    private void applyTab(Tab selectedTab) {
        boolean spare = selectedTab == spareTab;
        managementWorkspace.setManaged(!spare);
        managementWorkspace.setVisible(!spare);
        recordTable.getItems().clear();
        recordCountLabel.setText("總筆數：0");
        if (spare) {
            return;
        }

        if (selectedTab == taiSuiTab) {
            applyManagementMode(
                    "燈別", "查詢燈號", "燈號", "燈別", "燈號",
                    "燈號管理", "太歲年度清檔", List.of("太")
            );
        } else if (selectedTab == numberTab) {
            applyManagementMode(
                    "分類", "查詢編號", "編號", "分類", "編號",
                    "編號管理", "編號年度清檔", List.of("普")
            );
        } else {
            applyManagementMode(
                    "燈別",
                    "查詢燈號",
                    "燈號",
                    "燈別",
                    "燈號",
                    "燈號管理",
                    "點燈年度清檔",
                    configuredLightPrefixes
            );
        }
    }

    private void applyManagementMode(
            String typeLabel,
            String queryLabel,
            String recordNumberLabel,
            String editTypeText,
            String editNumberText,
            String managementTitle,
            String clearButtonText,
            List<String> types
    ) {
        filterTypeLabel.setText(typeLabel);
        queryNumberLabel.setText(queryLabel);
        recordNumberColumn.setText(recordNumberLabel);
        editTypeLabel.setText(editTypeText);
        editNumberLabel.setText(editNumberText);
        managementTitleLabel.setText(managementTitle);
        annualClearButton.setText(clearButtonText);
        filterTypeBox.setItems(FXCollections.observableArrayList(types));
        if (!types.isEmpty()) {
            filterTypeBox.getSelectionModel().selectFirst();
        }
        editTypeField.setText(filterTypeBox.getValue());
        queryNumberField.clear();
        startNumberField.clear();
        endNumberField.clear();
        statusBox.getSelectionModel().clearSelection();
    }

    private void updateSelectedStatuses(List<Integer> ids, boolean restore) {
        try {
            int updated = restore
                    ? lightNumberRepository.restoreDeleted(ids, AuthSession.getCurrentOperatorName())
                    : lightNumberRepository.softDelete(ids, AuthSession.getCurrentOperatorName());
            onSearch();
            AlertDialog.showInfo(
                    restore ? "已刪除轉未使用" : "刪除燈號",
                    "已更新 " + updated + " 筆"
            );
        } catch (SQLException e) {
            AlertDialog.showError(
                    restore ? "已刪除轉未使用" : "刪除燈號",
                    "更新失敗：" + e.getMessage()
            );
        }
    }

    private void bulkDeleteFour(String confirmation, boolean containsFour) {
        String lightType = selectedLightType();
        if (Util.isBlank(lightType)) {
            AlertDialog.showWarning("刪除提示", "請先選擇燈別");
            return;
        }
        if (!AlertDialog.showConfirm("刪除提示", confirmation)) {
            return;
        }

        try {
            int updated = containsFour
                    ? lightNumberRepository.softDeleteContainingFour(
                            currentManagementType(),
                            lightType,
                            AuthSession.getCurrentOperatorName()
                    )
                    : lightNumberRepository.softDeleteEndingInFour(
                            currentManagementType(),
                            lightType,
                            AuthSession.getCurrentOperatorName()
                    );
            onSearch();
            AlertDialog.showInfo("刪除燈號", "已將 " + updated + " 筆燈號標記為已刪除");
        } catch (SQLException e) {
            AlertDialog.showError("刪除燈號", "批次刪除失敗：" + e.getMessage());
        }
    }

    private String currentManagementType() {
        Tab selectedTab = managementTabs.getSelectionModel().getSelectedItem();
        if (selectedTab == taiSuiTab) {
            return "TAI_SUI";
        }
        if (selectedTab == numberTab) {
            return "NUMBER";
        }
        return "LIGHT";
    }

    private String selectedLightType() {
        return filterTypeBox.getValue() == null ? "" : filterTypeBox.getValue().trim();
    }

    private String selectedStatusCode() {
        String selected = statusBox.getValue();
        return Util.isBlank(selected) ? "" : selected.substring(0, 1);
    }

    private Integer parseSerialNumber(String value) {
        if (Util.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
