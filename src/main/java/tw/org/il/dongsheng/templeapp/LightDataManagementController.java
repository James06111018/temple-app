package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.model.LightNumberRecord;
import tw.org.il.dongsheng.templeapp.model.LightRegistrationReportRow;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightNumberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteSystemSettingsRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.LightManagementReportBuilder;
import tw.org.il.dongsheng.templeapp.util.LightTypeUtil;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class LightDataManagementController {
    @FXML private ComboBox<String> filterTypeBox, statusBox, taiSuiTypeBox, lightTypeBox;
    @FXML private TabPane managementTabs;
    @FXML private Tab taiSuiTab, lightTab, numberTab, spareTab;
    @FXML private HBox managementWorkspace;
    @FXML private Label filterTypeLabel, queryNumberLabel, managementTitleLabel;
    @FXML private Label editTypeLabel, editNumberLabel;
    @FXML private Label recordCountLabel;
    @FXML private TextField queryNumberField, editTypeField, startNumberField, endNumberField;
    @FXML private TextField taiSuiStartNumberField, taiSuiEndNumberField;
    @FXML private TextField taiSuiStartDateField, taiSuiEndDateField;
    @FXML private TextField lightStartNumberField, lightEndNumberField;
    @FXML private TextField lightStartDateField, lightEndDateField;
    @FXML private RadioButton taiSuiTypeRadio, taiSuiDateRadio, lightTypeRadio, lightDateRadio;
    @FXML private TableView<LightNumberRecord> recordTable;
    @FXML private TableColumn<LightNumberRecord, String> recordNumberColumn;
    @FXML private TableColumn<LightNumberRecord, String> memberIdColumn, principalNameColumn, statusColumn;
    @FXML private Button annualClearButton;

    private final SQLiteLightNumberRepository lightNumberRepository =
            new SQLiteLightNumberRepository(SQLiteDatabaseManager.getInstance());
    private final SQLiteSystemSettingsRepository settingsRepository =
            new SQLiteSystemSettingsRepository(SQLiteDatabaseManager.getInstance());
    private static final DateTimeFormatter ROC_DATE = DateTimeFormatter.ofPattern("yyy.MM.dd");
    private List<String> configuredLightPrefixes = List.of();

    @FXML
    public void initialize() {
        try {
            lightNumberRepository.createTable();
        } catch (SQLException e) {
            throw new IllegalStateException("建立燈號管理資料表失敗", e);
        }
        initializeRecordTable();
        initializeReportFilters();
        configuredLightPrefixes = loadConfiguredLightPrefixes();
        statusBox.setItems(FXCollections.observableArrayList(
                "",
                "N - 未使用",
                "A - 已登記",
                "D - 已刪除"
        ));
        statusBox.valueProperty().addListener((observable, oldStatus, newStatus) -> {
            if (newStatus != null
                    && managementTabs.getSelectionModel().getSelectedItem() != spareTab
                    && managementWorkspace.isVisible()) {
                onSearch();
            }
        });
        taiSuiTypeBox.setItems(FXCollections.observableArrayList("", "太"));
        lightTypeBox.setItems(FXCollections.observableArrayList(""));
        lightTypeBox.getItems().addAll(configuredLightPrefixes);
        taiSuiTypeBox.getSelectionModel().select("太");
        lightTypeBox.getSelectionModel().selectFirst();
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

    @FXML
    private void onPrintTaiSuiLabels() {
        openManagementReport(ReportKind.TAI_SUI_LABEL);
    }

    @FXML
    private void onPrintTaiSuiPrayer() {
        openManagementReport(ReportKind.TAI_SUI_PRAYER);
    }

    @FXML
    private void onPrintLightLabels() {
        openManagementReport(ReportKind.LIGHT_LABEL);
    }

    @FXML
    private void onPrintLightPrayer() {
        openManagementReport(ReportKind.LIGHT_PRAYER);
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

    private void initializeReportFilters() {
        ToggleGroup taiSuiGroup = new ToggleGroup();
        taiSuiTypeRadio.setToggleGroup(taiSuiGroup);
        taiSuiDateRadio.setToggleGroup(taiSuiGroup);
        taiSuiTypeRadio.setSelected(true);
        bindReportFilterState(
                taiSuiTypeRadio,
                taiSuiTypeBox,
                taiSuiStartNumberField,
                taiSuiEndNumberField,
                taiSuiStartDateField,
                taiSuiEndDateField
        );

        ToggleGroup lightGroup = new ToggleGroup();
        lightTypeRadio.setToggleGroup(lightGroup);
        lightDateRadio.setToggleGroup(lightGroup);
        lightTypeRadio.setSelected(true);
        bindReportFilterState(
                lightTypeRadio,
                lightTypeBox,
                lightStartNumberField,
                lightEndNumberField,
                lightStartDateField,
                lightEndDateField
        );
    }

    private void bindReportFilterState(
            RadioButton typeRadio,
            ComboBox<String> typeBox,
            TextField startNumberField,
            TextField endNumberField,
            TextField startDateField,
            TextField endDateField
    ) {
        typeBox.disableProperty().bind(typeRadio.selectedProperty().not());
        startNumberField.disableProperty().bind(typeRadio.selectedProperty().not());
        endNumberField.disableProperty().bind(typeRadio.selectedProperty().not());
        startDateField.disableProperty().bind(typeRadio.selectedProperty());
        endDateField.disableProperty().bind(typeRadio.selectedProperty());
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
                prefixes.add(LightTypeUtil.abbreviation(name));
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

    private void openManagementReport(ReportKind kind) {
        boolean taiSui = kind == ReportKind.TAI_SUI_LABEL || kind == ReportKind.TAI_SUI_PRAYER;
        try {
            List<LightRegistrationReportRow> rows = lightNumberRepository
                    .findRegisteredForReport(taiSui ? "TAI_SUI" : "LIGHT");
            ReportFilter filter = buildReportFilter(taiSui);
            if (filter == null) {
                return;
            }
            List<LightRegistrationReportRow> filtered = rows.stream()
                    .filter(filter::matches)
                    .toList();
            if (filtered.isEmpty()) {
                AlertDialog.showInfo("信眾點燈資料管理", "查無符合條件的點燈資料");
                return;
            }

            List<? extends javafx.scene.layout.Region> pages = switch (kind) {
                case TAI_SUI_LABEL -> LightManagementReportBuilder.buildTaiSuiLabelPages(filtered);
                case TAI_SUI_PRAYER -> LightManagementReportBuilder.buildTaiSuiPrayerPages(
                        filtered,
                        settingsRepository.findByGroup("PRAYER")
                );
                case LIGHT_LABEL -> LightManagementReportBuilder.buildLightLabelPages(filtered);
                case LIGHT_PRAYER -> LightManagementReportBuilder.buildLightPrayerPages(filtered);
            };
            PrintPreview.show(managementTabs.getScene().getWindow(), kind.title, pages);
        } catch (SQLException e) {
            AlertDialog.showError("信眾點燈資料管理", "讀取列印資料失敗：" + e.getMessage());
        }
    }

    private ReportFilter buildReportFilter(boolean taiSui) {
        RadioButton typeRadio = taiSui ? taiSuiTypeRadio : lightTypeRadio;
        ComboBox<String> typeBox = taiSui ? taiSuiTypeBox : lightTypeBox;
        TextField startNumberField = taiSui ? taiSuiStartNumberField : lightStartNumberField;
        TextField endNumberField = taiSui ? taiSuiEndNumberField : lightEndNumberField;
        TextField startDateField = taiSui ? taiSuiStartDateField : lightStartDateField;
        TextField endDateField = taiSui ? taiSuiEndDateField : lightEndDateField;

        if (typeRadio.isSelected()) {
            Integer startNumber = parseOptionalSerial(startNumberField.getText());
            Integer endNumber = parseOptionalSerial(endNumberField.getText());
            if ((!Util.isBlank(startNumberField.getText()) && startNumber == null)
                    || (!Util.isBlank(endNumberField.getText()) && endNumber == null)
                    || (startNumber != null && endNumber != null && startNumber > endNumber)) {
                AlertDialog.showWarning("列印條件", "請輸入正確的燈號起訖範圍");
                return null;
            }
            return ReportFilter.byType(typeBox.getValue(), startNumber, endNumber);
        }

        LocalDate startDate = parseOptionalDate(startDateField.getText());
        LocalDate endDate = parseOptionalDate(endDateField.getText());
        if ((!Util.isBlank(startDateField.getText()) && startDate == null)
                || (!Util.isBlank(endDateField.getText()) && endDate == null)
                || (startDate != null && endDate != null && startDate.isAfter(endDate))) {
            AlertDialog.showWarning("列印條件", "日期請輸入民國 115.05.14 或西元 2026-05-14 格式");
            return null;
        }
        return ReportFilter.byDate(startDate, endDate);
    }

    private Integer parseOptionalSerial(String value) {
        if (Util.isBlank(value)) {
            return null;
        }
        String digits = value.trim().replaceAll("\\D", "");
        try {
            return digits.isEmpty() ? null : Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseOptionalDate(String value) {
        if (Util.isBlank(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.matches("\\d{3}\\.\\d{2}\\.\\d{2}")) {
                return LocalDate.parse(text, ROC_DATE).plusYears(1911);
            }
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private enum ReportKind {
        TAI_SUI_LABEL("安太歲名條"),
        TAI_SUI_PRAYER("安太歲疏文"),
        LIGHT_LABEL("點燈名條"),
        LIGHT_PRAYER("點燈疏文");

        private final String title;

        ReportKind(String title) {
            this.title = title;
        }
    }

    private record ReportFilter(
            String lightType,
            Integer startNumber,
            Integer endNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        private static ReportFilter byType(String type, Integer start, Integer end) {
            return new ReportFilter(type == null ? "" : type.trim(), start, end, null, null);
        }

        private static ReportFilter byDate(LocalDate start, LocalDate end) {
            return new ReportFilter("", null, null, start, end);
        }

        private boolean matches(LightRegistrationReportRow row) {
            if (!lightType.isEmpty() && !value(row.lightNumber()).startsWith(lightType)) {
                return false;
            }
            int serial = serial(row.lightNumber());
            if (startNumber != null && serial < startNumber) {
                return false;
            }
            if (endNumber != null && serial > endNumber) {
                return false;
            }
            if (startDate != null
                    && (row.registrationDate() == null || row.registrationDate().isBefore(startDate))) {
                return false;
            }
            return endDate == null
                    || (row.registrationDate() != null && !row.registrationDate().isAfter(endDate));
        }

        private static int serial(String lightNumber) {
            String digits = value(lightNumber).replaceAll("\\D", "");
            try {
                return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }

        private static String value(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
