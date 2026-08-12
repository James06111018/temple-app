package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationAuditRecord;
import tw.org.il.dongsheng.templeapp.model.DonationSupplement;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public final class LightReportBuilder {
    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final double CONTENT_WIDTH = 714;
    private static final int ROSTER_ROWS_PER_PAGE = 30;
    private static final int DETAIL_ROWS_PER_PAGE = 27;
    private static final int TOTAL_DETAIL_ROWS_PER_PAGE = 24;
    private static final int CATEGORY_ROWS_PER_PAGE = 23;
    private static final int STATISTICS_ROWS_PER_PAGE = 25;
    private static final int INCOME_ROWS_PER_PAGE = 23;
    private static final int INCOME_SUMMARY_ROWS_PER_PAGE = 28;
    private static final int SUPPLEMENT_ROWS_PER_PAGE = 22;
    private static final int AUDIT_ROWS_PER_PAGE = 25;
    private static final String TEMPLE_NAME = "五結東聖宮";
    private static final String INCOME_REPORT_FONT_FAMILY = resolveIncomeReportFontFamily();
    private static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("yyyy.M.d");

    private LightReportBuilder() {
    }

    public static List<Region> buildRosterPages(List<LightMember> members, String phone, String address) {
        List<LightMember> rows = members == null
                ? List.of()
                : members.stream()
                        .sorted(Comparator.comparing(
                                LightMember::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                        .toList();
        int pageCount = Math.max(1, (rows.size() + ROSTER_ROWS_PER_PAGE - 1) / ROSTER_ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * ROSTER_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + ROSTER_ROWS_PER_PAGE);
            VBox page = createPage(TEMPLE_NAME + "　香客全戶明細表");
            page.getChildren().add(contactBlock(phone, address, false));
            page.getChildren().add(rosterHeader());

            for (int index = from; index < to; index++) {
                page.getChildren().add(rosterRow(index + 1, rows.get(index)));
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildDonationDetailPages(
            List<Donation> donations,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames,
            String phone,
            String address
    ) {
        List<Donation> rows = donations == null ? List.of() : donations;
        int pageCount = Math.max(1, (rows.size() + DETAIL_ROWS_PER_PAGE - 1) / DETAIL_ROWS_PER_PAGE);
        int totalAmount = rows.stream().mapToInt(donation -> valueOrZero(donation.getAmount())).sum();
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * DETAIL_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + DETAIL_ROWS_PER_PAGE);
            VBox page = createPage(TEMPLE_NAME + "　捐款明細表");
            page.getChildren().add(contactBlock(phone, address, true));
            page.getChildren().add(detailHeader());

            for (int index = from; index < to; index++) {
                Donation donation = rows.get(index);
                page.getChildren().add(detailRow(
                        donation,
                        membersById.get(donation.getMemberId()),
                        categoryNames.get(donation.getDonateType())
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(totalRow(totalAmount));
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildTotalAmountDetailPages(
            LightMember member,
            List<Donation> donations,
            Map<String, String> categoryNames
    ) {
        List<Donation> rows = sortDonations(donations);
        int pageCount = Math.max(1, pageCount(rows.size(), TOTAL_DETAIL_ROWS_PER_PAGE));
        int totalAmount = rows.stream().mapToInt(LightReportBuilder::reportAmount).sum();
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * TOTAL_DETAIL_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + TOTAL_DETAIL_ROWS_PER_PAGE);
            VBox page = createBlankPage();
            page.getChildren().add(totalMemberHeader(member));
            page.getChildren().add(row(
                    new String[]{"日期", "收據編號", "款項類別", "摘要", "金額"},
                    new double[]{92, 100, 160, 272, 90},
                    true,
                    true
            ));

            for (int index = from; index < to; index++) {
                Donation donation = rows.get(index);
                page.getChildren().add(row(
                        new String[]{
                                toRocDate(donation.getDonateDate()),
                                formatReceiptNo(donation.getReceiptNo()),
                                categoryLabel(categoryNames, donation.getDonateType()),
                                donationSummary(donation),
                                formatAmount(reportAmount(donation))
                        },
                        new double[]{92, 100, 160, 272, 90},
                        false,
                        true
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(totalRow(totalAmount));
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildTotalAmountCategoryPages(
            LightMember member,
            List<Donation> donations,
            Map<String, String> categoryNames
    ) {
        List<ClassifiedDonationRow> rows = classifiedRows(donations, categoryNames);
        int pageCount = Math.max(1, pageCount(rows.size(), CATEGORY_ROWS_PER_PAGE));
        int grandTotal = rows.stream()
                .filter(row -> !row.subtotal())
                .mapToInt(row -> reportAmount(row.donation()))
                .sum();
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * CATEGORY_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + CATEGORY_ROWS_PER_PAGE);
            VBox page = createPage(TEMPLE_NAME + "　收入明細表");
            page.getChildren().add(row(
                    new String[]{"款項類別", "金額", "日期", "收據編號", "姓名", "經辦人", "摘要"},
                    new double[]{105, 80, 85, 85, 90, 90, 179},
                    true,
                    true
            ));

            for (int index = from; index < to; index++) {
                ClassifiedDonationRow classifiedRow = rows.get(index);
                if (classifiedRow.subtotal()) {
                    page.getChildren().add(categorySubtotalRow(classifiedRow.categoryName(), classifiedRow.amount()));
                    continue;
                }
                Donation donation = classifiedRow.donation();
                page.getChildren().add(row(
                        new String[]{
                                classifiedRow.categoryName(),
                                formatAmount(reportAmount(donation)),
                                toRocDate(donation.getDonateDate()),
                                formatReceiptNo(donation.getReceiptNo()),
                                member == null ? "" : safe(member.getName()),
                                safe(donation.getCreator()),
                                donationSummary(donation)
                        },
                        new double[]{105, 80, 85, 85, 90, 90, 179},
                        false,
                        true
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(categoryGrandTotalRow(grandTotal));
            }
            page.getChildren().add(pageNumber(pageIndex + 1, pageCount));
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildTotalAmountStatisticsPages(
            LightMember member,
            List<Donation> donations,
            Map<String, String> categoryNames
    ) {
        List<CategorySummary> summaries = categorySummaries(donations, categoryNames);
        int pageCount = Math.max(1, pageCount(summaries.size(), STATISTICS_ROWS_PER_PAGE));
        int totalAmount = summaries.stream().mapToInt(CategorySummary::amount).sum();
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * STATISTICS_ROWS_PER_PAGE;
            int to = Math.min(summaries.size(), from + STATISTICS_ROWS_PER_PAGE);
            VBox page = createPage(TEMPLE_NAME + "　捐款統計表");

            Label memberLabel = textLabel(
                    "姓名：" + (member == null ? "" : safe(member.getName())),
                    15,
                    false
            );
            VBox.setMargin(memberLabel, new Insets(0, 0, 8, 0));
            page.getChildren().add(memberLabel);
            page.getChildren().add(row(
                    new String[]{"款項類別", "筆數", "金額"},
                    new double[]{390, 120, 204},
                    true,
                    false
            ));

            for (int index = from; index < to; index++) {
                CategorySummary summary = summaries.get(index);
                page.getChildren().add(row(
                        new String[]{
                                summary.categoryName(),
                                String.valueOf(summary.count()),
                                formatAmount(summary.amount())
                        },
                        new double[]{390, 120, 204},
                        false,
                        false
                ));
            }
            if (pageIndex == pageCount - 1) {
                Label totalLabel = textLabel("總金額：" + formatAmount(totalAmount), 18, true);
                VBox.setMargin(totalLabel, new Insets(24, 0, 0, 0));
                page.getChildren().add(totalLabel);
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildIncomeDetailPages(
            List<Donation> donations,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames
    ) {
        List<Donation> rows = sortDonations(donations);
        int pageCount = Math.max(1, pageCount(rows.size(), INCOME_ROWS_PER_PAGE));
        int totalAmount = rows.stream().mapToInt(LightReportBuilder::incomeAmount).sum();
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * INCOME_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + INCOME_ROWS_PER_PAGE);
            VBox page = createIncomePage(TEMPLE_NAME + "　收入明細表");
            page.getChildren().add(incomeRow(
                    new String[]{"收據編號", "金    額", "日    期", "款項類別", "姓    名", "經辦人", "摘    要"},
                    new double[]{90, 90, 90, 135, 100, 90, 119},
                    true,
                    1
            ));
            for (int index = from; index < to; index++) {
                Donation donation = rows.get(index);
                LightMember member = membersById.get(donation.getMemberId());
                page.getChildren().add(incomeRow(
                        new String[]{
                                formatReceiptNo(donation.getReceiptNo()),
                                formatAmount(incomeAmount(donation)),
                                toRocDate(donation.getDonateDate()),
                                categoryLabel(categoryNames, donation.getDonateType()),
                                member == null ? "" : safe(member.getName()),
                                safe(donation.getCreator()),
                                donationSummary(donation)
                        },
                        new double[]{90, 90, 90, 135, 100, 90, 119},
                        false,
                        1
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(incomeTotalRow("總　  計", totalAmount, 90));
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildIncomeSubtotalPages(
            List<Donation> donations,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames
    ) {
        List<IncomeReportLine> lines = new ArrayList<>();
        for (CategoryGroup group : categoryGroups(donations, categoryNames)) {
            for (Donation donation : group.donations()) {
                lines.add(new IncomeReportLine(group.categoryName(), donation, false, 0));
            }
            lines.add(new IncomeReportLine(
                    group.categoryName(),
                    null,
                    true,
                    group.donations().stream().mapToInt(LightReportBuilder::incomeAmount).sum()
            ));
        }
        int pageCount = Math.max(1, pageCount(lines.size(), INCOME_ROWS_PER_PAGE));
        int totalAmount = donations == null
                ? 0
                : donations.stream().mapToInt(LightReportBuilder::incomeAmount).sum();
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * INCOME_ROWS_PER_PAGE;
            int to = Math.min(lines.size(), from + INCOME_ROWS_PER_PAGE);
            VBox page = createIncomePage(TEMPLE_NAME + "　收入明細表");
            page.getChildren().add(incomeRow(
                    new String[]{"款項類別", "金    額", "日    期", "收據編號", "姓    名", "經辦人", "摘    要"},
                    new double[]{135, 90, 90, 90, 100, 90, 119},
                    true,
                    1
            ));
            for (int index = from; index < to; index++) {
                IncomeReportLine line = lines.get(index);
                if (line.subtotal()) {
                    page.getChildren().add(incomeSubtotalRow(line.amount()));
                    continue;
                }
                Donation donation = line.donation();
                LightMember member = membersById.get(donation.getMemberId());
                page.getChildren().add(incomeRow(
                        new String[]{
                                line.categoryName(),
                                formatAmount(incomeAmount(donation)),
                                toRocDate(donation.getDonateDate()),
                                formatReceiptNo(donation.getReceiptNo()),
                                member == null ? "" : safe(member.getName()),
                                safe(donation.getCreator()),
                                donationSummary(donation)
                        },
                        new double[]{135, 90, 90, 90, 100, 90, 119},
                        false,
                        1
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(incomeTotalRow("總    計", totalAmount, 135));
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildIncomeAllPages(
            List<Donation> donations,
            Map<Integer, LightMember> membersById
    ) {
        List<Donation> rows = sortDonations(donations);
        int pageCount = Math.max(1, pageCount(rows.size(), INCOME_ROWS_PER_PAGE));
        int totalAmount = rows.stream().mapToInt(LightReportBuilder::incomeAmount).sum();
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * INCOME_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + INCOME_ROWS_PER_PAGE);
            VBox page = createIncomePage(TEMPLE_NAME + "　收入明細表");
            page.getChildren().add(incomeRow(
                    new String[]{"收據編號", "金    額", "日    期", "經辦人", "收據代表人", "備    註"},
                    new double[]{90, 100, 90, 90, 170, 174},
                    true,
                    1
            ));
            for (int index = from; index < to; index++) {
                Donation donation = rows.get(index);
                LightMember member = membersById.get(donation.getMemberId());
                page.getChildren().add(incomeRow(
                        new String[]{
                                formatReceiptNo(donation.getReceiptNo()),
                                formatAmount(incomeAmount(donation)),
                                toRocDate(donation.getDonateDate()),
                                safe(donation.getCreator()),
                                member == null ? "" : safe(member.getName()),
                                donationSummary(donation)
                        },
                        new double[]{90, 100, 90, 90, 170, 174},
                        false,
                        1
                ));
            }
            if (pageIndex == pageCount - 1) {
                Label totalLabel = incomeTextLabel("現金收入：" + formatAmount(totalAmount) + "元", 18);
                VBox.setMargin(totalLabel, new Insets(24, 0, 0, 4));
                page.getChildren().add(totalLabel);
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildSupplementDetailPages(
            List<DonationSupplement> supplements,
            Map<Integer, Donation> donationsById,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames
    ) {
        return buildSupplementDetailPages(
                supplements, donationsById, membersById, categoryNames, true
        );
    }

    public static List<Region> buildSupplementAllPages(
            List<DonationSupplement> supplements,
            Map<Integer, Donation> donationsById,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames
    ) {
        return buildSupplementDetailPages(
                supplements, donationsById, membersById, categoryNames, false
        );
    }

    public static List<Region> buildSupplementSummaryPages(
            List<DonationSupplement> supplements,
            Map<Integer, Donation> donationsById,
            Map<String, String> categoryNames
    ) {
        List<SupplementReportLine> lines = supplementLines(
                supplements, donationsById, Map.of(), categoryNames
        );
        Map<String, int[]> grouped = new LinkedHashMap<>();
        for (SupplementReportLine line : lines) {
            int[] summary = grouped.computeIfAbsent(line.categoryName(), ignored -> new int[2]);
            summary[0]++;
            summary[1] += incomeAmount(line.donation());
        }
        List<CategorySummary> summaries = grouped.entrySet().stream()
                .map(entry -> new CategorySummary(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]
                ))
                .toList();
        int pageCount = Math.max(1, pageCount(summaries.size(), INCOME_SUMMARY_ROWS_PER_PAGE));
        int totalAmount = summaries.stream().mapToInt(CategorySummary::amount).sum();
        String dateRange = supplementDateRange(lines);
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * INCOME_SUMMARY_ROWS_PER_PAGE;
            int to = Math.min(summaries.size(), from + INCOME_SUMMARY_ROWS_PER_PAGE);
            VBox page = createIncomePage("補登統計表");
            page.getChildren().add(incomeSummaryRow(
                    new String[]{dateRange, "款項類別", "筆數", "金額"},
                    new double[]{180, 220, 100, 144},
                    true
            ));
            for (int index = from; index < to; index++) {
                CategorySummary summary = summaries.get(index);
                page.getChildren().add(incomeSummaryRow(
                        new String[]{
                                "",
                                summary.categoryName(),
                                String.valueOf(summary.count()),
                                formatAmount(summary.amount())
                        },
                        new double[]{250, 175, 30, 144},
                        false,
                        2, 3
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(incomeSummaryTotalRow(totalAmount));
            }
            pages.add(page);
        }
        return pages;
    }

    private static List<Region> buildSupplementDetailPages(
            List<DonationSupplement> supplements,
            Map<Integer, Donation> donationsById,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames,
            boolean showTotal
    ) {
        List<SupplementReportLine> lines = supplementLines(
                supplements, donationsById, membersById, categoryNames
        );
        int pageCount = Math.max(1, pageCount(lines.size(), SUPPLEMENT_ROWS_PER_PAGE));
        int totalAmount = lines.stream()
                .map(SupplementReportLine::donation)
                .mapToInt(LightReportBuilder::incomeAmount)
                .sum();
        double[] widths = {95, 90, 90, 130, 100, 90, 119};
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * SUPPLEMENT_ROWS_PER_PAGE;
            int to = Math.min(lines.size(), from + SUPPLEMENT_ROWS_PER_PAGE);
            VBox page = createIncomePage("補登款項明細表");
            page.getChildren().add(incomeRow(
                    new String[]{"補登號碼", "金    額", "日    期", "款項類別", "姓    名", "經辦人", "備    註"},
                    widths,
                    true,
                    1
            ));
            for (int index = from; index < to; index++) {
                SupplementReportLine line = lines.get(index);
                page.getChildren().add(incomeRow(
                        new String[]{
                                safe(line.supplement().getSupplementNo()),
                                formatAmount(incomeAmount(line.donation())),
                                toRocDate(line.supplement().getSupplementDate()),
                                line.categoryName(),
                                line.memberName(),
                                safe(line.donation().getCreator()),
                                line.note()
                        },
                        widths,
                        false,
                        1
                ));
            }
            if (showTotal && pageIndex == pageCount - 1) {
                page.getChildren().add(incomeTotalRow("總    計", totalAmount, widths[0]));
            }
            addIncomePageNumber(page, pageIndex + 1, pageCount);
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildDonationDeleteAuditPages(
            List<DonationAuditRecord> records,
            Map<String, String> categoryNames
    ) {
        List<String[]> rows = new ArrayList<>();
        if (records != null) {
            for (DonationAuditRecord record : records) {
                Donation donation = firstDonation(record.beforeDonation(), record.afterDonation());
                rows.add(new String[]{
                        donation == null ? "" : formatReceiptNo(donation.getReceiptNo()),
                        donation == null ? "" : toRocDate(donation.getDonateDate()),
                        safe(record.memberName()),
                        auditCategory(categoryNames, donation),
                        donation == null ? "" : formatAmount(incomeAmount(donation)),
                        safe(record.changedBy()),
                        toRocDateTime(record.changedAt()),
                        safe(record.reason())
                });
            }
        }
        return buildAuditPages(
                "刪除款項明細表",
                new String[]{"收據編號", "捐款日期", "姓    名", "款項類別", "金額", "刪除人", "刪除日期時間", "刪除原因"},
                new double[]{70, 82, 90, 105, 62, 70, 135, 100},
                rows,
                4
        );
    }

    public static List<Region> buildDonationUpdateAuditPages(
            List<DonationAuditRecord> records,
            Map<String, String> categoryNames
    ) {
        List<String[]> rows = new ArrayList<>();
        if (records != null) {
            for (DonationAuditRecord record : records) {
                Donation before = record.beforeDonation();
                Donation after = record.afterDonation();
                Donation identity = firstDonation(after, before);
                rows.add(new String[]{
                        identity == null ? "" : formatReceiptNo(identity.getReceiptNo()),
                        safe(record.memberName()),
                        auditCategory(categoryNames, firstDonation(before, after)),
                        before == null ? "" : toRocDate(before.getDonateDate()),
                        before == null ? "" : formatAmount(incomeAmount(before)),
                        before == null ? "" : safe(before.getCreator()),
                        after == null ? "" : toRocDate(after.getDonateDate()),
                        after == null ? "" : formatAmount(incomeAmount(after)),
                        after == null ? "" : safe(after.getCreator()),
                        toRocDateTime(record.changedAt())
                });
            }
        }
        return buildAuditPages(
                "修改款項明細表",
                new String[]{
                        "收據編號", "姓名", "款項類別", "原捐款日期", "原金額",
                        "原經辦人", "新捐款日期", "新金額", "修改人", "修改日期時間"
                },
                new double[]{62, 72, 86, 76, 58, 70, 76, 58, 70, 86},
                rows,
                4, 7
        );
    }

    public static List<Region> buildDonationReceiptSupplementAuditPages(
            List<DonationAuditRecord> records,
            Map<String, String> categoryNames
    ) {
        List<String[]> rows = new ArrayList<>();
        if (records != null) {
            for (DonationAuditRecord record : records) {
                Donation before = record.beforeDonation();
                Donation after = record.afterDonation();
                Donation donation = firstDonation(after, before);
                rows.add(new String[]{
                        donation == null ? "" : formatReceiptNo(donation.getReceiptNo()),
                        safe(record.memberName()),
                        auditCategory(categoryNames, donation),
                        before == null ? "" : toRocDate(before.getDonateDate()),
                        donation == null ? "" : formatAmount(incomeAmount(donation)),
                        before == null ? "" : safe(before.getCreator()),
                        safe(record.changedBy()),
                        toRocDateTime(record.changedAt()),
                        safe(record.reason())
                });
            }
        }
        return buildAuditPages(
                "補據款項明細表",
                new String[]{
                        "收據編號", "姓名", "款項類別", "捐款日期", "金額",
                        "經辦人", "補據人", "補據日期時間", "補據原因"
                },
                new double[]{65, 80, 95, 82, 58, 68, 68, 120, 78},
                rows,
                4
        );
    }

    public static List<Region> buildIncomeDailyOperatorPages(List<Donation> donations) {
        List<IncomeSummaryLine> summaries = summarizeDailyIncome(
                donations,
                donation -> firstNonBlank(donation.getCreator(), "未指定")
        );
        return buildIncomeDailySummaryPages("收入日報表", "經辦人", summaries);
    }

    public static List<Region> buildIncomeDailyCategoryPages(
            List<Donation> donations,
            Map<String, String> categoryNames
    ) {
        List<IncomeSummaryLine> summaries = summarizeDailyIncome(
                donations,
                donation -> categoryLabel(categoryNames, donation.getDonateType())
        );
        return buildIncomeDailySummaryPages("收入日統計表", "款項類別", summaries);
    }

    public static List<Region> buildIncomeMonthlyDailyPages(List<Donation> donations) {
        Map<YearMonth, Map<LocalDate, Integer>> monthlyDailyAmounts = new TreeMap<>();
        for (Donation donation : sortDonations(donations)) {
            LocalDate date = parseReportDate(donation.getDonateDate());
            if (date == null) {
                continue;
            }
            monthlyDailyAmounts
                    .computeIfAbsent(YearMonth.from(date), ignored -> new TreeMap<>())
                    .merge(date, incomeAmount(donation), Integer::sum);
        }

        int totalPageCount = monthlyDailyAmounts.values().stream()
                .mapToInt(rows -> Math.max(1, pageCount(rows.size(), INCOME_SUMMARY_ROWS_PER_PAGE)))
                .sum();
        int totalAmount = donations == null
                ? 0
                : donations.stream().mapToInt(LightReportBuilder::incomeAmount).sum();
        int reportPageNumber = 0;
        List<Region> pages = new ArrayList<>();
        for (Map.Entry<YearMonth, Map<LocalDate, Integer>> monthEntry : monthlyDailyAmounts.entrySet()) {
            List<Map.Entry<LocalDate, Integer>> rows = new ArrayList<>(monthEntry.getValue().entrySet());
            int pageCount = Math.max(1, pageCount(rows.size(), INCOME_SUMMARY_ROWS_PER_PAGE));
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                reportPageNumber++;
                int from = pageIndex * INCOME_SUMMARY_ROWS_PER_PAGE;
                int to = Math.min(rows.size(), from + INCOME_SUMMARY_ROWS_PER_PAGE);
                VBox page = createIncomePage(TEMPLE_NAME + "　收入月報表");
                page.getChildren().add(incomeSummaryRow(
                        new String[]{"資料月份：" + toRocMonth(monthEntry.getKey()), "日  期", "金額"},
                        new double[]{200, 132, 132},
                        true,
                        2
                ));
                for (int index = from; index < to; index++) {
                    Map.Entry<LocalDate, Integer> row = rows.get(index);
                    page.getChildren().add(incomeSummaryRow(
                            new String[]{"", toRocDate(row.getKey()), formatAmount(row.getValue())},
                            new double[]{240, 75, 102},
                            false,
                            2
                    ));
                }
                if (reportPageNumber == totalPageCount) {
                    page.getChildren().add(incomeSummaryTotalRow(totalAmount));
                }
                addIncomePageFooter(page, reportPageNumber, totalPageCount);
                pages.add(page);
            }
        }
        return pages;
    }

    public static List<Region> buildIncomeMonthlyTotalPages(List<Donation> donations) {
        Map<YearMonth, Integer> monthlyAmounts = new TreeMap<>();
        for (Donation donation : sortDonations(donations)) {
            LocalDate date = parseReportDate(donation.getDonateDate());
            if (date != null) {
                monthlyAmounts.merge(YearMonth.from(date), incomeAmount(donation), Integer::sum);
            }
        }

        List<Map.Entry<YearMonth, Integer>> rows = new ArrayList<>(monthlyAmounts.entrySet());
        int pageCount = Math.max(1, pageCount(rows.size(), INCOME_SUMMARY_ROWS_PER_PAGE));
        int totalAmount = rows.stream().mapToInt(Map.Entry::getValue).sum();
        List<Region> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * INCOME_SUMMARY_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + INCOME_SUMMARY_ROWS_PER_PAGE);
            VBox page = createIncomePage(TEMPLE_NAME + "　收入月統計表");
            if (!rows.isEmpty()) {
                page.getChildren().add(incomeSummaryRow(
                        new String[]{
                                "資料月份：" + toRocMonth(rows.get(0).getKey())
                                        + " － " + toRocMonth(rows.get(rows.size() - 1).getKey()),
                                "月  份",
                                "金額"
                        },
                        new double[]{220, 135, 132},
                        true,
                        2
                ));
            } else {
                page.getChildren().add(incomeSummaryRow(
                        new String[]{"資料月份：", "月  份", "金額"},
                        new double[]{220, 135, 132},
                        true,
                        2
                ));
            }
            for (int index = from; index < to; index++) {
                Map.Entry<YearMonth, Integer> row = rows.get(index);
                page.getChildren().add(incomeSummaryRow(
                        new String[]{"", toRocMonth(row.getKey()), formatAmount(row.getValue())},
                        new double[]{255, 55, 132},
                        false,
                        2
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(incomeSummaryTotalRow(totalAmount));
            }
            addIncomePageFooter(page, pageIndex + 1, pageCount);
            pages.add(page);
        }
        return pages;
    }

    private static VBox createPage(String title) {
        VBox page = createBlankPage();

        Label titleLabel = new Label(title);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
        VBox.setMargin(titleLabel, new Insets(0, 0, 14, 0));
        page.getChildren().add(titleLabel);

        return page;
    }

    private static VBox createIncomePage(String title) {
        VBox page = createBlankPage();
        Label titleLabel = incomeTextLabel(title, 24);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);
        VBox.setMargin(titleLabel, new Insets(0, 0, 14, 0));
        page.getChildren().add(titleLabel);
        return page;
    }

    private static VBox createBlankPage() {
        VBox page = new VBox(0);
        page.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMinSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMaxSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setPadding(new Insets(34, 40, 28, 40));
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");
        return page;
    }

    private static VBox contactBlock(String phone, String address, boolean addressFirst) {
        VBox block = new VBox(4);
        Label phoneLabel = textLabel("電話：" + safe(phone), 15, false);
        Label addressLabel = textLabel("地址：" + safe(address), 15, false);
        if (addressFirst) {
            block.getChildren().addAll(addressLabel, phoneLabel);
        } else {
            block.getChildren().addAll(phoneLabel, addressLabel);
        }
        VBox.setMargin(block, new Insets(0, 0, 12, 0));
        return block;
    }

    private static HBox rosterHeader() {
        return row(
                new String[]{"", "電腦編號", "姓名", "性別", "年齡", "農曆生日", "時辰", "生肖", "制化"},
                new double[]{34, 90, 115, 50, 50, 105, 60, 60, 150},
                true,
                false
        );
    }

    private static HBox rosterRow(int sequence, LightMember member) {
        return row(
                new String[]{
                        String.valueOf(sequence),
                        member.getId() == null ? "" : Util.stringFormat(member.getId()),
                        safe(member.getName()),
                        safe(member.getGender()),
                        member.getAge() == null ? "" : String.valueOf(member.getAge()),
                        safe(member.getLunarBirthDate()),
                        safe(member.getBirthTime()),
                        safe(member.getZodiac()),
                        ""
                },
                new double[]{34, 90, 115, 50, 50, 105, 60, 60, 150},
                false,
                false
        );
    }

    private static HBox detailHeader() {
        return row(
                new String[]{"日期", "收據編號", "姓名", "款項類別", "摘要", "金額"},
                new double[]{92, 90, 100, 112, 230, 90},
                true,
                true
        );
    }

    private static HBox detailRow(Donation donation, LightMember member, String categoryName) {
        String summary = safe(donation.getSummary());
        if (summary.isBlank()) {
            summary = firstNonBlank(donation.getDonorNo(), donation.getLightNo(), donation.getDonateNote());
        }
        return row(
                new String[]{
                        toRocDate(donation.getDonateDate()),
                        formatReceiptNo(donation.getReceiptNo()),
                        member == null ? "" : safe(member.getName()),
                        safe(categoryName),
                        summary,
                        formatAmount(donation.getAmount())
                },
                new double[]{92, 90, 100, 112, 230, 90},
                false,
                true
        );
    }

    private static HBox totalRow(int totalAmount) {
        Label totalLabel = tableCell("合　  計", 624, Pos.CENTER_RIGHT, true, true);
        Label amountLabel = tableCell(formatAmount(totalAmount), 90, Pos.CENTER_RIGHT, true, true);
        HBox row = new HBox(totalLabel, amountLabel);
        row.setStyle("-fx-border-color: #303030; -fx-border-width: 0 0 0 1;");
        return row;
    }

    private static VBox totalMemberHeader(LightMember member) {
        VBox block = new VBox(4);
        String name = member == null ? "" : safe(member.getName());
        String id = member == null || member.getId() == null ? "" : Util.stringFormat(member.getId());
        Label nameAndId = textLabel("姓名：" + name + "　　　　　　　　　電腦編號：" + id, 15, false);
        Label address = textLabel("地址：" + (member == null ? "" : safe(member.getAddress())), 15, false);
        Label phone = textLabel("電話：" + (member == null ? "" : safe(member.getPhone())), 15, false);
        block.getChildren().addAll(nameAndId, address, phone);
        VBox.setMargin(block, new Insets(0, 0, 12, 0));
        return block;
    }

    private static HBox categorySubtotalRow(String categoryName, int amount) {
        Label label = tableCell(categoryName + "　小  計", 624, Pos.CENTER, true, true);
        Label amountLabel = tableCell(formatAmount(amount), 90, Pos.CENTER_RIGHT, true, true);
        return new HBox(label, amountLabel);
    }

    private static HBox categoryGrandTotalRow(int amount) {
        Label label = tableCell("總  計", 105, Pos.CENTER, true, true);
        Label amountLabel = tableCell(formatAmount(amount), 185, Pos.CENTER_RIGHT, true, true);
        return new HBox(label, amountLabel);
    }

    private static HBox incomeSubtotalRow(int amount) {
        Label label = incomeTableCell("小　  計", 135, Pos.CENTER, 16);
        Label amountLabel = incomeTableCell(formatAmount(amount), 90, Pos.CENTER_RIGHT, 16);
        Label remainder = incomeTableCell("", 489, Pos.CENTER_LEFT);
        HBox row = new HBox(label, amountLabel, remainder);
        row.setStyle("-fx-border-color: #303030; -fx-border-width: 0 0 0 1;");
        return row;
    }

    private static HBox incomeTotalRow(String labelText, int amount, double labelWidth) {
        Label label = incomeTableCell(labelText, labelWidth, Pos.CENTER, 16);
        Label amountLabel = incomeTableCell(formatAmount(amount), 90, Pos.CENTER_RIGHT, 16);
        HBox row = new HBox(label, amountLabel);
        row.setStyle("-fx-border-color: #303030; -fx-border-width: 0 0 0 1;");
        return row;
    }

    private static List<Region> buildIncomeDailySummaryPages(
            String title,
            String groupHeading,
            List<IncomeSummaryLine> summaries
    ) {
        List<List<IncomeDailyDisplayLine>> pageLines = paginateDailySummaries(summaries);
        int pageCount = pageLines.size();
        int totalAmount = summaries.stream().mapToInt(IncomeSummaryLine::amount).sum();
        List<Region> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            VBox page = createIncomePage(TEMPLE_NAME + "　" + title);
            for (IncomeDailyDisplayLine line : pageLines.get(pageIndex)) {
                if (line.header()) {
                    page.getChildren().add(incomeSummaryRow(
                            new String[]{toRocDate(line.date()), groupHeading, "筆數", "金額"},
                            new double[]{170, 250, 110, 184},
                            true
                    ));
                    continue;
                }
                IncomeSummaryLine summary = line.summary();
                page.getChildren().add(incomeSummaryRow(
                        new String[]{
                                "",
                                summary.label(),
                                String.valueOf(summary.count()),
                                formatAmount(summary.amount())
                        },
                        new double[]{260, 195, 140, 184},
                        false
                ));
            }
            if (pageIndex == pageCount - 1) {
                page.getChildren().add(incomeSummaryTotalRow(totalAmount));
            }
            addIncomePageFooter(page, pageIndex + 1, pageCount);
            pages.add(page);
        }
        return pages;
    }

    private static List<List<IncomeDailyDisplayLine>> paginateDailySummaries(
            List<IncomeSummaryLine> summaries
    ) {
        List<List<IncomeDailyDisplayLine>> pages = new ArrayList<>();
        List<IncomeDailyDisplayLine> currentPage = new ArrayList<>();
        LocalDate currentDate = null;

        for (IncomeSummaryLine summary : summaries) {
            boolean needsHeader = !summary.date().equals(currentDate);
            int requiredLines = needsHeader ? 2 : 1;
            if (!currentPage.isEmpty()
                    && currentPage.size() + requiredLines > INCOME_SUMMARY_ROWS_PER_PAGE) {
                pages.add(List.copyOf(currentPage));
                currentPage = new ArrayList<>();
                currentDate = null;
                needsHeader = true;
            }
            if (needsHeader) {
                currentPage.add(new IncomeDailyDisplayLine(summary.date(), null, true));
                currentDate = summary.date();
            }
            currentPage.add(new IncomeDailyDisplayLine(summary.date(), summary, false));
        }

        if (!currentPage.isEmpty()) {
            pages.add(List.copyOf(currentPage));
        }
        if (pages.isEmpty()) {
            pages.add(List.of());
        }
        return pages;
    }

    private static List<IncomeSummaryLine> summarizeDailyIncome(
            List<Donation> donations,
            Function<Donation, String> labelProvider
    ) {
        Map<LocalDate, Map<String, int[]>> grouped = new TreeMap<>();
        for (Donation donation : sortDonations(donations)) {
            LocalDate date = parseReportDate(donation.getDonateDate());
            if (date == null) {
                continue;
            }
            String label = firstNonBlank(labelProvider.apply(donation), "未指定");
            int[] summary = grouped
                    .computeIfAbsent(date, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(label, ignored -> new int[2]);
            summary[0]++;
            summary[1] += incomeAmount(donation);
        }

        List<IncomeSummaryLine> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<String, int[]>> dateEntry : grouped.entrySet()) {
            for (Map.Entry<String, int[]> groupEntry : dateEntry.getValue().entrySet()) {
                result.add(new IncomeSummaryLine(
                        dateEntry.getKey(),
                        groupEntry.getKey(),
                        groupEntry.getValue()[0],
                        groupEntry.getValue()[1]
                ));
            }
        }
        return result;
    }

    private static List<Region> buildAuditPages(
            String title,
            String[] headers,
            double[] widths,
            List<String[]> rows,
            int... rightAlignedColumns
    ) {
        List<String[]> safeRows = rows == null ? List.of() : rows;
        int pageCount = Math.max(1, pageCount(safeRows.size(), AUDIT_ROWS_PER_PAGE));
        List<Region> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * AUDIT_ROWS_PER_PAGE;
            int to = Math.min(safeRows.size(), from + AUDIT_ROWS_PER_PAGE);
            VBox page = createIncomePage(title);
            page.getChildren().add(auditRow(headers, widths, true, rightAlignedColumns));
            for (int index = from; index < to; index++) {
                page.getChildren().add(auditRow(
                        safeRows.get(index), widths, false, rightAlignedColumns
                ));
            }
            addIncomePageFooter(page, pageIndex + 1, pageCount);
            pages.add(page);
        }
        return pages;
    }

    private static HBox auditRow(
            String[] values,
            double[] widths,
            boolean header,
            int... rightAlignedColumns
    ) {
        HBox row = new HBox(0);
        double fontSize = header ? (values.length >= 9 ? 10 : 11) : 11;
        for (int index = 0; index < values.length; index++) {
            boolean rightAligned = false;
            for (int column : rightAlignedColumns) {
                if (column == index) {
                    rightAligned = true;
                    break;
                }
            }
            Label cell = incomeTextLabel(safe(values[index]), fontSize);
            cell.setPrefWidth(widths[index]);
            cell.setMinWidth(widths[index]);
            cell.setMaxWidth(widths[index]);
            cell.setPrefHeight(30);
            cell.setMinHeight(30);
            cell.setAlignment(header ? Pos.CENTER : rightAligned ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            cell.setPadding(new Insets(2, 4, 2, 4));
            row.getChildren().add(cell);
        }
        if (header) {
            row.setStyle("-fx-border-color: #303030; -fx-border-width: 0 0 1.5 0;");
        }
        return row;
    }

    private static Donation firstDonation(Donation preferred, Donation fallback) {
        return preferred == null ? fallback : preferred;
    }

    private static String auditCategory(Map<String, String> categoryNames, Donation donation) {
        if (donation == null) {
            return "";
        }
        String category = categoryLabel(categoryNames, donation.getDonateType());
        return category.isBlank() ? firstNonBlank(donation.getSummary(), "未分類") : category;
    }

    private static HBox incomeRow(String[] values, double[] widths, boolean header, int amountColumn) {
        HBox row = new HBox(0);
        for (int index = 0; index < values.length; index++) {
            Pos alignment = header
                    ? Pos.CENTER
                    : index == amountColumn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
            row.getChildren().add(incomeTableCell(
                    values[index],
                    widths[index],
                    alignment,
                    header ? 16 : 14
            ));
        }
        row.setStyle("-fx-border-color: #303030; -fx-border-width: "
                + (header ? "1 0 0 1;" : "0 0 0 1;"));
        return row;
    }

    private static HBox incomeSummaryRow(
            String[] values,
            double[] widths,
            boolean header,
            int... rightAlignedColumns
    ) {
        HBox row = new HBox(0);
        for (int index = 0; index < values.length; index++) {
            boolean rightAligned = false;
            for (int column : rightAlignedColumns) {
                if (column == index) {
                    rightAligned = true;
                    break;
                }
            }
            Pos alignment = header ? Pos.CENTER : rightAligned ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
            row.getChildren().add(incomeSummaryCell(
                    values[index],
                    widths[index],
                    alignment,
                    header ? 16 : 14
            ));
        }
        if (header) {
            row.setStyle("-fx-border-color: #303030; -fx-border-width: 0 0 1.5 0;");
        }
        return row;
    }

    private static Label incomeSummaryCell(
            String text,
            double width,
            Pos alignment,
            double fontSize
    ) {
        Label label = incomeTextLabel(safe(text), fontSize);
        label.setPrefWidth(width);
        label.setMinWidth(width);
        label.setMaxWidth(width);
        label.setPrefHeight(32);
        label.setMinHeight(32);
        label.setAlignment(alignment);
        label.setPadding(new Insets(2, 8, 2, 8));
        return label;
    }

    private static HBox incomeSummaryTotalRow(int amount) {
        Label label = incomeSummaryCell("總金額：", 110, Pos.CENTER_LEFT, 18);
        Label amountLabel = incomeSummaryCell(formatAmount(amount), 120, Pos.CENTER_LEFT, 18);
        HBox row = new HBox(label, amountLabel);
        VBox.setMargin(row, new Insets(24, 0, 0, 4));
        return row;
    }

    private static void addIncomePageFooter(VBox page, int pageNumber, int pageCount) {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Region line = new Region();
        line.setPrefHeight(1.5);
        line.setMinHeight(1.5);
        line.setMaxHeight(1.5);
        line.setMaxWidth(Double.MAX_VALUE);
        line.setStyle("-fx-background-color: #303030;");

        Label pageLabel = incomeTextLabel("Page " + pageNumber + " of " + pageCount, 11);
        pageLabel.setMaxWidth(Double.MAX_VALUE);
        pageLabel.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(pageLabel, new Insets(6, 4, 0, 0));
        page.getChildren().addAll(spacer, line, pageLabel);
    }

    private static void addIncomePageNumber(VBox page, int pageNumber, int pageCount) {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label pageLabel = incomeTextLabel("Page " + pageNumber + " of " + pageCount, 11);
        pageLabel.setMaxWidth(Double.MAX_VALUE);
        pageLabel.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(pageLabel, new Insets(6, 4, 0, 0));
        page.getChildren().addAll(spacer, pageLabel);
    }

    private static Label incomeTableCell(String text, double width, Pos alignment) {
        return incomeTableCell(text, width, alignment, 14);
    }

    private static Label incomeTableCell(String text, double width, Pos alignment, double fontSize) {
        Label label = incomeTextLabel(safe(text), fontSize);
        label.setPrefWidth(width);
        label.setMinWidth(width);
        label.setMaxWidth(width);
        label.setPrefHeight(28);
        label.setMinHeight(28);
        label.setAlignment(alignment);
        label.setPadding(new Insets(2, 5, 2, 5));
        label.setStyle(label.getStyle()
                + "-fx-border-color: #303030; -fx-border-width: 0 1 1 0;");
        return label;
    }

    private static Label incomeTextLabel(String text, double size) {
        Label label = new Label(safe(text));
        label.setFont(Font.font(INCOME_REPORT_FONT_FAMILY, FontWeight.BOLD, size));
        label.setStyle("-fx-font-weight: bold;");
        label.setMaxWidth(CONTENT_WIDTH);
        return label;
    }

    private static String resolveIncomeReportFontFamily() {
        List<String> available = Font.getFamilies();
        String[] preferred = {"BiauKai", "Kaiti TC", "DFKai-SB", "KaiTi", "標楷體", "STKaiti"};
        for (String family : preferred) {
            if (available.contains(family)) {
                return family;
            }
        }
        return "Serif";
    }

    private static Label pageNumber(int page, int pageCount) {
        Label label = textLabel("Page " + page + " of " + pageCount, 12, false);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(label, new Insets(16, 0, 0, 0));
        return label;
    }

    private static HBox row(String[] values, double[] widths, boolean header, boolean boxed) {
        HBox row = new HBox(0);
        for (int index = 0; index < values.length; index++) {
            Pos alignment = index == values.length - 1 && boxed ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
            if (header) {
                alignment = Pos.CENTER;
            }
            row.getChildren().add(tableCell(values[index], widths[index], alignment, header, boxed));
        }
        if (!boxed) {
            row.setStyle("-fx-border-color: #303030; -fx-border-width: 0 0 1 0;");
        } else {
            row.setStyle("-fx-border-color: #303030; -fx-border-width: "
                    + (header ? "1 0 0 1;" : "0 0 0 1;"));
        }
        return row;
    }

    private static Label tableCell(String text, double width, Pos alignment, boolean bold, boolean boxed) {
        Label label = textLabel(safe(text), 14, bold);
        label.setPrefWidth(width);
        label.setMinWidth(width);
        label.setMaxWidth(width);
        label.setPrefHeight(28);
        label.setMinHeight(28);
        label.setAlignment(alignment);
        label.setPadding(new Insets(2, 5, 2, 5));
        if (boxed) {
            label.setStyle(label.getStyle() + "-fx-border-color: #303030; -fx-border-width: 0 1 1 0;");
        }
        return label;
    }

    private static Label textLabel(String text, double size, boolean bold) {
        Label label = new Label(text);
        label.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        label.setMaxWidth(CONTENT_WIDTH);
        return label;
    }

    private static String formatReceiptNo(String receiptNo) {
        if (receiptNo == null || receiptNo.isBlank()) {
            return "";
        }
        try {
            return String.format("%06d", Integer.parseInt(receiptNo.trim()));
        } catch (NumberFormatException ignored) {
            return receiptNo;
        }
    }

    private static String formatAmount(Integer amount) {
        return NumberFormat.getIntegerInstance().format(valueOrZero(amount));
    }

    private static int pageCount(int rowCount, int rowsPerPage) {
        return (rowCount + rowsPerPage - 1) / rowsPerPage;
    }

    private static List<Donation> sortDonations(List<Donation> donations) {
        if (donations == null) {
            return List.of();
        }
        return donations.stream()
                .sorted(Comparator
                        .comparing(
                                (Donation donation) -> parseReportDate(donation.getDonateDate()),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(Donation::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static LocalDate parseReportDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().replace('/', '.').replace('-', '.').split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return LocalDate.of(year < 1912 ? year + 1911 : year, month, day);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int reportAmount(Donation donation) {
        return donation == null ? 0 : valueOrZero(donation.getShouldPay());
    }

    private static int incomeAmount(Donation donation) {
        return donation == null ? 0 : valueOrZero(donation.getAmount());
    }

    private static String donationSummary(Donation donation) {
        return firstNonBlank(
                donation.getSummary(),
                donation.getDonorNo(),
                donation.getLightNo(),
                donation.getDonateNote(),
                donation.getOtherNote()
        );
    }

    private static String categoryLabel(Map<String, String> categoryNames, String categoryId) {
        String category = categoryNames == null
                ? ""
                : categoryNames.getOrDefault(categoryId, "");
        if (category.isBlank()) {
            return safe(categoryId);
        }
        int separatorIndex = category.indexOf(" - ");
        return separatorIndex >= 0 ? category.substring(separatorIndex + 3) : category;
    }

    private static List<SupplementReportLine> supplementLines(
            List<DonationSupplement> supplements,
            Map<Integer, Donation> donationsById,
            Map<Integer, LightMember> membersById,
            Map<String, String> categoryNames
    ) {
        if (supplements == null || donationsById == null) {
            return List.of();
        }
        return supplements.stream()
                .filter(supplement -> supplement.getDonationId() != null)
                .filter(supplement -> donationsById.containsKey(supplement.getDonationId()))
                .sorted(Comparator
                        .comparing(
                                (DonationSupplement supplement) -> parseReportDate(
                                        supplement.getSupplementDate()
                                ),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                DonationSupplement::getSupplementNo,
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparing(
                                DonationSupplement::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .map(supplement -> {
                    Donation donation = donationsById.get(supplement.getDonationId());
                    LightMember member = membersById == null
                            ? null
                            : membersById.get(donation.getMemberId());
                    String categoryName = categoryLabel(categoryNames, donation.getDonateType());
                    if (categoryName.isBlank()) {
                        categoryName = firstNonBlank(donation.getSummary(), "未分類");
                    }
                    String note = firstNonBlank(
                            donation.getDonateNote(),
                            donation.getOtherNote(),
                            donation.getDonorNo(),
                            donation.getLightNo()
                    );
                    return new SupplementReportLine(
                            supplement,
                            donation,
                            member == null ? "" : safe(member.getName()),
                            categoryName,
                            note
                    );
                })
                .toList();
    }

    private static String supplementDateRange(List<SupplementReportLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        LocalDate startDate = lines.stream()
                .map(line -> parseReportDate(line.supplement().getSupplementDate()))
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate endDate = lines.stream()
                .map(line -> parseReportDate(line.supplement().getSupplementDate()))
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (startDate == null || endDate == null) {
            return "";
        }
        return toRocDate(startDate) + " - " + toRocDate(endDate);
    }

    private static List<ClassifiedDonationRow> classifiedRows(
            List<Donation> donations,
            Map<String, String> categoryNames
    ) {
        List<ClassifiedDonationRow> result = new ArrayList<>();
        for (CategoryGroup group : categoryGroups(donations, categoryNames)) {
            for (Donation donation : group.donations()) {
                result.add(new ClassifiedDonationRow(group.categoryName(), donation, false, 0));
            }
            int subtotal = group.donations().stream().mapToInt(LightReportBuilder::reportAmount).sum();
            result.add(new ClassifiedDonationRow(group.categoryName(), null, true, subtotal));
        }
        return result;
    }

    private static List<CategorySummary> categorySummaries(
            List<Donation> donations,
            Map<String, String> categoryNames
    ) {
        return categoryGroups(donations, categoryNames).stream()
                .map(group -> new CategorySummary(
                        group.categoryName(),
                        group.donations().size(),
                        group.donations().stream().mapToInt(LightReportBuilder::reportAmount).sum()
                ))
                .toList();
    }

    private static List<CategoryGroup> categoryGroups(
            List<Donation> donations,
            Map<String, String> categoryNames
    ) {
        List<Donation> sortedDonations = sortDonations(donations);
        Map<String, List<Donation>> grouped = new LinkedHashMap<>();

        if (categoryNames != null) {
            for (String categoryId : categoryNames.keySet()) {
                grouped.put(categoryId, new ArrayList<>());
            }
        }
        for (Donation donation : sortedDonations) {
            grouped.computeIfAbsent(safe(donation.getDonateType()), ignored -> new ArrayList<>())
                    .add(donation);
        }

        List<CategoryGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<Donation>> entry : grouped.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(new CategoryGroup(
                        categoryLabel(categoryNames, entry.getKey()),
                        List.copyOf(entry.getValue())
                ));
            }
        }
        return result;
    }

    private static String toRocDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('/', '.').replace('-', '.');
        String[] parts = normalized.split("\\.");
        if (parts.length != 3) {
            return value;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (year < 1912) {
                return String.format("%03d.%02d.%02d", year, month, day);
            }
            LocalDate date = LocalDate.parse(year + "." + month + "." + day, DOT_DATE);
            return String.format("%03d.%02d.%02d", date.getYear() - 1911, date.getMonthValue(), date.getDayOfMonth());
        } catch (NumberFormatException | DateTimeParseException ignored) {
            return value;
        }
    }

    private static String toRocDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return String.format(
                "%03d.%02d.%02d",
                date.getYear() - 1911,
                date.getMonthValue(),
                date.getDayOfMonth()
        );
    }

    private static String toRocDateTime(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() < 10) {
            return value;
        }
        String date = toRocDate(trimmed.substring(0, 10));
        String time = trimmed.length() > 10 ? trimmed.substring(10).trim() : "";
        return time.isBlank() ? date : date + " " + time;
    }

    private static String toRocMonth(YearMonth month) {
        if (month == null) {
            return "";
        }
        return String.format("%03d.%02d", month.getYear() - 1911, month.getMonthValue());
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record CategoryGroup(String categoryName, List<Donation> donations) {
    }

    private record ClassifiedDonationRow(
            String categoryName,
            Donation donation,
            boolean subtotal,
            int amount
    ) {
    }

    private record CategorySummary(String categoryName, int count, int amount) {
    }

    private record IncomeReportLine(
            String categoryName,
            Donation donation,
            boolean subtotal,
            int amount
    ) {
    }

    private record IncomeSummaryLine(LocalDate date, String label, int count, int amount) {
    }

    private record IncomeDailyDisplayLine(
            LocalDate date,
            IncomeSummaryLine summary,
            boolean header
    ) {
    }

    private record SupplementReportLine(
            DonationSupplement supplement,
            Donation donation,
            String memberName,
            String categoryName,
            String note
    ) {
    }
}
