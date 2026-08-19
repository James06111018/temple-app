package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationAuditRecord;
import tw.org.il.dongsheng.templeapp.model.DonationSupplement;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationSupplementRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.LightReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static tw.org.il.dongsheng.templeapp.util.Util.*;

public class IncomeReportController {

    @FXML private TextField startDateField;
    @FXML private TextField endDateField;

    private SQLiteDonationRepository donationRepository;
    private SQLiteDictionaryRepository dictionaryRepository;
    private SQLiteLightMemberRepository memberRepository;
    private SQLiteDonationSupplementRepository supplementRepository;

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
    }

    @FXML
    private void onOpenDetailReport() {
        openReport(ReportKind.DETAIL);
    }

    @FXML
    private void onOpenSubtotalReport() {
        openReport(ReportKind.SUBTOTAL);
    }

    @FXML
    private void onOpenAllReport() {
        openReport(ReportKind.ALL);
    }

    @FXML
    private void onOpenDailyReport() {
        openReport(ReportKind.DAILY_OPERATOR);
    }

    @FXML
    private void onOpenDailyCategoryReport() {
        openReport(ReportKind.DAILY_CATEGORY);
    }

    @FXML
    private void onOpenMonthlyReport() {
        openReport(ReportKind.MONTHLY_DAILY);
    }

    @FXML
    private void onOpenMonthlyTotalReport() {
        openReport(ReportKind.MONTHLY_TOTAL);
    }

    @FXML
    private void onOpenSupplementDetailReport() {
        openSupplementReport(SupplementReportKind.DETAIL);
    }

    @FXML
    private void onOpenSupplementSummaryReport() {
        openSupplementReport(SupplementReportKind.SUMMARY);
    }

    @FXML
    private void onOpenSupplementAllReport() {
        openSupplementReport(SupplementReportKind.ALL);
    }

    @FXML
    private void onOpenDeleteAuditReport() {
        openAuditReport(AuditReportKind.DELETE);
    }

    @FXML
    private void onOpenUpdateAuditReport() {
        openAuditReport(AuditReportKind.UPDATE);
    }

    @FXML
    private void onOpenReceiptSupplementAuditReport() {
        openAuditReport(AuditReportKind.RECEIPT_SUPPLEMENT);
    }

    private void openReport(ReportKind kind) {
        LocalDate startDate = parseDate(startDateField.getText());
        LocalDate endDate = parseDate(endDateField.getText());
        if (startDate == null || endDate == null) {
            AlertDialog.showWarning("收入報表", "日期請輸入民國 115.05.14 或西元 2026-05-14 格式");
            return;
        }
        if (startDate.isAfter(endDate)) {
            AlertDialog.showWarning("收入報表", "起始日期不可晚於結束日期");
            return;
        }

        try {
            ReportData data = loadReportData(startDate, endDate);
            if (data.donations().isEmpty()) {
                AlertDialog.showInfo("收入報表", "查無指定日期內的點燈或中元普渡收入資料");
                return;
            }
            List<? extends javafx.scene.layout.Region> pages = switch (kind) {
                case DETAIL -> LightReportBuilder.buildIncomeDetailPages(
                        data.donations(), data.membersById(), data.categoryNames()
                );
                case SUBTOTAL -> LightReportBuilder.buildIncomeSubtotalPages(
                        data.donations(), data.membersById(), data.categoryNames()
                );
                case ALL -> LightReportBuilder.buildIncomeAllPages(
                        data.donations(), data.membersById()
                );
                case DAILY_OPERATOR -> LightReportBuilder.buildIncomeDailyOperatorPages(
                        data.donations()
                );
                case DAILY_CATEGORY -> LightReportBuilder.buildIncomeDailyCategoryPages(
                        data.donations(), data.categoryNames()
                );
                case MONTHLY_DAILY -> LightReportBuilder.buildIncomeMonthlyDailyPages(
                        data.donations()
                );
                case MONTHLY_TOTAL -> LightReportBuilder.buildIncomeMonthlyTotalPages(
                        data.donations()
                );
            };
            PrintPreview.show(startDateField.getScene().getWindow(), kind.title, pages);
        } catch (SQLException e) {
            AlertDialog.showError("收入報表", "讀取收入資料失敗：" + e.getMessage());
        }
    }

    private ReportData loadReportData(LocalDate startDate, LocalDate endDate) throws SQLException {
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

        List<Donation> donations = donationRepository.findAll(startDate, endDate, null, null).stream()
                .filter(donation -> incomeCategoryIds.contains(donation.getDonateType()))
//                .filter(donation -> isWithinRange(donation, startDate, endDate))
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
        if (startDate == null || endDate == null) {
            AlertDialog.showWarning("補登款項", "日期請輸入民國 115.05.14 或西元 2026-05-14 格式");
            return;
        }
        if (startDate.isAfter(endDate)) {
            AlertDialog.showWarning("補登款項", "起始日期不可晚於結束日期");
            return;
        }

        try {
            List<DonationSupplement> supplements = supplementRepository.findByDateRange(startDate, endDate, null);
            List<Integer> donationIds = supplements.stream()
                    .map(DonationSupplement::getDonationId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Integer, Donation> donationsById = donationRepository.findByIds(donationIds, null).stream()
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
                case DETAIL -> LightReportBuilder.buildSupplementDetailPages(
                        supplements, donationsById, membersById, categoryNames
                );
                case SUMMARY -> LightReportBuilder.buildSupplementSummaryPages(
                        supplements, donationsById, categoryNames
                );
                case ALL -> LightReportBuilder.buildSupplementAllPages(
                        supplements, donationsById, membersById, categoryNames
                );
            };
            PrintPreview.show(startDateField.getScene().getWindow(), kind.title, pages);
        } catch (SQLException e) {
            AlertDialog.showError("補登款項", "讀取補登資料失敗：" + e.getMessage());
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

    private void openAuditReport(AuditReportKind kind) {
        LocalDate startDate = parseDate(startDateField.getText());
        LocalDate endDate = parseDate(endDateField.getText());
        if (startDate == null || endDate == null) {
            AlertDialog.showWarning(kind.title, "日期請輸入民國 115.05.14 或西元 2026-05-14 格式");
            return;
        }
        if (startDate.isAfter(endDate)) {
            AlertDialog.showWarning(kind.title, "起始日期不可晚於結束日期");
            return;
        }

        try {
            List<DonationAuditRecord> records = switch (kind) {
                case DELETE -> donationRepository.findAuditRecordsByDateRange(
                        SQLiteDonationRepository.AUDIT_ACTION_DELETE, startDate, endDate, null, null
                );
                case UPDATE -> donationRepository.findAuditRecordsByDateRange(
                        SQLiteDonationRepository.AUDIT_ACTION_UPDATE, startDate, endDate, null, null
                );
                case RECEIPT_SUPPLEMENT -> donationRepository.findAuditRecordsByDateRange(
                        SQLiteDonationRepository.AUDIT_ACTION_RECEIPT_SUPPLEMENT, startDate, endDate, null, null
                );
            };
            Map<String, String> categoryNames = loadAllDonationCategoryNames();
            List<? extends javafx.scene.layout.Region> pages = switch (kind) {
                case DELETE -> LightReportBuilder.buildDonationDeleteAuditPages(
                        records, categoryNames
                );
                case UPDATE -> LightReportBuilder.buildDonationUpdateAuditPages(
                        records, categoryNames
                );
                case RECEIPT_SUPPLEMENT -> LightReportBuilder.buildDonationReceiptSupplementAuditPages(
                        records, categoryNames
                );
            };
            PrintPreview.show(startDateField.getScene().getWindow(), kind.title, pages);
        } catch (SQLException e) {
            AlertDialog.showError(kind.title, "讀取捐款記錄失敗：" + e.getMessage());
        }
    }

    private boolean isWithinRange(Donation donation, LocalDate startDate, LocalDate endDate) {
        LocalDate donationDate = parseDate(donation.getDonateDate());
        return donationDate != null
                && !donationDate.isBefore(startDate)
                && !donationDate.isAfter(endDate);
    }

    private enum ReportKind {
        DETAIL("收入報表－明細表"),
        SUBTOTAL("收入報表－明細表（分類小計）"),
        ALL("收入報表－明細表（全）"),
        DAILY_OPERATOR("收入日報表"),
        DAILY_CATEGORY("收入日統計表"),
        MONTHLY_DAILY("收入月報表"),
        MONTHLY_TOTAL("收入月統計表");

        private final String title;

        ReportKind(String title) {
            this.title = title;
        }
    }

    private enum SupplementReportKind {
        DETAIL("補登款項明細表"),
        SUMMARY("補登統計表"),
        ALL("補登款項明細表（全）");

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
