package tw.org.il.dongsheng.templeapp;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationDetailRow;
import tw.org.il.dongsheng.templeapp.model.DonationSupplement;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationSupplementRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.DonationDetailsExcelPreview;
import tw.org.il.dongsheng.templeapp.util.DonationDetailsReportBuilder;
import tw.org.il.dongsheng.templeapp.util.DonationRosterReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.math.BigInteger;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DonationDetailsController {
    private static final String SORT_DEFAULT = "順序";
    private static final String SORT_NAME = "姓名";
    private static final String SORT_AMOUNT_ASC = "金額（低->高）";
    private static final String SORT_AMOUNT_DESC = "金額（高->低）";
    private static final String SORT_RECEIPT = "收據編號";
    private static final String SORT_SUPPLEMENT = "補登號碼";
    private static final String SORT_DATE = "捐款日期";

    private enum DetailMode {
        ALL,
        SUPPLEMENT,
        UNPAID
    }

    @FXML private ComboBox<String> sortBox;
    @FXML private PaginationBar donationDetailsPageBar;
    @FXML private TableView<DonationDetailRow> donationDetailsTable;
    @FXML private TableColumn<DonationDetailRow, String> nameColumn;
    @FXML private TableColumn<DonationDetailRow, String> receiptNoColumn;
    @FXML private TableColumn<DonationDetailRow, String> donationDateColumn;
    @FXML private TableColumn<DonationDetailRow, String> amountColumn;
    @FXML private TableColumn<DonationDetailRow, String> summaryColumn;
    @FXML private TableColumn<DonationDetailRow, String> categoryColumn;
    @FXML private TableColumn<DonationDetailRow, String> donationNoteColumn;
    @FXML private TableColumn<DonationDetailRow, String> memberIdColumn;
    @FXML private TableColumn<DonationDetailRow, String> taiSuiLightNoColumn;
    @FXML private TableColumn<DonationDetailRow, String> brightLightNoColumn;
    @FXML private TableColumn<DonationDetailRow, String> amountDueColumn;
    @FXML private TableColumn<DonationDetailRow, String> operatorColumn;
    @FXML private TableColumn<DonationDetailRow, String> descriptionColumn;

    private final NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);
    private List<DonationDetailRow> sourceRows = List.of();
    private DetailMode detailMode = DetailMode.ALL;

    @FXML
    private void initialize() {
        configureColumns();
        sortBox.setItems(FXCollections.observableArrayList(
                SORT_DEFAULT,
                SORT_NAME,
                SORT_AMOUNT_ASC,
                SORT_AMOUNT_DESC,
                SORT_RECEIPT,
                SORT_SUPPLEMENT,
                SORT_DATE
        ));
        sortBox.getSelectionModel().selectFirst();
        sortBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshRows());

        donationDetailsPageBar.setTotalCount(0);
        donationDetailsPageBar.setOnAction(() -> selectRow(donationDetailsPageBar.getCurrentIndex()));
        donationDetailsTable.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, newValue) -> {
                    int index = newValue.intValue();
                    if (index >= 0) {
                        donationDetailsPageBar.setCurrentIndex(index);
                    }
                }
        );
    }

    public void setDonations(List<Donation> donations) {
        try {
            sourceRows = buildRows(donations == null ? List.of() : donations);
            detailMode = DetailMode.ALL;
            refreshRows();
        } catch (SQLException e) {
            sourceRows = List.of();
            refreshRows();
            AlertDialog.showError("捐款明細", "讀取捐款明細失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onShowPaymentDetails() {
        List<DonationDetailRow> rows = reportRows(DetailMode.ALL);
        if (!ensureReportRows(rows)) {
            return;
        }
        showReportTitleDialog("油香明細表").ifPresent(title ->
                PrintPreview.show(
                        donationDetailsTable.getScene().getWindow(),
                        title,
                        DonationDetailsReportBuilder.buildPages(
                                title,
                                rows,
                                DonationDetailsReportBuilder.ReportKind.PAYMENT
                        )
                )
        );
    }

    @FXML
    private void onShowSupplementDetails() {
        showReport(
                "補登明細表",
                reportRows(DetailMode.SUPPLEMENT),
                DonationDetailsReportBuilder.ReportKind.SUPPLEMENT
        );
    }

    @FXML
    private void onShowUnpaidDetails() {
        showReport(
                "未繳清明細表",
                reportRows(DetailMode.UNPAID),
                DonationDetailsReportBuilder.ReportKind.UNPAID
        );
    }

    @FXML
    private void onPrintRoster() {
        List<DonationDetailRow> rows = reportRows(DetailMode.ALL);
        if (!ensureReportRows(rows)) {
            return;
        }
        showReportTitleDialog("油香名冊").ifPresent(title ->
                PrintPreview.show(
                        donationDetailsTable.getScene().getWindow(),
                        title,
                        DonationRosterReportBuilder.buildRosterPages(title, rows)
                )
        );
    }

    @FXML
    private void onPrintDonationList() {
        List<DonationDetailRow> rows = reportRows(DetailMode.ALL);
        if (!ensureReportRows(rows)) {
            return;
        }
        PrintPreview.show(
                donationDetailsTable.getScene().getWindow(),
                "捐獻名單",
                DonationRosterReportBuilder.buildDonationListPages(rows)
        );
    }

    @FXML
    private void onExportExcel() {
        List<DonationDetailRow> rows = reportRows(DetailMode.ALL);
        if (!ensureReportRows(rows)) {
            return;
        }
        DonationDetailsExcelPreview.show(donationDetailsTable.getScene().getWindow(), rows);
    }

    private void configureColumns() {
        nameColumn.setCellValueFactory(cell -> text(cell.getValue().name()));
        receiptNoColumn.setCellValueFactory(cell -> text(cell.getValue().receiptNo()));
        donationDateColumn.setCellValueFactory(cell -> text(cell.getValue().donationDate()));
        amountColumn.setCellValueFactory(cell -> text(formatAmount(cell.getValue().amount())));
        summaryColumn.setCellValueFactory(cell -> text(cell.getValue().summary()));
        categoryColumn.setCellValueFactory(cell -> text(cell.getValue().category()));
        donationNoteColumn.setCellValueFactory(cell -> text(cell.getValue().donationNote()));
        memberIdColumn.setCellValueFactory(cell -> text(cell.getValue().memberIdText()));
        taiSuiLightNoColumn.setCellValueFactory(cell -> text(cell.getValue().taiSuiLightNo()));
        brightLightNoColumn.setCellValueFactory(cell -> text(cell.getValue().brightLightNo()));
        amountDueColumn.setCellValueFactory(cell -> text(formatAmount(cell.getValue().amountDue())));
        operatorColumn.setCellValueFactory(cell -> text(cell.getValue().operator()));
        descriptionColumn.setCellValueFactory(cell -> text(cell.getValue().description()));
        amountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        amountDueColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    private List<DonationDetailRow> buildRows(List<Donation> donations) throws SQLException {
        SQLiteDatabaseManager manager = SQLiteDatabaseManager.getInstance();
        Map<Integer, LightMember> membersById = new SQLiteLightMemberRepository(manager).findAll().stream()
                .collect(Collectors.toMap(
                        LightMember::getId,
                        member -> member,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, DictionaryItem> categoriesById = new SQLiteDictionaryRepository(manager).findAllItems().stream()
                .collect(Collectors.toMap(
                        item -> String.valueOf(item.getId()),
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Integer, DonationSupplement> supplementsByDonationId =
                new SQLiteDonationSupplementRepository(manager)
                        .findByDateRange(LocalDate.of(1912, 1, 1), LocalDate.of(9999, 12, 31), null)
                        .stream()
                        .collect(Collectors.toMap(
                                DonationSupplement::getDonationId,
                                supplement -> supplement,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));

        List<DonationDetailRow> rows = new ArrayList<>();
        for (int index = 0; index < donations.size(); index++) {
            Donation donation = donations.get(index);
            LightMember member = membersById.get(donation.getMemberId());
            DictionaryItem category = categoriesById.get(normalized(donation.getDonateType()));
            DonationSupplement supplement = donation.getId() == null
                    ? null
                    : supplementsByDonationId.get(donation.getId());
            String supplementNo = firstNotBlank(
                    donation.getExtraNo(),
                    supplement == null ? "" : supplement.getSupplementNo()
            );
            rows.add(new DonationDetailRow(
                    index,
                    donation.getId(),
                    member == null ? "" : normalized(member.getName()),
                    member == null ? "" : normalized(member.getAddress()),
                    member == null ? "" : normalized(member.getPhone()),
                    normalized(donation.getReceiptNo()),
                    toRocDate(donation.getDonateDate()),
                    parseDate(donation.getDonateDate()),
                    donation.getAmount(),
                    normalized(donation.getSummary()),
                    category == null ? normalized(donation.getDonateType()) : category.toString(),
                    normalized(donation.getDonateNote()),
                    donation.getMemberId(),
                    donation.getMemberId() == null ? "" : Util.stringFormat(donation.getMemberId()),
                    normalized(donation.getDonorNo()),
                    normalized(donation.getLightNo()),
                    donation.getShouldPay(),
                    normalized(donation.getCreator()),
                    normalized(donation.getOtherNote()),
                    supplementNo
            ));
        }
        return List.copyOf(rows);
    }

    private void refreshRows() {
        Predicate<DonationDetailRow> modeFilter = switch (detailMode) {
            case SUPPLEMENT -> row -> !normalized(row.supplementNo()).isEmpty();
            case UNPAID -> row -> amount(row.amountDue()) > amount(row.amount());
            default -> row -> true;
        };
        Comparator<DonationDetailRow> comparator = comparatorFor(sortBox.getValue());
        List<DonationDetailRow> rows = sourceRows.stream()
                .filter(modeFilter)
                .sorted(comparator)
                .toList();
        donationDetailsTable.setItems(FXCollections.observableArrayList(rows));
        donationDetailsPageBar.setTotalCount(rows.size());
        donationDetailsPageBar.setCurrentIndex(0);
        selectRow(rows.isEmpty() ? -1 : 0);
    }

    private List<DonationDetailRow> reportRows(DetailMode mode) {
        Predicate<DonationDetailRow> filter = switch (mode) {
            case SUPPLEMENT -> row -> !normalized(row.supplementNo()).isEmpty();
            case UNPAID -> row -> amount(row.amountDue()) > amount(row.amount());
            default -> row -> true;
        };
        return sourceRows.stream()
                .filter(filter)
                .sorted(comparatorFor(sortBox.getValue()))
                .toList();
    }

    private void showReport(
            String title,
            List<DonationDetailRow> rows,
            DonationDetailsReportBuilder.ReportKind kind
    ) {
        if (!ensureReportRows(rows)) {
            return;
        }
        PrintPreview.show(
                donationDetailsTable.getScene().getWindow(),
                title,
                DonationDetailsReportBuilder.buildPages(title, rows, kind)
        );
    }

    private boolean ensureReportRows(List<DonationDetailRow> rows) {
        if (rows != null && !rows.isEmpty()) {
            return true;
        }
        AlertDialog.showInfo("捐款明細", "查無資料");
        return false;
    }

    private Optional<String> showReportTitleDialog(String defaultTitle) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("提示");
        dialog.setHeaderText(null);
        if (donationDetailsTable.getScene() != null) {
            dialog.initOwner(donationDetailsTable.getScene().getWindow());
        }

        ButtonType confirmButton = new ButtonType("確定", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButton, cancelButton);

        Label instruction = new Label("請輸入報表抬頭");
        TextField titleField = new TextField(defaultTitle);
        VBox content = new VBox(18, instruction, titleField);
        content.setPadding(new Insets(12));
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);

        Node confirmNode = dialog.getDialogPane().lookupButton(confirmButton);
        confirmNode.addEventFilter(ActionEvent.ACTION, event -> {
            if (normalized(titleField.getText()).isEmpty()) {
                AlertDialog.showWarning("提示", "請輸入報表抬頭");
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == confirmButton ? normalized(titleField.getText()) : null);
        Platform.runLater(titleField::selectAll);
        return dialog.showAndWait();
    }

    private Comparator<DonationDetailRow> comparatorFor(String sort) {
        Comparator<DonationDetailRow> fallback = Comparator.comparingInt(DonationDetailRow::originalOrder);
        Comparator<DonationDetailRow> selected = switch (sort == null ? SORT_DEFAULT : sort) {
            case SORT_NAME -> Comparator.comparing(
                    row -> normalized(row.name()),
                    String.CASE_INSENSITIVE_ORDER
            );
            case SORT_AMOUNT_ASC -> Comparator.comparingLong(row -> amount(row.amount()));
            case SORT_AMOUNT_DESC -> Comparator.comparingLong(
                    (DonationDetailRow row) -> amount(row.amount())
            ).reversed();
            case SORT_RECEIPT -> Comparator.comparing(
                    DonationDetailRow::receiptNo,
                    this::compareNumberAware
            );
            case SORT_SUPPLEMENT -> Comparator.comparing(
                    DonationDetailRow::supplementNo,
                    this::compareNumberAware
            );
            case SORT_DATE -> Comparator.comparing(
                    DonationDetailRow::donationDateValue,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> fallback;
        };
        return selected == fallback ? fallback : selected.thenComparing(fallback);
    }

    private int compareNumberAware(String left, String right) {
        String first = normalized(left);
        String second = normalized(right);
        if (first.isEmpty() || second.isEmpty()) {
            return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                    .compare(first.isEmpty() ? null : first, second.isEmpty() ? null : second);
        }
        if (first.matches("\\d+") && second.matches("\\d+")) {
            return new BigInteger(first).compareTo(new BigInteger(second));
        }
        return first.compareToIgnoreCase(second);
    }

    private void selectRow(int index) {
        if (index < 0 || index >= donationDetailsTable.getItems().size()) {
            donationDetailsTable.getSelectionModel().clearSelection();
            return;
        }
        donationDetailsTable.getSelectionModel().select(index);
        donationDetailsTable.scrollTo(index);
    }

    private ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(normalized(value));
    }

    private String formatAmount(Integer value) {
        return value == null ? "" : integerFormat.format(value);
    }

    private long amount(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private LocalDate parseDate(String value) {
        String normalized = normalized(value).replace('/', '.').replace('-', '.');
        if (normalized.isEmpty()) {
            return null;
        }
        String[] parts = normalized.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            return LocalDate.of(
                    year < 1912 ? year + 1911 : year,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String toRocDate(String value) {
        LocalDate date = parseDate(value);
        if (date == null) {
            return normalized(value);
        }
        return String.format("%03d.%02d.%02d", date.getYear() - 1911, date.getMonthValue(), date.getDayOfMonth());
    }

    private String firstNotBlank(String first, String second) {
        return normalized(first).isEmpty() ? normalized(second) : normalized(first);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
