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
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.HouseholdLightRecord;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.LightType;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteHouseholdLightRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HouseholdLightController {
    @FXML private ComboBox<String> collectorBox;
    @FXML private TableView<HouseholdLightRow> householdTable;

    private SQLiteHouseholdLightRepository repository;
    private SQLiteDictionaryRepository dictionaryRepository;
    private List<LightType> lightTypes = new ArrayList<>();
    private List<LightMember> currentMembers = new ArrayList<>();
    private int rocYear;

    @FXML
    public void initialize() {
        collectorBox.setItems(FXCollections.observableArrayList("", "林暐皓"));
        collectorBox.getSelectionModel().select(1);
        householdTable.setEditable(true);
    }

    public void setData(List<LightMember> members, SQLiteHouseholdLightRepository repository, SQLiteDictionaryRepository dictionaryRepository, int rocYear) {
        this.repository = repository;
        this.dictionaryRepository = dictionaryRepository;
        this.rocYear = rocYear;
        this.currentMembers = new ArrayList<>(members);
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

    @FXML
    private void onPlaceholderAction() {
        if (repository == null) {
            return;
        }
        String changedBy = Util.emptyToDefault(collectorBox.getValue(), System.getProperty("user.name"));
        try {
            for (HouseholdLightRow row : householdTable.getItems()) {
                for (LightType lightType : lightTypes) {
                    boolean selected = row.lightProperty(lightType.getId()).get();
                    if (selected) {
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
                    } else {
                        repository.deleteRecord(row.member().getId(), lightType.getId(), rocYear, changedBy);
                    }
                }
            }
            loadData(currentMembers);
            AlertDialog.showInfo("全戶點燈", "儲存成功");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
