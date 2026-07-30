package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LightReportBuilder {
    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final double CONTENT_WIDTH = 714;
    private static final int ROSTER_ROWS_PER_PAGE = 30;
    private static final int DETAIL_ROWS_PER_PAGE = 27;
    private static final int TOTAL_DETAIL_ROWS_PER_PAGE = 24;
    private static final int CATEGORY_ROWS_PER_PAGE = 23;
    private static final int STATISTICS_ROWS_PER_PAGE = 25;
    private static final String TEMPLE_NAME = "五結東聖宮";
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
        Label totalLabel = tableCell("合　計", 624, Pos.CENTER_RIGHT, true, true);
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
        Label label = tableCell(categoryName + "　小計", 624, Pos.CENTER, true, true);
        Label amountLabel = tableCell(formatAmount(amount), 90, Pos.CENTER_RIGHT, true, true);
        return new HBox(label, amountLabel);
    }

    private static HBox categoryGrandTotalRow(int amount) {
        Label label = tableCell("總　計", 105, Pos.CENTER, true, true);
        Label amountLabel = tableCell(formatAmount(amount), 185, Pos.CENTER_RIGHT, true, true);
        return new HBox(label, amountLabel);
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
}
