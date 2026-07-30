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
import java.util.List;
import java.util.Map;

public final class LightReportBuilder {
    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final double CONTENT_WIDTH = 714;
    private static final int ROSTER_ROWS_PER_PAGE = 30;
    private static final int DETAIL_ROWS_PER_PAGE = 27;
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

    private static VBox createPage(String title) {
        VBox page = new VBox(0);
        page.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMinSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMaxSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setPadding(new Insets(34, 40, 28, 40));
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");

        Label titleLabel = new Label(title);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
        VBox.setMargin(titleLabel, new Insets(0, 0, 14, 0));
        page.getChildren().add(titleLabel);

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
}
