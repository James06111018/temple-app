package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageOrientation;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.DonationDetailRow;

import java.util.ArrayList;
import java.util.List;

public final class DonationRosterReportBuilder {
    private static final double PAGE_WIDTH = 1123;
    private static final double PAGE_HEIGHT = 794;
    private static final double SIDE_MARGIN = 34;
    private static final double TOP_MARGIN = 24;
    private static final int ROSTER_ROWS_PER_PAGE = 13;
    private static final int DONATION_LIST_ROWS_PER_PAGE = 14;
    private static final String REPORT_FONT_FAMILY = resolveReportFontFamily();

    private DonationRosterReportBuilder() {
    }

    public static List<Region> buildRosterPages(String title, List<DonationDetailRow> sourceRows) {
        List<DonationDetailRow> rows = printableRows(sourceRows);
        int pageCount = pageCount(rows.size(), ROSTER_ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();

        double tableTop = 92;
        double tableWidth = PAGE_WIDTH - SIDE_MARGIN * 2;
        double headerHeight = 42;
        double rowHeight = 47;
        double numberWidth = 68;
        double nameWidth = 250;
        double addressWidth = 590;
        double noteWidth = tableWidth - numberWidth - nameWidth - addressWidth;

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createLandscapePage();
            HBox titleCell = rotatedCharacterCell(title, tableWidth, 52, 28, false, Pos.CENTER);
            titleCell.relocate(SIDE_MARGIN, TOP_MARGIN);
            page.getChildren().add(titleCell);

            double x = SIDE_MARGIN;
            page.getChildren().add(rotatedHeaderCell("編號", x, tableTop, numberWidth, headerHeight));
            x += numberWidth;
            page.getChildren().add(rotatedHeaderCell("姓    名", x, tableTop, nameWidth, headerHeight));
            x += nameWidth;
            page.getChildren().add(rotatedHeaderCell("地    址", x, tableTop, addressWidth, headerHeight));
            x += addressWidth;
            page.getChildren().add(rotatedHeaderCell("備  註", x, tableTop, noteWidth, headerHeight));

            int from = pageIndex * ROSTER_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + ROSTER_ROWS_PER_PAGE);
            for (int slot = 0; slot < ROSTER_ROWS_PER_PAGE; slot++) {
                DonationDetailRow row = from + slot < to ? rows.get(from + slot) : null;
                double y = tableTop + headerHeight + slot * rowHeight;
                x = SIDE_MARGIN;
                page.getChildren().add(rotatedTableCell(
                        row == null ? "" : Integer.toString(from + slot + 1),
                        x,
                        y,
                        numberWidth,
                        rowHeight,
                        16,
                        Pos.CENTER
                ));
                x += numberWidth;
                page.getChildren().add(rotatedTableCell(
                        row == null ? "" : row.name(), x, y, nameWidth, rowHeight, 24
                ));
                x += nameWidth;
                page.getChildren().add(rotatedTableCell(
                        row == null ? "" : row.address(), x, y, addressWidth, rowHeight, 15
                ));
                x += addressWidth;
                page.getChildren().add(rotatedTableCell(
                        row == null ? "" : rosterNote(row), x, y, noteWidth, rowHeight, 14
                ));
            }
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildDonationListPages(List<DonationDetailRow> sourceRows) {
        List<DonationDetailRow> rows = printableRows(sourceRows);
        int pageCount = pageCount(rows.size(), DONATION_LIST_ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();

        double sideMargin = 14;
        double tableTop = 14;
        double tableWidth = PAGE_WIDTH - sideMargin * 2;
        double rowHeight = (PAGE_HEIGHT - tableTop * 2) / DONATION_LIST_ROWS_PER_PAGE;
        double nameWidth = 370;
        double amountWidth = tableWidth - nameWidth;

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createLandscapePage();
            int from = pageIndex * DONATION_LIST_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + DONATION_LIST_ROWS_PER_PAGE);
            for (int slot = 0; slot < DONATION_LIST_ROWS_PER_PAGE; slot++) {
                DonationDetailRow row = from + slot < to ? rows.get(from + slot) : null;
                double y = tableTop + slot * rowHeight;
                page.getChildren().add(rotatedTableCell(
                        row == null ? "" : row.name(), sideMargin, y, nameWidth, rowHeight, 30
                ));
                page.getChildren().add(rotatedTableCell(
                        row == null ? "" : financialChinese(amount(row.amount())),
                        sideMargin + nameWidth,
                        y,
                        amountWidth,
                        rowHeight,
                        28
                ));
            }
            pages.add(page);
        }
        return pages;
    }

    private static Region rotatedHeaderCell(
            String text,
            double x,
            double y,
            double width,
            double height
    ) {
        HBox cell = rotatedCharacterCell(text, width, height, 15, true, Pos.CENTER);
        cell.relocate(x, y);
        return cell;
    }

    private static Region rotatedTableCell(
            String text,
            double x,
            double y,
            double width,
            double height,
            double fontSize
    ) {
        return rotatedTableCell(text, x, y, width, height, fontSize, Pos.CENTER_LEFT);
    }

    private static Region rotatedTableCell(
            String text,
            double x,
            double y,
            double width,
            double height,
            double fontSize,
            Pos alignment
    ) {
        HBox cell = rotatedCharacterCell(text, width, height, fontSize, true, alignment);
        cell.relocate(x, y);
        return cell;
    }

    private static HBox rotatedCharacterCell(
            String value,
            double width,
            double height,
            double preferredFontSize,
            boolean bordered,
            Pos alignment
    ) {
        String text = safe(value);
        int[] codePoints = text.codePoints().toArray();
        double horizontalPadding = 8;
        double verticalPadding = 3;
        double glyphPadding = 4;
        double availableWidth = width - horizontalPadding * 2;
        double availableHeight = height - verticalPadding * 2;
        double fontSize = codePoints.length == 0
                ? preferredFontSize
                : Math.min(preferredFontSize, Math.min(
                        availableHeight - glyphPadding,
                        availableWidth / codePoints.length - glyphPadding
                ));
        fontSize = Math.max(8, fontSize);

        HBox cell = new HBox();
        cell.setAlignment(alignment);
        cell.setSpacing(0);
        cell.setPadding(new Insets(verticalPadding, horizontalPadding, verticalPadding, horizontalPadding));
        cell.setPrefSize(width, height);
        cell.setMinSize(width, height);
        cell.setMaxSize(width, height);
        if (bordered) {
            cell.setStyle("-fx-border-color: #202020; -fx-border-width: 1;");
        }

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

    private static Pane createLandscapePage() {
        Pane page = new Pane();
        page.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMinSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMaxSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");
        PrintPreview.setPageOrientation(page, PageOrientation.LANDSCAPE);
        return page;
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

    private static List<DonationDetailRow> printableRows(List<DonationDetailRow> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().filter(row -> row != null && !safe(row.name()).isBlank()).toList();
    }

    private static String rosterNote(DonationDetailRow row) {
        if (!safe(row.donationNote()).isBlank()) {
            return row.donationNote();
        }
        if (!safe(row.summary()).isBlank()) {
            return row.summary();
        }
        return row.description();
    }

    private static long amount(Integer value) {
        return value == null ? 0 : value;
    }

    private static int pageCount(int rowCount, int rowsPerPage) {
        return Math.max(1, (rowCount + rowsPerPage - 1) / rowsPerPage);
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
}
