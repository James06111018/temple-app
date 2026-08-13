package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.DonationRankingRow;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public final class DonationRankingReportBuilder {
    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final double TABLE_X = 52;
    private static final double TABLE_Y = 132;
    private static final double ROW_HEIGHT = 34;
    private static final int ROWS_PER_PAGE = 24;
    private static final double[] COLUMN_WIDTHS = {72, 296, 104, 142, 76};
    private static final String REPORT_FONT_FAMILY = resolveReportFontFamily();

    private DonationRankingReportBuilder() {
    }

    public static List<Region> buildPages(List<DonationRankingRow> rankingRows) {
        List<DonationRankingRow> rows = rankingRows == null ? List.of() : List.copyOf(rankingRows);
        int pageCount = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage();
            addText(page, "捐　款　排　行　榜", 0, 53, PAGE_WIDTH, 42, 28, Pos.CENTER, true);
            addRow(
                    page,
                    TABLE_Y,
                    new String[]{"名次", "姓名", "電腦編號", "捐款總金額", "總筆數"},
                    true
            );

            int from = pageIndex * ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + ROWS_PER_PAGE);
            double y = TABLE_Y + ROW_HEIGHT;
            for (int index = from; index < to; index++) {
                DonationRankingRow row = rows.get(index);
                addRow(
                        page,
                        y,
                        new String[]{
                                String.valueOf(row.rank()),
                                safe(row.memberName()),
                                Util.stringFormat(row.memberId()),
                                NumberFormat.getIntegerInstance().format(row.totalAmount()),
                                String.valueOf(row.totalCount())
                        },
                        false
                );
                y += ROW_HEIGHT;
            }

            addText(
                    page,
                    "Page " + (pageIndex + 1) + " of " + pageCount,
                    570,
                    1052,
                    170,
                    24,
                    11,
                    Pos.CENTER_RIGHT,
                    false
            );
            pages.add(page);
        }
        return pages;
    }

    private static Pane createPage() {
        Pane page = new Pane();
        page.setPrefSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMinSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setMaxSize(PAGE_WIDTH, PAGE_HEIGHT);
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");
        return page;
    }

    private static void addRow(Pane page, double y, String[] values, boolean header) {
        HBox row = new HBox(0);
        row.setPrefSize(tableWidth(), ROW_HEIGHT);
        row.setMinSize(tableWidth(), ROW_HEIGHT);
        row.setMaxSize(tableWidth(), ROW_HEIGHT);
        row.setStyle("-fx-border-color: #202020; -fx-border-width: 1 0 0 1;");

        for (int index = 0; index < values.length; index++) {
            Pos alignment = header || index == 0 || index == 2
                    ? Pos.CENTER
                    : index >= 3 ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
            Label cell = new Label(safe(values[index]));
            cell.setFont(Font.font(REPORT_FONT_FAMILY, FontWeight.BOLD, header ? 16 : 15));
            cell.setAlignment(alignment);
            cell.setPadding(new Insets(2, 7, 2, 7));
            cell.setPrefSize(COLUMN_WIDTHS[index], ROW_HEIGHT);
            cell.setMinSize(COLUMN_WIDTHS[index], ROW_HEIGHT);
            cell.setMaxSize(COLUMN_WIDTHS[index], ROW_HEIGHT);
            cell.setStyle("-fx-font-weight: bold; -fx-border-color: #202020; -fx-border-width: 0 1 1 0;");
            row.getChildren().add(cell);
        }
        row.relocate(TABLE_X, y);
        page.getChildren().add(row);
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
        Label label = new Label(safe(text));
        label.setFont(Font.font(
                REPORT_FONT_FAMILY,
                bold ? FontWeight.BOLD : FontWeight.NORMAL,
                fontSize
        ));
        label.setAlignment(alignment);
        label.setPrefSize(width, height);
        label.setMinSize(width, height);
        label.setMaxSize(width, height);
        label.relocate(x, y);
        parent.getChildren().add(label);
        return label;
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
        return value == null ? "" : value;
    }
}
