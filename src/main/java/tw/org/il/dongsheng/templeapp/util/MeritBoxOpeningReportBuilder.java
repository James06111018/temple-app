package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.MeritBoxOpening;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MeritBoxOpeningReportBuilder {
    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final double LEFT = 48;
    private static final double RIGHT = 746;
    private static final double HEADER_Y = 132;
    private static final double ROW_HEIGHT = 29;
    private static final int ROWS_PER_PAGE = 27;
    private static final double[] X = {48, 126, 206, 302, 424, 650};
    private static final double[] W = {78, 90, 86, 122, 226, 96};

    private MeritBoxOpeningReportBuilder() {
    }

    public static List<Pane> buildPages(List<MeritBoxOpening> rows) {
        int pageCount = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        long total = rows.stream().mapToLong(MeritBoxOpening::amount).sum();
        List<Pane> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage();
            addText(page, "開 箱 明 細 表", 210, 56, 374, 40, 25, Pos.CENTER, true);
            String[] headers = {"日 期", "時 間", "金 額", "開箱人員", "備 註", "分 類"};
            for (int i = 0; i < headers.length; i++) {
                addText(page, headers[i], X[i], 102, W[i], 28, 14, i == 2 ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT, true);
            }
            line(page, LEFT, HEADER_Y, RIGHT, HEADER_Y);

            int from = pageIndex * ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + ROWS_PER_PAGE);
            for (int i = from; i < to; i++) {
                MeritBoxOpening row = rows.get(i);
                double y = HEADER_Y + 9 + (i - from) * ROW_HEIGHT;
                addText(page, rocDate(row), X[0], y, W[0], 24, 13, Pos.CENTER_LEFT, true);
                addText(page, "", X[1], y, W[1], 24, 13, Pos.CENTER_LEFT, true);
                addText(page, number(row.amount()), X[2], y, W[2], 24, 13, Pos.CENTER_RIGHT, true);
                addText(page, row.opener(), X[3], y, W[3], 24, 13, Pos.CENTER_LEFT, true);
                addText(page, row.note(), X[4], y, W[4], 24, 13, Pos.CENTER_LEFT, true);
                addText(page, row.categoryName(), X[5], y, W[5], 24, 13, Pos.CENTER_LEFT, true);
            }

            if (pageIndex == pageCount - 1) {
                addText(page, "總金額： " + number(total), LEFT, 953, 320, 34, 17, Pos.CENTER_LEFT, true);
            }
            line(page, LEFT, 1040, RIGHT, 1040);
            addText(page, "Page " + (pageIndex + 1) + " of " + pageCount, 630, 1046, 116, 24, 11, Pos.CENTER_RIGHT, false);
            pages.add(page);
        }
        return pages;
    }

    private static Pane createPage() {
        Pane page = new Pane();
        page.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMinSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMaxSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setStyle("-fx-background-color: white; -fx-border-color: #222; -fx-border-width: 1;");
        return page;
    }

    private static void addText(Pane page, String value, double x, double y, double width, double height,
                                double size, Pos alignment, boolean bold) {
        Label label = new Label(value == null ? "" : value);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setPrefSize(width, height);
        label.setAlignment(alignment);
        label.setFont(Font.font(reportFont(), bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        label.setWrapText(false);
        page.getChildren().add(label);
    }

    private static void line(Pane page, double startX, double startY, double endX, double endY) {
        javafx.scene.shape.Line line = new javafx.scene.shape.Line(startX, startY, endX, endY);
        line.setStrokeWidth(1.2);
        page.getChildren().add(line);
    }

    private static String rocDate(MeritBoxOpening row) {
        return String.format("%03d.%02d.%02d", row.openingDate().getYear() - 1911,
                row.openingDate().getMonthValue(), row.openingDate().getDayOfMonth());
    }

    private static String number(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private static String reportFont() {
        for (String candidate : List.of("BiauKai", "Kaiti TC", "DFKai-SB", "KaiTi", "標楷體", "STKaiti")) {
            if (Font.getFamilies().contains(candidate)) {
                return candidate;
            }
        }
        return Font.getDefault().getFamily();
    }
}
