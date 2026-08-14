package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageOrientation;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.DonationDetailRow;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DonationDetailsReportBuilder {
    public enum ReportKind {
        PAYMENT("收據編號"),
        SUPPLEMENT("補登編號"),
        UNPAID("收據編號");

        private final String numberHeader;

        ReportKind(String numberHeader) {
            this.numberHeader = numberHeader;
        }
    }

    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final double CONTENT_X = 38;
    private static final double HEADER_Y = 112;
    private static final double HEADER_HEIGHT = 30;
    private static final double ROW_HEIGHT = 27;
    private static final int ROWS_PER_PAGE = 33;
    private static final double[] COLUMN_WIDTHS = {76, 86, 105, 124, 82, 128, 117};
    private static final double SECONDARY_AMOUNT_SUMMARY_GAP = 12;
    private static final String REPORT_FONT_FAMILY = resolveReportFontFamily();
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    private DonationDetailsReportBuilder() {
    }

    public static List<Region> buildPages(
            String title,
            List<DonationDetailRow> reportRows,
            ReportKind kind
    ) {
        List<DonationDetailRow> rows = reportRows == null ? List.of() : List.copyOf(reportRows);
        int pageCount = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage();
            addText(page, safe(title), 38, 42, 718, 48, 25, Pos.CENTER, true);
            addHeader(page, kind);

            int from = pageIndex * ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + ROWS_PER_PAGE);
            double y = HEADER_Y + HEADER_HEIGHT;
            for (int index = from; index < to; index++) {
                addDataRow(page, rows.get(index), kind, y);
                y += ROW_HEIGHT;
            }

            Line footerLine = new Line(CONTENT_X, 1060, CONTENT_X + tableWidth(), 1060);
            footerLine.setStrokeWidth(1.2);
            page.getChildren().add(footerLine);
            addText(
                    page,
                    "Page " + (pageIndex + 1) + " of " + pageCount,
                    610,
                    1064,
                    146,
                    22,
                    10,
                    Pos.CENTER_RIGHT,
                    true
            );
            pages.add(page);
        }
        return pages;
    }

    private static void addHeader(Pane page, ReportKind kind) {
        String[] headers = {
                kind.numberHeader,
                "日期",
                "姓名",
                "款項類別",
                "金額",
                "摘要",
                "備註"
        };
        double x = CONTENT_X;
        for (int index = 0; index < headers.length; index++) {
            Pos alignment = index == 4 ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
            Label label = addText(
                    page,
                    headers[index],
                    x,
                    HEADER_Y,
                    COLUMN_WIDTHS[index],
                    HEADER_HEIGHT,
                    14,
                    alignment,
                    true
            );
            applySecondaryDetailSpacing(label, kind, index);
            x += COLUMN_WIDTHS[index];
        }
        Line line = new Line(CONTENT_X, HEADER_Y + HEADER_HEIGHT, CONTENT_X + tableWidth(), HEADER_Y + HEADER_HEIGHT);
        line.setStrokeWidth(1.4);
        page.getChildren().add(line);
    }

    private static void addDataRow(Pane page, DonationDetailRow row, ReportKind kind, double y) {
        String number = switch (kind) {
            case PAYMENT -> formatReceiptNo(row.receiptNo());
            case SUPPLEMENT -> row.supplementNo();
            case UNPAID -> formatReceiptNo(row.receiptNo());
        };
        long amount = kind == ReportKind.UNPAID
                ? Math.max(0L, amount(row.amountDue()) - amount(row.amount()))
                : amount(row.amount());
        String[] values = {
                safe(number),
                safe(row.donationDate()),
                safe(row.name()),
                categoryName(row.category()),
                INTEGER_FORMAT.format(amount),
                safe(row.summary()),
                safe(row.donationNote())
        };

        double x = CONTENT_X;
        for (int index = 0; index < values.length; index++) {
            Pos alignment = index == 4 ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
            Label label = addText(
                    page,
                    values[index],
                    x,
                    y,
                    COLUMN_WIDTHS[index],
                    ROW_HEIGHT,
                    13,
                    alignment,
                    true
            );
            applySecondaryDetailSpacing(label, kind, index);
            x += COLUMN_WIDTHS[index];
        }
    }

    private static void applySecondaryDetailSpacing(Label label, ReportKind kind, int columnIndex) {
        if (columnIndex == 4) {
            label.setPadding(new Insets(0, SECONDARY_AMOUNT_SUMMARY_GAP, 0, 0));
        } else if (columnIndex == 5) {
            label.setPadding(new Insets(0, 0, 0, SECONDARY_AMOUNT_SUMMARY_GAP));
        }
    }

    private static Pane createPage() {
        Pane page = new Pane();
        page.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMinSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMaxSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");
        PrintPreview.setPageOrientation(page, PageOrientation.PORTRAIT);
        return page;
    }

    private static Label addText(
            Pane parent,
            String value,
            double x,
            double y,
            double width,
            double height,
            double fontSize,
            Pos alignment,
            boolean bold
    ) {
        Label label = new Label(safe(value));
        label.setFont(Font.font(REPORT_FONT_FAMILY, bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize));
        label.setAlignment(alignment);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        label.setPrefSize(width, height);
        label.setMinSize(width, height);
        label.setMaxSize(width, height);
        label.relocate(x, y);
        parent.getChildren().add(label);
        return label;
    }

    private static String categoryName(String category) {
        return safe(category).replaceFirst("^\\S+\\s*-\\s*", "");
    }

    private static String formatReceiptNo(String receiptNo) {
        if (receiptNo == null || receiptNo.isBlank()) {
            return "";
        }
        try {
            return String.format("%06d", Integer.parseInt(receiptNo.trim()));
        } catch (NumberFormatException ignored) {
            return receiptNo.trim();
        }
    }

    private static long amount(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private static double tableWidth() {
        double width = 0;
        for (double columnWidth : COLUMN_WIDTHS) {
            width += columnWidth;
        }
        return width;
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
