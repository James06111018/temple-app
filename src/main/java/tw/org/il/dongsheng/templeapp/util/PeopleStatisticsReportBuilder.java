package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Pos;
import javafx.print.PageOrientation;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.PeopleStatisticsRow;

import java.text.Collator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PeopleStatisticsReportBuilder {
    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final int ROSTER_ROWS_PER_PAGE = 40;
    private static final int NAME_LABEL_ROWS_PER_PAGE = 20;
    private static final int MULTI_LABEL_COLUMNS = 2;
    private static final int MULTI_LABEL_ROWS = 8;
    private static final double REPORT_SIDE_MARGIN = 24;
    private static final double REPORT_TOP_MARGIN = 24;
    private static final double REPORT_BOTTOM_MARGIN = 24;
    private static final String REPORT_FONT_FAMILY = resolveReportFontFamily();
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    private PeopleStatisticsReportBuilder() {
    }

    public static List<Region> buildHorizontalLabelPages(List<PeopleStatisticsRow> rows) {
        return PrintLabelsReportBuilder.buildHorizontalLabelPages(toMembers(rows), 1);
    }

    public static List<Region> buildVerticalLabelPages(List<PeopleStatisticsRow> rows) {
        return PrintLabelsReportBuilder.buildVerticalLabelPages(toMembers(rows), 1);
    }

    public static List<Region> buildLetterPages(List<PeopleStatisticsRow> rows) {
        return PrintLabelsReportBuilder.buildLetterPages(toMembers(rows));
    }

    public static List<Region> buildDonationRosterPages(List<PeopleStatisticsRow> sourceRows, boolean sortByAmount) {
        Comparator<PeopleStatisticsRow> comparator;
        String title;
        if (sortByAmount) {
            comparator = Comparator.comparingLong(PeopleStatisticsRow::totalAmount).reversed()
                    .thenComparingInt(PeopleStatisticsRow::memberId);
            title = "捐 款 名 冊（依金額排序）";
        } else {
            Collator collator = Collator.getInstance(Locale.TAIWAN);
            comparator = Comparator.comparing(PeopleStatisticsRow::name, collator)
                    .thenComparingInt(PeopleStatisticsRow::memberId);
            title = "捐 款 名 冊（依姓名排序）";
        }

        List<PeopleStatisticsRow> rows = printableRows(sourceRows).stream().sorted(comparator).toList();
        int pageCount = pageCount(rows.size(), ROSTER_ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage();
            addText(page, title, 0, 20, PAGE_WIDTH, 36, 23, Pos.CENTER, true);
            addRosterHeader(page);

            int from = pageIndex * ROSTER_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + ROSTER_ROWS_PER_PAGE);
            double y = 104;
            for (int index = from; index < to; index++) {
                addRosterRow(page, rows.get(index), y);
                y += 24;
            }
            addFooter(page, pageIndex + 1, pageCount);
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildDonationNameLabelPages(List<PeopleStatisticsRow> sourceRows) {
        List<PeopleStatisticsRow> rows = printableRows(sourceRows).stream()
                .sorted(Comparator.comparingLong(PeopleStatisticsRow::totalAmount).reversed()
                        .thenComparingInt(PeopleStatisticsRow::memberId))
                .toList();
        int pageCount = pageCount(rows.size(), NAME_LABEL_ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage();
            double tableTop = REPORT_TOP_MARGIN;
            double rowHeight = (PAGE_HEIGHT - REPORT_TOP_MARGIN - REPORT_BOTTOM_MARGIN)
                    / NAME_LABEL_ROWS_PER_PAGE;

            int from = pageIndex * NAME_LABEL_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + NAME_LABEL_ROWS_PER_PAGE);
            for (int slot = 0; slot < NAME_LABEL_ROWS_PER_PAGE; slot++) {
                int index = from + slot;
                PeopleStatisticsRow row = index < to ? rows.get(index) : null;
                addNameLabelRow(
                        page,
                        tableTop + slot * rowHeight,
                        rowHeight,
                        row == null ? "" : row.name(),
                        row == null ? "" : financialChinese(row.totalAmount())
                );
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildMultiRecipientLabelPages(List<PeopleStatisticsRow> sourceRows) {
        List<MultiRecipientRow> rows = groupRecipients(printableRows(sourceRows));
        int slotsPerPage = MULTI_LABEL_COLUMNS * MULTI_LABEL_ROWS;
        int pageCount = pageCount(rows.size(), slotsPerPage);
        List<Region> pages = new ArrayList<>();
        double left = REPORT_SIDE_MARGIN;
        double top = REPORT_TOP_MARGIN;
        double columnGap = 28;
        double rowGap = 6;
        double cellWidth = (PAGE_WIDTH - left * 2 - columnGap) / MULTI_LABEL_COLUMNS;
        double cellHeight = (PAGE_HEIGHT - top * 2 - rowGap * (MULTI_LABEL_ROWS - 1)) / MULTI_LABEL_ROWS;

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage();
            for (int slot = 0; slot < slotsPerPage; slot++) {
                int rowIndex = pageIndex * slotsPerPage + slot;
                if (rowIndex >= rows.size()) {
                    break;
                }
                int column = slot % MULTI_LABEL_COLUMNS;
                int row = slot / MULTI_LABEL_COLUMNS;
                Pane label = multiRecipientLabel(rows.get(rowIndex), cellWidth, cellHeight);
                label.relocate(
                        left + column * (cellWidth + columnGap),
                        top + row * (cellHeight + rowGap)
                );
                page.getChildren().add(label);
            }
            pages.add(page);
        }
        return pages;
    }

    private static void addRosterHeader(Pane page) {
        addText(page, "電腦編號", 24, 68, 88, 30, 15, Pos.CENTER_LEFT, true);
        addText(page, "姓名", 120, 68, 120, 30, 15, Pos.CENTER_LEFT, true);
        addText(page, "地址", 246, 68, 316, 30, 15, Pos.CENTER_LEFT, true);
        addText(page, "電話", 568, 68, 130, 30, 15, Pos.CENTER_LEFT, true);
        addText(page, "累計金額", 704, 68, 66, 30, 15, Pos.CENTER_RIGHT, true);
        addHorizontalLine(page, 24, 100, 746);
    }

    private static void addRosterRow(Pane page, PeopleStatisticsRow row, double y) {
        addText(page, Util.stringFormat(row.memberId()), 24, y, 88, 23, 12, Pos.CENTER_LEFT, true);
        addText(page, safe(row.name()), 120, y, 120, 23, 12, Pos.CENTER_LEFT, true);
        addText(page, safe(row.address()), 246, y, 316, 23, 12, Pos.CENTER_LEFT, true);
        addText(page, safe(row.phone()), 568, y, 130, 23, 12, Pos.CENTER_LEFT, true);
        addText(page, INTEGER_FORMAT.format(row.totalAmount()), 704, y, 66, 23, 12, Pos.CENTER_RIGHT, true);
    }

    private static void addNameLabelRow(Pane page, double y, double height, String name, String amount) {
        HBox row = new HBox();
        double rowWidth = PAGE_WIDTH - REPORT_SIDE_MARGIN * 2;
        double nameWidth = 250;
        double amountWidth = rowWidth - nameWidth;
        row.setPrefSize(rowWidth, height);
        row.setMinSize(rowWidth, height);
        row.setMaxSize(rowWidth, height);
        row.setStyle("-fx-border-color: #202020; -fx-border-width: 1 1 1 1;");
        row.getChildren().add(rotatedCharacterCell(name, nameWidth, height, 30));
        row.getChildren().add(rotatedCharacterCell(amount, amountWidth, height, 28));
        row.relocate(REPORT_SIDE_MARGIN, y);
        page.getChildren().add(row);
    }

    private static HBox rotatedCharacterCell(String value, double width, double height, double preferredFontSize) {
        String text = safe(value);
        int[] codePoints = text.codePoints().toArray();
        double horizontalPadding = 10;
        double verticalPadding = 3;
        double availableWidth = width - horizontalPadding * 2;
        double availableHeight = height - verticalPadding * 2;
        double glyphPadding = 6;
        double fontSize = codePoints.length == 0
                ? preferredFontSize
                : Math.min(preferredFontSize, Math.min(
                        availableHeight - glyphPadding,
                        availableWidth / codePoints.length - glyphPadding
                ));
        fontSize = Math.max(8, fontSize);

        HBox cell = new HBox();
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setSpacing(0);
        cell.setPadding(new javafx.geometry.Insets(
                verticalPadding,
                horizontalPadding,
                verticalPadding,
                horizontalPadding
        ));
        cell.setPrefSize(width, height);
        cell.setMinSize(width, height);
        cell.setMaxSize(width, height);
        cell.setStyle("-fx-border-color: #202020; -fx-border-width: 0 1 0 0;");

        double glyphSize = fontSize + glyphPadding;
        for (int codePoint : codePoints) {
            Label glyph = fixedLabel(
                    new String(Character.toChars(codePoint)),
                    glyphSize,
                    glyphSize,
                    fontSize,
                    Pos.CENTER,
                    true
            );
            glyph.setRotate(-90);
            cell.getChildren().add(glyph);
        }
        return cell;
    }

    private static Pane multiRecipientLabel(MultiRecipientRow row, double width, double height) {
        Pane cell = fixedPane(width, height);
        addText(cell, row.zipCode(), 0, 4, 75, 24, 14, Pos.CENTER_LEFT, true);
        addText(cell, row.address(), 0, 27, width - 4, 35, 14, Pos.TOP_LEFT, true);
        Label names = addText(cell, row.names(), 0, 67, width - 4, height - 71, 20, Pos.TOP_LEFT, true);
        names.setWrapText(true);
        return cell;
    }

    private static List<MultiRecipientRow> groupRecipients(List<PeopleStatisticsRow> rows) {
        Map<String, RecipientAccumulator> grouped = new LinkedHashMap<>();
        for (PeopleStatisticsRow row : rows) {
            String address = safe(row.address()).trim();
            String key = address.isEmpty()
                    ? "member:" + row.memberId()
                    : safe(row.zipCode()).trim() + "\u0000" + address;
            grouped.computeIfAbsent(
                    key,
                    ignored -> new RecipientAccumulator(safe(row.zipCode()).trim(), address)
            ).names.add(safe(row.name()).trim());
        }
        return grouped.values().stream()
                .map(value -> new MultiRecipientRow(value.zipCode, value.address, String.join("、", value.names)))
                .toList();
    }

    private static List<LightMember> toMembers(List<PeopleStatisticsRow> rows) {
        return printableRows(rows).stream().map(row -> {
            LightMember member = new LightMember();
            member.setId(row.memberId());
            member.setName(row.name());
            member.setAddress(row.address());
            member.setPhone(row.phone());
            member.setZipCode(row.zipCode());
            member.setIsMail(row.isMail());
            return member;
        }).toList();
    }

    private static List<PeopleStatisticsRow> printableRows(List<PeopleStatisticsRow> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().filter(row -> row != null && !safe(row.name()).isBlank()).toList();
    }

    private static String financialChinese(long value) {
        if (value == 0) {
            return "零元";
        }
        if (value < 0) {
            return "負" + financialChinese(-value);
        }
        String[] largeUnits = {"", "萬", "億", "兆", "京"};
        List<Integer> groups = new ArrayList<>();
        long remaining = value;
        while (remaining > 0) {
            groups.add((int) (remaining % 10_000));
            remaining /= 10_000;
        }

        StringBuilder result = new StringBuilder();
        boolean pendingZero = false;
        for (int index = groups.size() - 1; index >= 0; index--) {
            int group = groups.get(index);
            if (group == 0) {
                if (!result.isEmpty()) {
                    pendingZero = true;
                }
                continue;
            }
            if (!result.isEmpty() && (pendingZero || group < 1000)) {
                result.append('零');
            }
            result.append(financialGroup(group)).append(largeUnits[index]);
            pendingZero = false;
        }
        return result.append('元').toString();
    }

    private static String financialGroup(int value) {
        String[] digits = {"零", "壹", "貳", "參", "肆", "伍", "陸", "柒", "捌", "玖"};
        String[] units = {"仟", "佰", "拾", ""};
        int divisor = 1000;
        StringBuilder result = new StringBuilder();
        boolean pendingZero = false;
        for (int index = 0; index < units.length; index++) {
            int digit = value / divisor;
            value %= divisor;
            divisor /= 10;
            if (digit == 0) {
                pendingZero = !result.isEmpty() && value > 0;
                continue;
            }
            if (pendingZero) {
                result.append('零');
                pendingZero = false;
            }
            result.append(digits[digit]).append(units[index]);
        }
        return result.toString();
    }

    private static Pane createPage() {
        Pane page = fixedPane(PAGE_WIDTH, PAGE_HEIGHT);
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");
        PrintPreview.setPageOrientation(page, PageOrientation.PORTRAIT);
        return page;
    }

    private static Pane fixedPane(double width, double height) {
        Pane pane = new Pane();
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
    }

    private static Label addText(
            Pane parent,
            String text,
            double x,
            double y,
            double width,
            double height,
            double fontSize,
            Pos alignment,
            boolean bold
    ) {
        Label label = fixedLabel(text, width, height, fontSize, alignment, bold);
        label.relocate(x, y);
        parent.getChildren().add(label);
        return label;
    }

    private static Label fixedLabel(
            String text,
            double width,
            double height,
            double fontSize,
            Pos alignment,
            boolean bold
    ) {
        Label label = new Label(safe(text));
        label.setFont(Font.font(REPORT_FONT_FAMILY, bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize));
        label.setAlignment(alignment);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        label.setPrefSize(width, height);
        label.setMinSize(width, height);
        label.setMaxSize(width, height);
        return label;
    }

    private static void addHorizontalLine(Pane page, double x, double y, double width) {
        Region line = new Region();
        line.setPrefSize(width, 1);
        line.setStyle("-fx-border-color: #202020; -fx-border-width: 0 0 1.2 0;");
        line.relocate(x, y);
        page.getChildren().add(line);
    }

    private static void addFooter(Pane page, int pageNumber, int pageCount) {
        addHorizontalLine(page, 24, 1080, 746);
        addText(page, "Page " + pageNumber + " of " + pageCount, 604, 1084, 166, 24, 11, Pos.CENTER_RIGHT, false);
    }

    private static int pageCount(int rowCount, int rowsPerPage) {
        return Math.max(1, (rowCount + rowsPerPage - 1) / rowsPerPage);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String resolveReportFontFamily() {
        List<String> available = Font.getFamilies();
        String[] preferred = {"BiauKai", "Kaiti TC", "DFKai-SB", "KaiTi", "標楷體", "STKaiti"};
        for (String family : preferred) {
            if (available.contains(family)) {
                return family;
            }
        }
        return "Serif";
    }

    private record MultiRecipientRow(String zipCode, String address, String names) {
    }

    private static final class RecipientAccumulator {
        private final String zipCode;
        private final String address;
        private final List<String> names = new ArrayList<>();

        private RecipientAccumulator(String zipCode, String address) {
            this.zipCode = zipCode;
            this.address = address;
        }
    }
}
