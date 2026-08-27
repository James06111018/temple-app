package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.HouseholdLightRecord;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.LightNumberRecord;
import tw.org.il.dongsheng.templeapp.model.LightType;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationSupplement;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationSupplementRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteHouseholdLightRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightNumberRepository;
import tw.org.il.dongsheng.templeapp.service.DonationService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.LightTypeUtil;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static tw.org.il.dongsheng.templeapp.util.Util.currentRocDate;
import static tw.org.il.dongsheng.templeapp.util.Util.parseDate;

public class HouseholdLightController {
    @FXML private ComboBox<String> collectorBox;
    @FXML private TableView<HouseholdLightRow> householdTable;
    @FXML private PaginationBar householdPageBar;
    @FXML private TextField incenseAmountField;
    @FXML private TextField supplementDateField;
    @FXML private TextField supplementNoField;

    private SQLiteHouseholdLightRepository repository;
    private SQLiteDictionaryRepository dictionaryRepository;
    private DonationService donationService;
    private final SQLiteLightNumberRepository lightNumberRepository =
            new SQLiteLightNumberRepository(SQLiteDatabaseManager.getInstance());
    private final SQLiteDonationSupplementRepository supplementRepository =
            new SQLiteDonationSupplementRepository(SQLiteDatabaseManager.getInstance());
    private List<LightType> lightTypes = new ArrayList<>();
    private List<LightMember> currentMembers = new ArrayList<>();
    private Map<String, HouseholdLightRecord> currentRecordMap = new LinkedHashMap<>();
    private int rocYear;

    @FXML
    public void initialize() {
        householdTable.setEditable(true);
        householdPageBar.setTotalCount(0);
        householdPageBar.setOnAction(() -> selectTableRow(householdPageBar.getCurrentIndex()));
        try {
            supplementRepository.createTable();
        } catch (SQLException e) {
            throw new IllegalStateException("初始化補登資料表失敗", e);
        }
    }

    public void setData(List<LightMember> members, SQLiteHouseholdLightRepository repository, SQLiteDictionaryRepository dictionaryRepository, DonationService donationService, int rocYear) {
        this.repository = repository;
        this.dictionaryRepository = dictionaryRepository;
        this.donationService = donationService;
        this.rocYear = rocYear;
        this.currentMembers = new ArrayList<>(members);
        collectorBox.setItems(FXCollections.observableArrayList(
                members.stream()
                        .map(LightMember::getName)
                        .filter(name -> name != null && !name.isBlank())
                        .toList()
        ));
        if (!collectorBox.getItems().isEmpty()) {
            collectorBox.getSelectionModel().selectFirst();
        }
        loadData(members);
    }

    private void loadData(List<LightMember> members) {
        try {
            this.lightTypes = dictionaryRepository.findEnabledItemsByType(SQLiteDictionaryRepository.TYPE_LIGHT).stream()
                    .map(dictionaryRepository::toLightType)
                    .toList();
            List<Integer> memberIds = members.stream().map(LightMember::getId).toList();
            List<HouseholdLightRecord> records = repository.findRecordsByMembersAndYear(memberIds, rocYear);
            currentRecordMap = records.stream()
                    .collect(Collectors.toMap(
                            record -> recordKey(record.getMemberId(), record.getLightTypeId()),
                            record -> record,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            buildColumns();
            householdTable.setItems(FXCollections.observableArrayList(
                    members.stream()
                            .map(member -> new HouseholdLightRow(member, lightTypes, currentRecordMap, rocYear))
                            .toList()
            ));
            householdPageBar.setTotalCount(householdTable.getItems().size());
            selectTableRow(0);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void onOpenLightTypeManagement() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("light-type-management.fxml"));
        Parent root = loader.load();
        LightTypeManagementController controller = loader.getController();
        controller.setRepository(dictionaryRepository);

        Stage stage = new Stage();
        stage.setTitle("燈種管理");
        stage.setScene(new Scene(root, 520, 420));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
        loadData(currentMembers);
    }

    private void buildColumns() {
        householdTable.getColumns().clear();

        TableColumn<HouseholdLightRow, String> markerColumn = textColumn("", 28, row -> "");
        TableColumn<HouseholdLightRow, String> idColumn = textColumn("電腦編號", 92, row -> Util.stringFormat(row.member().getId()));
        TableColumn<HouseholdLightRow, String> nameColumn = textColumn("姓名", 104, row -> row.member().getName());
        TableColumn<HouseholdLightRow, String> zodiacColumn = textColumn("生肖", 54, row -> row.member().getZodiac());
        TableColumn<HouseholdLightRow, String> ageColumn = textColumn("年齡", 54, row -> row.member().getAge() == null ? "" : String.valueOf(row.member().getAge()));

        householdTable.getColumns().add(markerColumn);
        householdTable.getColumns().add(idColumn);
        householdTable.getColumns().add(nameColumn);
        householdTable.getColumns().add(zodiacColumn);
        householdTable.getColumns().add(ageColumn);

        for (LightType lightType : lightTypes) {
            TableColumn<HouseholdLightRow, Boolean> column = new TableColumn<>(lightType.getName());
            column.setPrefWidth(Math.max(72, lightType.getName().length() * 18.0));
            column.setCellValueFactory(data -> data.getValue().lightProperty(lightType.getId()));
            column.setCellFactory(CheckBoxTableCell.forTableColumn(column));
            column.setEditable(true);
            householdTable.getColumns().add(column);
        }

        householdTable.getColumns().add(textColumn("建議事項", 124, HouseholdLightRow::suggestion));
        householdTable.getColumns().add(textColumn("本年度點燈資訊", 300, HouseholdLightRow::yearInfo));
    }

    private TableColumn<HouseholdLightRow, String> textColumn(String title, double width, RowTextProvider provider) {
        TableColumn<HouseholdLightRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(provider.get(data.getValue())));
        return column;
    }

    private void selectTableRow(int index) {
        if (householdTable.getItems().isEmpty()) {
            householdTable.getSelectionModel().clearSelection();
            return;
        }
        int safeIndex = Math.max(0, Math.min(index, householdTable.getItems().size() - 1));
        householdPageBar.setCurrentIndex(safeIndex);
        householdTable.getSelectionModel().select(safeIndex);
        householdTable.scrollTo(safeIndex);
    }

    @FXML
    private void onConfirm() {
        if (repository == null) {
            return;
        }
        HouseholdLightRow selectedRow = householdTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            AlertDialog.showWarning("全戶點燈", "請先選擇一筆信眾資料");
            return;
        }
        List<SelectedLight> selectedLights = collectSelectedLights(selectedRow);
        if (selectedLights.isEmpty()) {
            AlertDialog.showWarning(
                    "全戶點燈",
                    "請至少為「" + selectedRow.member().getName() + "」勾選一個燈別"
            );
            return;
        }

        try {
            List<String> shortages = findInventoryShortages(selectedLights);
            if (!shortages.isEmpty()) {
                AlertDialog.showWarning(
                        "全戶點燈",
                        "下列燈別沒有足夠的未使用燈號：\n"
                                + String.join("\n", shortages)
                                + "\n請先至管理建立燈號。"
                );
                return;
            }
        } catch (SQLException e) {
            AlertDialog.showError("全戶點燈", "檢查燈號失敗：" + e.getMessage());
            return;
        }

        Integer incenseAmount = Util.parseInteger(incenseAmountField.getText());
        if (incenseAmount == null || incenseAmount <= 0) {
            AlertDialog.showWarning("全戶點燈", "請輸入油香金額");
            return;
        }
        LightMember representative = findRepresentative();
        if (representative == null) {
            AlertDialog.showWarning("全戶點燈", "請選擇收據代表人");
            return;
        }
        SupplementInput supplementInput;
        try {
            supplementInput = readSupplementInput();
        } catch (IllegalArgumentException e) {
            AlertDialog.showWarning("全戶點燈", e.getMessage());
            return;
        }

        String changedBy = AuthSession.getCurrentOperatorName();
        try {
            List<SelectedLight> deselectedLights = findDeselectedLights(selectedRow, selectedLights);
            for (SelectedLight selectedLight : selectedLights) {
                saveSelectedLight(selectedLight, changedBy);
            }
            for (SelectedLight deselectedLight : deselectedLights) {
                cancelSelectedLight(deselectedLight, changedBy);
            }
            Donation donation = donationService.save(
                    buildIncenseDonation(representative, incenseAmount, changedBy)
            );
            if (supplementInput != null) {
                supplementRepository.save(new DonationSupplement(
                        null,
                        donation.getId(),
                        supplementInput.date().toString(),
                        supplementInput.number(),
                        SQLiteDonationSupplementRepository.SOURCE_HOUSEHOLD_LIGHT,
                        changedBy,
                        null,
                        changedBy,
                        null,
                        false
                ), changedBy);
            }
            supplementDateField.clear();
            supplementNoField.clear();
            loadData(currentMembers);
            AlertDialog.showInfo("全戶點燈", "儲存成功");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private SupplementInput readSupplementInput() {
        String dateText = supplementDateField.getText() == null
                ? ""
                : supplementDateField.getText().trim();
        String number = supplementNoField.getText() == null
                ? ""
                : supplementNoField.getText().trim();
        if (dateText.isEmpty() && number.isEmpty()) {
            return null;
        }
        if (dateText.isEmpty() || number.isEmpty()) {
            throw new IllegalArgumentException("補登日期與補登號碼必須同時填寫");
        }
        LocalDate date = parseDate(dateText);
        if (date == null) {
            throw new IllegalArgumentException("補登日期請輸入民國 115.05.14 或西元 2026-05-14 格式");
        }
        return new SupplementInput(date, number);
    }

    private List<SelectedLight> collectSelectedLights(HouseholdLightRow row) {
        List<SelectedLight> selectedLights = new ArrayList<>();
        for (LightType lightType : lightTypes) {
            if (row.lightProperty(lightType.getId()).get()) {
                selectedLights.add(new SelectedLight(row, lightType));
            }
        }
        return selectedLights;
    }

    private List<SelectedLight> findDeselectedLights(HouseholdLightRow row, List<SelectedLight> selectedLights) {
        List<SelectedLight> deselectedLights = new ArrayList<>();
        for (LightType lightType : lightTypes) {
            boolean selected = row.lightProperty(lightType.getId()).get();
            HouseholdLightRecord existingRecord = existingRecord(new SelectedLight(row, lightType));
            if (!selected && existingRecord != null) {
                deselectedLights.add(new SelectedLight(row, lightType));
            }
        }
        return deselectedLights;
    }

    private List<String> findInventoryShortages(List<SelectedLight> selectedLights) throws SQLException {
        Map<InventoryKey, List<SelectedLight>> selectionsByInventory = selectedLights.stream()
                .filter(this::requiresLightNumber)
                .collect(Collectors.groupingBy(
                        selected -> inventoryKey(selected.lightType()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<String> shortages = new ArrayList<>();
        for (Map.Entry<InventoryKey, List<SelectedLight>> entry : selectionsByInventory.entrySet()) {
            InventoryKey key = entry.getKey();
            int required = entry.getValue().size();
            int available = lightNumberRepository.countByStatus(
                    key.managementType(),
                    key.lightType(),
                    SQLiteLightNumberRepository.STATUS_UNUSED
            );
            if (available >= required) {
                continue;
            }
            String lightNames = entry.getValue().stream()
                    .map(SelectedLight::lightType)
                    .map(LightType::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining("、"));
            shortages.add(String.format(
                    "%s（管理燈別：%s）：需要 %d 個，目前未使用 %d 個",
                    lightNames,
                    key.lightType(),
                    required,
                    available
            ));
        }
        return shortages;
    }

    private void saveSelectedLight(SelectedLight selectedLight, String changedBy) throws SQLException {
        HouseholdLightRecord existingRecord = existingRecord(selectedLight);
        if (existingRecord != null && !Util.isBlank(existingRecord.getLightNo())) {
            return;
        }

        InventoryKey key = inventoryKey(selectedLight.lightType());
        LightMember member = selectedLight.row().member();
        LightNumberRecord assignedNumber = lightNumberRepository.assignFirstUnused(
                        key.managementType(),
                        key.lightType(),
                        member.getId(),
                        member.getName(),
                        changedBy
                )
                .orElseThrow(() -> new SQLException(
                        "「" + selectedLight.lightType().getName() + "」已無未使用燈號"
                ));
        try {
            repository.saveRecord(new HouseholdLightRecord(
                    existingRecord == null ? null : existingRecord.getId(),
                    member.getId(),
                    selectedLight.lightType().getId(),
                    rocYear,
                    assignedNumber.getDisplayNumber(),
                    existingRecord == null ? "" : existingRecord.getNote(),
                    existingRecord == null ? changedBy : existingRecord.getCreatedBy(),
                    existingRecord == null ? null : existingRecord.getCreatedAt(),
                    changedBy,
                    null
            ), changedBy);
        } catch (SQLException e) {
            lightNumberRepository.releaseAssignment(assignedNumber.getId(), changedBy);
            throw e;
        }
    }

    private void cancelSelectedLight(SelectedLight selectedLight, String changedBy) throws SQLException {
        HouseholdLightRecord existingRecord = existingRecord(selectedLight);
        if (existingRecord == null) {
            return;
        }
        InventoryKey key = inventoryKey(selectedLight.lightType());
        if (!Util.isBlank(existingRecord.getLightNo())) {
            lightNumberRepository.releaseAssignment(
                    key.managementType(),
                    key.lightType(),
                    existingRecord.getLightNo(),
                    changedBy
            );
        }
        repository.deleteRecord(
                selectedLight.row().member().getId(),
                selectedLight.lightType().getId(),
                rocYear,
                changedBy
        );
    }

    private boolean requiresLightNumber(SelectedLight selectedLight) {
        HouseholdLightRecord record = existingRecord(selectedLight);
        return record == null || Util.isBlank(record.getLightNo());
    }

    private HouseholdLightRecord existingRecord(SelectedLight selectedLight) {
        return currentRecordMap.get(recordKey(
                selectedLight.row().member().getId(),
                selectedLight.lightType().getId()
        ));
    }

    private static String recordKey(Integer memberId, Integer lightTypeId) {
        return memberId + ":" + lightTypeId;
    }

    private InventoryKey inventoryKey(LightType lightType) {
        String name = lightType.getName() == null ? "" : lightType.getName().trim();
        if ("安太歲".equals(name)) {
            return new InventoryKey("TAI_SUI", "太");
        }
        return new InventoryKey("LIGHT", LightTypeUtil.abbreviation(name));
    }

    private LightMember findRepresentative() {
        String selectedName = collectorBox.getValue();
        return currentMembers.stream()
                .filter(member -> selectedName != null && selectedName.equals(member.getName()))
                .findFirst()
                .orElse(currentMembers.isEmpty() ? null : currentMembers.get(0));
    }

    private Donation buildIncenseDonation(LightMember representative, int incenseAmount, String changedBy) {
        return new Donation(
                null,
                representative.getId(),
                null,
                currentRocDate(),
                null,
                incenseAmount,
                "全戶點燈",
                "",
                "",
                "",
                "",
                incenseAmount,
                "",
                changedBy,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @FunctionalInterface
    private interface RowTextProvider {
        String get(HouseholdLightRow row);
    }

    private record SelectedLight(HouseholdLightRow row, LightType lightType) {
    }

    private record InventoryKey(String managementType, String lightType) {
    }

    private record SupplementInput(LocalDate date, String number) {
    }

    public record HouseholdLightRow(
            LightMember member,
            Map<Integer, BooleanProperty> lightSelections,
            String suggestion,
            String yearInfo
    ) {
        public HouseholdLightRow(LightMember member, List<LightType> lightTypes, Map<String, HouseholdLightRecord> records, int rocYear) {
            this(member, buildSelections(member, lightTypes, records), buildSuggestion(lightTypes, member, records), buildYearInfo(lightTypes, member, records));
        }

        public BooleanProperty lightProperty(Integer lightTypeId) {
            return lightSelections.computeIfAbsent(lightTypeId, id -> new SimpleBooleanProperty(false));
        }

        private static Map<Integer, BooleanProperty> buildSelections(LightMember member, List<LightType> lightTypes, Map<String, HouseholdLightRecord> records) {
            Map<Integer, BooleanProperty> selections = new LinkedHashMap<>();
            for (LightType lightType : lightTypes) {
                selections.put(lightType.getId(), new SimpleBooleanProperty(records.containsKey(member.getId() + ":" + lightType.getId())));
            }
            return selections;
        }

        private static String buildSuggestion(List<LightType> lightTypes, LightMember member, Map<String, HouseholdLightRecord> records) {
            boolean hasTaiSui = lightTypes.stream()
                    .filter(type -> "安太歲".equals(type.getName()))
                    .anyMatch(type -> records.containsKey(member.getId() + ":" + type.getId()));
            return hasTaiSui ? "安太歲" : "";
        }

        private static String buildYearInfo(List<LightType> lightTypes, LightMember member, Map<String, HouseholdLightRecord> records) {
            return lightTypes.stream()
                    .filter(type -> records.containsKey(member.getId() + ":" + type.getId()))
                    .map(type -> {
                        HouseholdLightRecord record = records.get(member.getId() + ":" + type.getId());
                        return Util.emptyToDefault(record.getLightNo(), type.getName());
                    })
                    .collect(Collectors.joining(" "));
        }
    }
}
