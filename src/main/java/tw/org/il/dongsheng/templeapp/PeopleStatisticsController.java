package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.PeopleStatisticsRow;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;
import tw.org.il.dongsheng.templeapp.util.PeopleStatisticsReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PeopleStatisticsController {

    @FXML private RadioButton allZipRadio, customZipRadio;
    @FXML private TextField zipStartField, zipEndField;
    @FXML private Label totalCountLabel;
    @FXML private ComboBox<String> reportTypeBox;
    @FXML private CheckBox allMailCheck, sortByZipCheck;
    @FXML private PaginationBar peopleStatisticsPageBar;
    @FXML private TableView<PeopleStatisticsRow> peopleStatisticsTable;
    @FXML private TableColumn<PeopleStatisticsRow, String> memberIdColumn, nameColumn, addressColumn,
            phoneColumn, zipCodeColumn, mailColumn, totalAmountColumn;

    private final SQLiteLightMemberRepository memberRepository = new SQLiteLightMemberRepository(
            SQLiteDatabaseManager.getInstance()
    );
    private final NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);
    private List<PeopleStatisticsRow> sourceRows = List.of();

    @FXML
    private void initialize() {
        allZipRadio.setSelected(true);
        reportTypeBox.setItems(FXCollections.observableArrayList(
                "1. 郵寄標籤(橫式)",
                "2. 郵寄標籤(直式)",
                "3. 捐款名冊一",
                "4. 捐款名冊二",
                "5. 捐款名條(金額)",
                "7. 通信函",
                "9. 郵寄標籤(多人)"
        ));
        reportTypeBox.getSelectionModel().selectFirst();
        configureColumns();

        peopleStatisticsPageBar.setTotalCount(0);
        peopleStatisticsPageBar.setOnAction(() -> selectRow(peopleStatisticsPageBar.getCurrentIndex()));
        peopleStatisticsTable.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, selectedIndex) -> {
                    if (selectedIndex.intValue() >= 0) {
                        peopleStatisticsPageBar.setCurrentIndex(selectedIndex.intValue());
                    }
                }
        );
        updateRows(List.of());
    }

    public void setDonations(List<Donation> donations) {
        try {
            Map<Integer, LightMember> membersById = memberRepository.findAll().stream()
                    .collect(Collectors.toMap(
                            LightMember::getId,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            Map<Integer, Long> amountsByMember = donations.stream()
                    .filter(donation -> donation.getMemberId() != null)
                    .collect(Collectors.groupingBy(
                            Donation::getMemberId,
                            LinkedHashMap::new,
                            Collectors.summingLong(this::amountOf)
                    ));

            sourceRows = amountsByMember.entrySet().stream()
                    .map(entry -> toRow(membersById.get(entry.getKey()), entry.getValue()))
                    .filter(row -> row != null)
                    .sorted(Comparator.comparingInt(PeopleStatisticsRow::memberId))
                    .toList();
            allZipRadio.setSelected(true);
            zipStartField.clear();
            zipEndField.clear();
            updateRows(sourceRows);
        } catch (SQLException exception) {
            sourceRows = List.of();
            updateRows(sourceRows);
            AlertDialog.showError("人數統計", "讀取信眾資料失敗：" + exception.getMessage());
        }
    }

    @FXML
    private void onFilter() {
        if (allZipRadio.isSelected()) {
            updateRows(sourceRows);
            return;
        }

        Integer start = parseZipCode(zipStartField.getText());
        Integer end = parseZipCode(zipEndField.getText());
        if (start == null || end == null) {
            AlertDialog.showWarning("人數統計", "請輸入完整的郵遞區號起訖範圍");
            return;
        }
        if (start > end) {
            AlertDialog.showWarning("人數統計", "郵遞區號起始值不可大於結束值");
            return;
        }

        List<PeopleStatisticsRow> filtered = sourceRows.stream()
                .filter(row -> {
                    Integer zipCode = parseZipCode(row.zipCode());
                    return zipCode != null && zipCode >= start && zipCode <= end;
                })
                .toList();
        updateRows(filtered);
    }

    @FXML
    private void onPrint() {
        List<PeopleStatisticsRow> rows = peopleStatisticsTable.getItems().stream()
                .filter(row -> allMailCheck.isSelected() || isMarkedForMail(row))
                .toList();
        if (rows.isEmpty()) {
            AlertDialog.showWarning("人數統計", "依目前條件查無可列印的信眾資料");
            return;
        }

        int reportType = reportTypeBox.getSelectionModel().getSelectedIndex();
        Comparator<PeopleStatisticsRow> labelOrder = sortByZipCheck.isSelected()
                ? Comparator.comparingInt((PeopleStatisticsRow row) -> zipNumber(row.zipCode()))
                        .thenComparingInt(PeopleStatisticsRow::memberId)
                : Comparator.comparingInt(PeopleStatisticsRow::memberId);
        List<PeopleStatisticsRow> orderedRows = rows.stream().sorted(labelOrder).toList();
        var pages = switch (reportType) {
            case 0 -> PeopleStatisticsReportBuilder.buildHorizontalLabelPages(orderedRows);
            case 1 -> PeopleStatisticsReportBuilder.buildVerticalLabelPages(orderedRows);
            case 2 -> PeopleStatisticsReportBuilder.buildDonationRosterPages(rows, false);
            case 3 -> PeopleStatisticsReportBuilder.buildDonationRosterPages(rows, true);
            case 4 -> PeopleStatisticsReportBuilder.buildDonationNameLabelPages(rows);
            case 5 -> PeopleStatisticsReportBuilder.buildLetterPages(orderedRows);
            case 6 -> PeopleStatisticsReportBuilder.buildMultiRecipientLabelPages(orderedRows);
            default -> List.<javafx.scene.layout.Region>of();
        };
        PrintPreview.show(reportTypeBox.getScene().getWindow(), reportTypeBox.getValue(), pages);
    }

    private void configureColumns() {
        memberIdColumn.setCellValueFactory(cell -> text(Util.stringFormat(cell.getValue().memberId())));
        nameColumn.setCellValueFactory(cell -> text(cell.getValue().name()));
        addressColumn.setCellValueFactory(cell -> text(cell.getValue().address()));
        phoneColumn.setCellValueFactory(cell -> text(cell.getValue().phone()));
        zipCodeColumn.setCellValueFactory(cell -> text(cell.getValue().zipCode()));
        mailColumn.setCellValueFactory(cell -> text(cell.getValue().isMail()));
        totalAmountColumn.setCellValueFactory(cell -> text(integerFormat.format(cell.getValue().totalAmount())));
        totalAmountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    private void updateRows(List<PeopleStatisticsRow> rows) {
        peopleStatisticsTable.setItems(FXCollections.observableArrayList(rows));
        totalCountLabel.setText(rows.size() + " 筆");
        peopleStatisticsPageBar.setTotalCount(rows.size());
        selectRow(rows.isEmpty() ? -1 : 0);
    }

    private void selectRow(int index) {
        if (index < 0 || index >= peopleStatisticsTable.getItems().size()) {
            peopleStatisticsTable.getSelectionModel().clearSelection();
            return;
        }
        peopleStatisticsTable.getSelectionModel().select(index);
        peopleStatisticsTable.scrollTo(index);
    }

    private PeopleStatisticsRow toRow(LightMember member, long totalAmount) {
        if (member == null || member.getId() == null) {
            return null;
        }
        return new PeopleStatisticsRow(
                member.getId(),
                normalized(member.getName()),
                fullAddress(member),
                normalized(member.getPhone()),
                normalized(member.getZipCode()),
                normalized(member.getIsMail()),
                totalAmount
        );
    }

    private long amountOf(Donation donation) {
        return tw.org.il.dongsheng.templeapp.util.DonationAmounts.actual(donation);
    }

    private boolean isMarkedForMail(PeopleStatisticsRow row) {
        return "Y".equalsIgnoreCase(normalized(row.isMail()));
    }

    private int zipNumber(String value) {
        Integer zipCode = parseZipCode(value);
        return zipCode == null ? Integer.MAX_VALUE : zipCode;
    }

    private Integer parseZipCode(String value) {
        try {
            String normalized = normalized(value);
            return normalized.matches("\\d{3,6}") ? Integer.valueOf(normalized) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private String fullAddress(LightMember member) {
        String address = normalized(member.getAddress());
        String city = normalized(member.getCity());
        String district = normalized(member.getDist());
        StringBuilder result = new StringBuilder();
        if (!city.isEmpty() && !containsAddressPart(address, city)) {
            result.append(city);
        }
        if (!district.isEmpty() && !containsAddressPart(address, district)) {
            result.append(district);
        }
        return result.append(address).toString();
    }

    private boolean containsAddressPart(String address, String part) {
        return address.replace('台', '臺').contains(part.replace('台', '臺'));
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    public boolean isAllMailSelected() {
        return allMailCheck.isSelected();
    }

    public boolean isSortByZipSelected() {
        return sortByZipCheck.isSelected();
    }

}
