package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.*;
import tw.org.il.dongsheng.templeapp.repository.sqlite.*;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.CheckoutReportBuilder;
import tw.org.il.dongsheng.templeapp.util.LightReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static tw.org.il.dongsheng.templeapp.util.Util.*;

public class CheckoutReportController {

    final String TITLE = "結帳報表";
    @FXML private TextField operatorField, startDateField, endDateField, receiptNoField;

    private SQLiteDonationRepository donationRepository;
    private SQLiteDictionaryRepository dictionaryRepository;
    private SQLiteLightMemberRepository memberRepository;
    private SQLiteDonationSupplementRepository supplementRepository;
    private SQLiteAuthRepository authRepository;

    @FXML
    private void initialize() {
        String today = currentRocDate();
        startDateField.setText(today);
        endDateField.setText(today);

        SQLiteDatabaseManager manager = SQLiteDatabaseManager.getInstance();
        dictionaryRepository = new SQLiteDictionaryRepository(manager);
        donationRepository = new SQLiteDonationRepository(manager);
        memberRepository = new SQLiteLightMemberRepository(manager);
        supplementRepository = new SQLiteDonationSupplementRepository(manager);
        authRepository = new SQLiteAuthRepository(manager);
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo(TITLE, "此功能尚未實作");
    }

    @FXML
    private void onChooseOperator() {
        try {
            List<AppUser> users = authRepository.findAllUsers().stream()
                    .filter(AppUser::isEnabled)
                    .toList();
            ListView<AppUser> listView = new ListView<>(FXCollections.observableArrayList(users));
            listView.setPrefSize(220, 280);

            Stage stage = new Stage();
            stage.setTitle("經辦人");
            stage.setScene(new Scene(listView));
            stage.initOwner(operatorField.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            listView.setOnMouseClicked(event -> {
                AppUser selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null && event.getClickCount() == 2) {
                    operatorField.setText(selected.toString());
                    stage.close();
                }
            });
            stage.showAndWait();
        } catch (SQLException e) {
            AlertDialog.showError(TITLE, "讀取經辦人失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onOpenDetailReport() {
        openReport(ReportKind.DETAIL);
    }
    @FXML
    private void onOpenAllReport() {
//        openSupplementReport(SupplementReportKind.ALL);
    }
    @FXML
    private void onOpenClassificationReport() {
        openReport(ReportKind.CLASSIFICATION);
    }
    @FXML
    private void onOpenSupplementDetailReport() {
        openSupplementReport(SupplementReportKind.DETAIL);
    }

    private void openReport(ReportKind kind) {
        LocalDate startDate = parseDate(startDateField.getText());
        LocalDate endDate = parseDate(endDateField.getText());
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                AlertDialog.showWarning(TITLE, "起始日期不可晚於結束日期");
                return;
            }
        }
        String operator = operatorField.getText();
        String receiptNo = receiptNoField.getText();

        try {
            ReportData data = loadReportData(startDate, endDate, operator, receiptNo);
            if (data.donations().isEmpty()) {
                AlertDialog.showInfo("TITLE", "查無指定條件內的點燈或中元普渡結帳資料");
                return;
            }
            List<? extends javafx.scene.layout.Region> pages = switch (kind) {
                case DETAIL -> CheckoutReportBuilder.buildIncomeDetailPages(
                        data.donations(), data.membersById(), data.categoryNames()
                );
                case CLASSIFICATION -> CheckoutReportBuilder.buildClassificationPages(
                        data.membersById(), data.donations(), data.categoryNames
                );
                case DAILY_CATEGORY -> null;
                case MONTHLY_DAILY -> null;
                case SUPPLEMENT_DETAIL -> null;
            };
            PrintPreview.show(startDateField.getScene().getWindow(), kind.title, pages);
        } catch (SQLException e) {
            AlertDialog.showError(TITLE, "讀取結帳資料失敗：" + e.getMessage());
        }
    }

    private ReportData loadReportData(LocalDate startDate, LocalDate endDate, String operator, String receiptNo) throws SQLException {
        List<DictionaryItem> categoryItems = new ArrayList<>();
        categoryItems.addAll(dictionaryRepository.findItemsByType(
                SQLiteDictionaryRepository.TYPE_DONATION_LIGHT
        ));
        categoryItems.addAll(dictionaryRepository.findItemsByType(
                SQLiteDictionaryRepository.TYPE_DONATION_GHOST
        ));

        Set<String> incomeCategoryIds = categoryItems.stream()
                .filter(item -> !"-".equals(item.getDirection()))
                .map(DictionaryItem::getId)
                .map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> categoryNames = categoryItems.stream()
                .filter(item -> incomeCategoryIds.contains(String.valueOf(item.getId())))
                .collect(Collectors.toMap(
                        item -> String.valueOf(item.getId()),
                        DictionaryItem::getName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<Donation> donations = donationRepository.findAll(convertToDbDateString(startDate), convertToDbDateString(endDate), receiptNo, operator).stream()
                .filter(donation -> incomeCategoryIds.contains(donation.getDonateType()))
                .sorted(Comparator
                        .comparing(
                                (Donation donation) -> parseDate(donation.getDonateDate()),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(Donation::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<Integer, LightMember> membersById = memberRepository.findAllIncludingDeleted().stream()
                .collect(Collectors.toMap(
                        LightMember::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return new ReportData(donations, membersById, categoryNames);
    }

    private void openSupplementReport(SupplementReportKind kind) {
        LocalDate startDate = parseDate(startDateField.getText());
        LocalDate endDate = parseDate(endDateField.getText());
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                AlertDialog.showWarning(TITLE, "起始日期不可晚於結束日期");
                return;
            }
        }
        String operator = operatorField.getText();
        String receiptNo = receiptNoField.getText();

        try {
            List<DonationSupplement> supplements = supplementRepository.findByDateRange(startDate, endDate);
            List<Integer> donationIds = supplements.stream()
                    .map(DonationSupplement::getDonationId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Integer, Donation> donationsById = donationRepository.findByIds(donationIds).stream()
                    .collect(Collectors.toMap(
                            Donation::getId,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            supplements = supplements.stream()
                    .filter(supplement -> donationsById.containsKey(supplement.getDonationId()))
                    .toList();
            if (supplements.isEmpty()) {
                AlertDialog.showInfo("補登款項", "查無指定補登日期內的資料");
                return;
            }

            Map<Integer, LightMember> membersById = memberRepository.findAllIncludingDeleted().stream()
                    .collect(Collectors.toMap(
                            LightMember::getId,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            Map<String, String> categoryNames = loadAllDonationCategoryNames();
            List<? extends javafx.scene.layout.Region> pages = switch (kind) {
                case DETAIL -> CheckoutReportBuilder.buildSupplementDetailPages(
                        supplements, donationsById, membersById, categoryNames
                );
                case ALL -> CheckoutReportBuilder.buildSupplementAllPages(
                        supplements, donationsById, membersById, categoryNames
                );
            };
            PrintPreview.show(startDateField.getScene().getWindow(), kind.title, pages);
        } catch (SQLException e) {
            AlertDialog.showError(TITLE, "讀取補登資料失敗：" + e.getMessage());
        }
    }

    private Map<String, String> loadAllDonationCategoryNames() throws SQLException {
        List<DictionaryItem> categoryItems = new ArrayList<>();
        categoryItems.addAll(dictionaryRepository.findItemsByType(
                SQLiteDictionaryRepository.TYPE_DONATION_LIGHT
        ));
        categoryItems.addAll(dictionaryRepository.findItemsByType(
                SQLiteDictionaryRepository.TYPE_DONATION_GHOST
        ));
        return categoryItems.stream().collect(Collectors.toMap(
                item -> String.valueOf(item.getId()),
                DictionaryItem::getName,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private enum ReportKind {
        DETAIL("結帳報表－明細表"),
        CLASSIFICATION("分類表"),
        DAILY_CATEGORY("統計表"),
        MONTHLY_DAILY("月報表"),
        SUPPLEMENT_DETAIL("補登款項");

        private final String title;

        ReportKind(String title) {
            this.title = title;
        }
    }

    private enum SupplementReportKind {
        DETAIL("補登款項明細表"),
        ALL("結帳報表－明細表（全）");

        private final String title;

        SupplementReportKind(String title) {
            this.title = title;
        }
    }

    private enum AuditReportKind {
        DELETE("刪除款項明細表"),
        UPDATE("修改款項明細表"),
        RECEIPT_SUPPLEMENT("補據款項明細表");

        private final String title;

        AuditReportKind(String title) {
            this.title = title;
        }
    }

    private record ReportData(
            List<Donation> donations,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames
    ) {
    }
}
