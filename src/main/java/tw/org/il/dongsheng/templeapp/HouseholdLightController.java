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
import tw.org.il.dongsheng.templeapp.model.LightType;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteHouseholdLightRepository;
import tw.org.il.dongsheng.templeapp.service.DonationService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
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

public class HouseholdLightController {
    @FXML private ComboBox<String> collectorBox;
    @FXML private TableView<HouseholdLightRow> householdTable;
    @FXML private PaginationBar householdPageBar;
    @FXML private TextField incenseAmountField;

    private SQLiteHouseholdLightRepository repository;
    private SQLiteDictionaryRepository dictionaryRepository;
    private DonationService donationService;
    private List<LightType> lightTypes = new ArrayList<>();
    private List<LightMember> currentMembers = new ArrayList<>();
    private int rocYear;

    @FXML
    public void initialize() {
        householdTable.setEditable(true);
        householdPageBar.setTotalCount(0);
        householdPageBar.setOnAction(() -> selectTableRow(householdPageBar.getCurrentIndex()));
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
            Map<String, HouseholdLightRecord> recordMap = records.stream()
                    .collect(Collectors.toMap(
                            record -> record.getMemberId() + ":" + record.getLightTypeId(),
                            record -> record,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            buildColumns();
            householdTable.setItems(FXCollections.observableArrayList(
                    members.stream()
                            .map(member -> new HouseholdLightRow(member, lightTypes, recordMap, rocYear))
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
    private void onPlaceholderAction() {
        if (repository == null) {
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

        String changedBy = Util.emptyToDefault(collectorBox.getValue(), System.getProperty("user.name"));
        try {
            boolean hasSelectedLight = false;
            for (HouseholdLightRow row : householdTable.getItems()) {
                for (LightType lightType : lightTypes) {
                    boolean selected = row.lightProperty(lightType.getId()).get();
                    if (selected) {
                        hasSelectedLight = true;
                        repository.saveRecord(new HouseholdLightRecord(
                                null,
                                row.member().getId(),
                                lightType.getId(),
                                rocYear,
                                "",
                                "",
                                changedBy,
                                null,
                                changedBy,
                                null
                        ), changedBy);
                    }
                }
            }
            if (!hasSelectedLight) {
                AlertDialog.showWarning("全戶點燈", "請至少勾選一個燈別");
                return;
            }
            donationService.save(buildIncenseDonation(representative, incenseAmount, changedBy));
            loadData(currentMembers);
            AlertDialog.showInfo("全戶點燈", "儲存成功");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private LightMember findRepresentative() {
        String selectedName = collectorBox.getValue();
        return currentMembers.stream()
                .filter(member -> selectedName != null && selectedName.equals(member.getName()))
                .findFirst()
                .orElse(currentMembers.isEmpty() ? null : currentMembers.get(0));
    }

    private Donation buildIncenseDonation(LightMember representative, int incenseAmount, String changedBy) throws SQLException {
        DictionaryItem incenseType = dictionaryRepository.findEnabledItemsByType(SQLiteDictionaryRepository.TYPE_DONATION_LIGHT).stream()
                .filter(item -> "1".equals(item.getCode()) || "油香".equals(item.getName()))
                .findFirst()
                .orElseThrow(() -> new SQLException("找不到油香款項類別"));
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
                String.valueOf(incenseType.getId()),
                changedBy
        );
    }

    private String currentRocDate() {
        LocalDate today = LocalDate.now();
        return String.format("%03d.%02d.%02d", today.getYear() - 1911, today.getMonthValue(), today.getDayOfMonth());
    }

    @FunctionalInterface
    private interface RowTextProvider {
        String get(HouseholdLightRow row);
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
