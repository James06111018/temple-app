package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.print.PageOrientation;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.shape.Rectangle;
import tw.org.il.dongsheng.templeapp.model.LightRegistrationReportRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public final class LightManagementReportBuilder {
    private static final double PORTRAIT_WIDTH = 794;
    private static final double PORTRAIT_HEIGHT = 1123;
    private static final double LANDSCAPE_WIDTH = 1123;
    private static final double LANDSCAPE_HEIGHT = 794;
    private static final String APP_TITLE = ResourceBundle
            .getBundle("tw.org.il.dongsheng.templeapp.strings")
            .getString("app.title");
    private static final String PRAYER_FONT_FAMILY = resolvePrayerFontFamily();

    private LightManagementReportBuilder() {
    }

    public static List<Region> buildTaiSuiLabelPages(List<LightRegistrationReportRow> rows) {
        return buildLabelPages(rows, 5, 14, false);
    }

    public static List<Region> buildLightLabelPages(List<LightRegistrationReportRow> rows) {
        return buildLabelPages(rows, 4, 16, true);
    }

    public static List<Region> buildTaiSuiPrayerPages(
            List<LightRegistrationReportRow> rows,
        Map<String, String> prayerSettings
    ) {
        List<Region> pages = new ArrayList<>();
        for (LightRegistrationReportRow row : rows) {
            VBox page = createPage(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, Insets.EMPTY);
            page.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            Pane canvas = new Pane();
            canvas.setPrefSize(LANDSCAPE_WIDTH - 4, LANDSCAPE_HEIGHT - 4);
            canvas.setMinSize(LANDSCAPE_WIDTH - 4, LANDSCAPE_HEIGHT - 4);
            canvas.setMaxSize(LANDSCAPE_WIDTH - 4, LANDSCAPE_HEIGHT - 4);

            String honorific = "男".equals(row.gender()) ? "信士" : "信女";
            String star = safe(prayerSettings.get("tai_sui_star"));

            // TITLE
            addVerticalCentered(canvas, "安奉值年太歲疏文", 1030, 32);
            addHorizontal(canvas, row.lightNumber(), 970, 680, 28);

            addVertical(canvas, "伏  以", 950, 126, 20);
            addVertical(canvas, "太歲慈光  兆民咸沾膏澤", 915, 58, 20);
            addVertical(canvas, "星君普度  萬類端賴陶鎔", 880, 58, 20);
            addVertical(canvas, "謹  據", 845, 108, 20);
            addVertical(canvas, "中華民國" + safe(row.address()), 810, 80, 20);
            addVertical(canvas, "吉宅居住 沐恩" + honorific, 775, 570, 20);

            addVertical(canvas, "誠惶誠恐稽首頓首謹為安奉", 740, 80, 20);
            addVertical(canvas, "值年太歲星君之福事虔備香燈茶果天金長錢香楮財帛之儀", 705, 58, 20);
            addVertical(canvas, "修立疏文一封敢敬獻於", 670, 58, 20);
            addVertical(canvas, "值年太歲 " + star + " 星君 寶座前", 635, 58, 20);
            addVertical(
                    canvas,
                    "恭申意者窃念" + honorific + safe(row.name()) + agePrayerText(row),
                    600,
                    80,
                    20
            );
            addVertical(canvas, birthPrayerText(row), 565, 250, 20);

            addVertical(canvas, "本年命宮星辰運限歲煞行沖總屬", 495, 80, 20);
            addVertical(canvas, "太歲星君之所主宰", 460, 80, 20);
            addVertical(canvas, "恭  就 " + APP_TITLE + "  安奉", 425, 108, 20);
            addVertical(canvas, "值年太歲星君", 390, 58, 20);

            addVertical(canvas, "伏  祈", 355, 152, 20);
            addVertical(canvas, "星光垂佑", 320, 58, 20);
            addVertical(canvas, "君澤宏施", 285, 58, 20);
            addVertical(
                    canvas,
                    "從此千災而掃滅百病以安全元辰光彩運限亨通無任懇禱之至",
                    245,
                    80,
                    20
            );
            addVertical(canvas, "謹拜疏奉聞", 205, 95, 20);
            addVertical(canvas, "天運", 165, 58, 20);
            addVertical(canvas, establishedPrayerDate(prayerSettings), 165, 118, 20);
            addVertical(canvas, "沐恩" + honorific + row.name() + "  九叩", 165, 418, 20);
            addVertical(canvas, "上申", 125, 58, 20);

            page.getChildren().add(canvas);
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildLightPrayerPages(List<LightRegistrationReportRow> rows) {
        int rowsPerPage = 13;
        int pageCount = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        List<Region> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * rowsPerPage;
            int to = Math.min(rows.size(), from + rowsPerPage);
            VBox page = createPage(PORTRAIT_WIDTH, PORTRAIT_HEIGHT, Insets.EMPTY);
            page.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            Pane canvas = new Pane();
            canvas.setPrefSize(PORTRAIT_WIDTH - 4, PORTRAIT_HEIGHT - 4);
            canvas.setMinSize(PORTRAIT_WIDTH - 4, PORTRAIT_HEIGHT - 4);
            canvas.setMaxSize(PORTRAIT_WIDTH - 4, PORTRAIT_HEIGHT - 4);

            addVertical(canvas, APP_TITLE + " 善信點光明燈總疏文", 724, 95, 26);

            GridPane table = new GridPane();
            table.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            table.setHgap(0);
            table.setVgap(0);
            table.relocate(48, 58);

            double dataColumnWidth = 46;
            double headingColumnWidth = 54;
            double[] rowHeights = {44, 140, 220, 80, 480};
            String[] headings = {"編號", "姓   名", "出 生 年 月 日 時", "年齡", "地          址"};
            for (int rowIndex = 0; rowIndex < headings.length; rowIndex++) {
                table.add(verticalReportCell(
                        headings[rowIndex],
                        headingColumnWidth,
                        rowHeights[rowIndex],
                        15,
                        true,
                        false,
                        true
                ), rowsPerPage, rowIndex);
            }

            for (int localIndex = 0; localIndex < rowsPerPage; localIndex++) {
                int tableColumn = rowsPerPage - localIndex - 1;
                int dataIndex = from + localIndex;
                LightRegistrationReportRow row = dataIndex < to ? rows.get(dataIndex) : null;
                String[] values = row == null
                        ? new String[]{"", "", "", "", ""}
                        : new String[]{
                                String.valueOf(dataIndex + 1),
                                safe(row.name()),
                                lightPrayerBirthText(row),
                                row.age() == null ? "" : row.age() + "\n歲",
                                safe(row.address())
                        };
                double[] fontSizes = {15, 24, 16, 16, 16};
                for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                    table.add(verticalReportCell(
                            values[rowIndex],
                            dataColumnWidth,
                            rowHeights[rowIndex],
                            fontSizes[rowIndex],
                            false,
                            rowIndex == 1 || rowIndex == 4,
                            rowIndex != 3
                    ), tableColumn, rowIndex);
                }
            }

            canvas.getChildren().add(table);
            page.getChildren().add(canvas);
            pages.add(page);
        }
        return pages;
    }

    private static List<Region> buildLabelPages(
            List<LightRegistrationReportRow> rows,
            int columns,
            int rowsPerPage,
            boolean bordered
    ) {
        List<LightRegistrationReportRow> orderedRows = new ArrayList<>(rows);
        orderedRows.sort(Comparator
                .comparing((LightRegistrationReportRow row) -> lightNumberPrefix(row.lightNumber()))
                .thenComparingInt(row -> lightNumberSerial(row.lightNumber()))
                .thenComparing(row -> safe(row.lightNumber())));

        int pageSize = columns * rowsPerPage;
        int pageCount = Math.max(1, (orderedRows.size() + pageSize - 1) / pageSize);
        List<Region> pages = new ArrayList<>();
        double horizontalPadding = bordered ? 39 : 41;
        double topPadding = bordered ? 56 : 54;
        double cellWidth = bordered ? 178 : 132;
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            VBox page = createPage(
                    PORTRAIT_WIDTH,
                    PORTRAIT_HEIGHT,
                    new Insets(topPadding, horizontalPadding, 38, horizontalPadding)
            );
            page.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            GridPane grid = new GridPane();
            grid.setAlignment(Pos.TOP_LEFT);
            grid.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            grid.setHgap(bordered ? 2 : 8);
            grid.setVgap(bordered ? 2 : 6);
            for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
                ColumnConstraints constraints = new ColumnConstraints(cellWidth, cellWidth, cellWidth);
                grid.getColumnConstraints().add(constraints);
            }

            int from = pageIndex * pageSize;
            int to = Math.min(orderedRows.size(), from + pageSize);
            for (int index = from; index < to; index++) {
                LightRegistrationReportRow row = orderedRows.get(index);
                int localIndex = index - from;
                VBox cell = labelCell(row, bordered);
                cell.setPrefSize(
                        cellWidth,
                        bordered ? 61 : 67
                );
                cell.setMinWidth(cellWidth);
                cell.setMaxWidth(cellWidth);
                int columnIndex = localIndex % columns;
                int rowIndex = localIndex / columns;
                grid.add(cell, columnIndex, rowIndex);
            }
            page.getChildren().add(grid);
            pages.add(page);
        }
        return pages;
    }

    private static VBox labelCell(LightRegistrationReportRow row, boolean bordered) {
        VBox cell = new VBox(bordered ? 1 : 3);
        cell.setAlignment(Pos.CENTER);
        if (bordered) {
            cell.setStyle("-fx-border-color: #202020; -fx-border-width: 1;");
            cell.getChildren().addAll(
                    label(safe(row.name()), 22, true),
                    label(safe(row.lightNumber()), 13, true)
            );
        } else {
            String honorific = "男".equals(row.gender()) ? "信士" : "信女";
            cell.getChildren().addAll(
                    label(honorific + "　" + safe(row.lightNumber()), 12, true),
                    label(safe(row.name()), 23, true)
            );
        }
        return cell;
    }

    private static VBox createPage(double width, double height, Insets padding) {
        VBox page = new VBox(0);
        page.setPrefSize(width, height);
        page.setMinSize(width, height);
        page.setMaxSize(width, height);
        page.setPadding(padding);
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");
        PrintPreview.setPageOrientation(
                page,
                width > height ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT
        );
        return page;
    }

    private static void addVertical(
            Pane canvas,
            String text,
            double x,
            double y,
            double size
    ) {
        Text value = prayerText(verticalText(text), size);
        value.setTextAlignment(TextAlignment.CENTER);
        value.setLineSpacing(-5);
        value.relocate(x, y);
        canvas.getChildren().add(value);
    }

    private static void addVerticalCentered(
            Pane canvas,
            String text,
            double x,
            double size
    ) {
        Text value = prayerText(verticalText(text), size);
        value.setTextAlignment(TextAlignment.CENTER);
        value.setLineSpacing(-7);
        StackPane container = new StackPane(value);
        container.setAlignment(Pos.CENTER);
        container.setPrefSize(58, LANDSCAPE_HEIGHT - 4);
        container.setMinSize(58, LANDSCAPE_HEIGHT - 4);
        container.setMaxSize(58, LANDSCAPE_HEIGHT - 4);
        container.relocate(x, 0);
        canvas.getChildren().add(container);
    }

    private static void addHorizontal(
            Pane canvas,
            String text,
            double x,
            double y,
            double size
    ) {
        Text value = prayerText(text, size);
        value.relocate(x, y);
        canvas.getChildren().add(value);
    }

    private static String verticalText(String text) {
        return safe(text).codePoints()
                .mapToObj(codePoint -> Character.isWhitespace(codePoint)
                        ? ""
                        : new String(Character.toChars(codePoint)))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static StackPane verticalReportCell(
            String text,
            double width,
            double height,
            double fontSize,
            boolean heading,
            boolean topAligned,
            boolean vertical
    ) {
        int lineCount = Math.max(1, safe(text).codePointCount(0, safe(text).length()));
        double fittingSize = (height - 8 + (3 * (lineCount - 1))) / (1.2 * lineCount);
        double effectiveFontSize = vertical
                ? Math.min(fontSize, Math.max(9, fittingSize))
                : fontSize;
        Text value = prayerText(vertical ? verticalText(text) : safe(text), effectiveFontSize);
        value.setTextAlignment(TextAlignment.CENTER);
        value.setLineSpacing(-3);

        StackPane cell = new StackPane(value);
        cell.setAlignment(topAligned ? Pos.TOP_CENTER : Pos.CENTER);
        if (topAligned) {
            cell.setPadding(new Insets(10, 0, 0, 0));
        }
        cell.setPrefSize(width, height);
        cell.setMinSize(width, height);
        cell.setMaxSize(width, height);
        cell.setClip(new Rectangle(width, height));
        cell.setStyle(
                "-fx-background-color: white;"
                        + "-fx-border-color: #202020;"
                        + "-fx-border-width: 0.6;"
        );
        if (heading) {
            value.setStrokeWidth(0.55);
        }
        return cell;
    }

    private static Label label(String text, double size, boolean bold) {
        Label label = new Label(safe(text));
        label.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        return label;
    }

    private static Text prayerText(String text, double size) {
        Text value = new Text(safe(text));
        value.setFont(Font.font(PRAYER_FONT_FAMILY, FontWeight.BOLD, size));
        value.setFill(Color.BLACK);
        value.setStroke(Color.BLACK);
        value.setStrokeWidth(size >= 24 ? 0.65 : 0.45);
        return value;
    }

    private static String resolvePrayerFontFamily() {
        List<String> available = Font.getFamilies();
        String[] preferred = {"BiauKai", "Kaiti TC", "DFKai-SB", "KaiTi", "標楷體", "STKaiti"};
        for (String family : preferred) {
            if (available.contains(family)) {
                return family;
            }
        }
        return "Serif";
    }

    private static String birthPrayerText(LightRegistrationReportRow row) {
        String birthday = safe(row.lunarBirthDate());
        if (birthday.isEmpty()) {
            birthday = safe(row.birthDate());
        }
        String[] parts = birthday.split("[^0-9]+");
        String month = parts.length >= 2 ? lunarMonthText(parts[parts.length - 2]) : "";
        String day = parts.length >= 1 ? lunarDayText(parts[parts.length - 1]) : "";
        String year = safe(row.zodiacYear());
        if (year.isEmpty() && parts.length >= 3) {
            year = trimLeadingZero(parts[parts.length - 3]);
        }
        return "本命 " + year + " 年 " + month + " 月 " + day + " 日 "
                + safe(row.birthTime()) + "時 建生";
    }

    private static String agePrayerText(LightRegistrationReportRow row) {
        return row.age() == null ? "" : "     庚 " + chineseNumber(row.age()) + " 歲";
    }

    private static String chineseNumber(int value) {
        if (value < 0 || value > 999) {
            return String.valueOf(value);
        }
        if (value == 0) {
            return "零";
        }

        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        StringBuilder result = new StringBuilder();
        int hundreds = value / 100;
        int remainder = value % 100;
        if (hundreds > 0) {
            result.append(digits[hundreds]).append("百");
            if (remainder > 0 && remainder < 10) {
                result.append("零");
            }
        }

        int tens = remainder / 10;
        int ones = remainder % 10;
        if (tens > 0) {
            if (tens > 1 || hundreds > 0) {
                result.append(digits[tens]);
            }
            result.append("十");
        }
        if (ones > 0) {
            result.append(digits[ones]);
        }
        return result.toString();
    }

    private static String establishedPrayerDate(Map<String, String> settings) {
        String year = safe(settings.get("tai_sui_year"));
        String month = safe(settings.get("tai_sui_month"));
        String day = safe(settings.get("tai_sui_day"));
        return year + " 年 " + month + " 月 " + day + " 日";
    }

    private static String trimLeadingZero(String value) {
        return value == null ? "" : value.replaceFirst("^0+(?!$)", "");
    }

    private static String lunarMonthText(String value) {
        Integer month = parseNumber(value);
        if (month == null || month < 1 || month > 12) {
            return trimLeadingZero(value);
        }
        return switch (month) {
            case 1 -> "正";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            case 11 -> "十一";
            case 12 -> "十二";
            default -> "";
        };
    }

    private static String lunarDayText(String value) {
        Integer day = parseNumber(value);
        if (day == null || day < 1 || day > 30) {
            return trimLeadingZero(value);
        }
        String[] days = {
                "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
                "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
        };
        return days[day];
    }

    private static Integer parseNumber(String value) {
        try {
            return Integer.valueOf(trimLeadingZero(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String lightPrayerBirthText(LightRegistrationReportRow row) {
        String birthday = safe(row.lunarBirthDate());
        if (birthday.isEmpty()) {
            birthday = safe(row.birthDate());
        }
        String[] parts = birthday.split("[^0-9]+");
        String year = safe(row.zodiacYear());
        if (year.isEmpty() && parts.length >= 3) {
            year = trimLeadingZero(parts[parts.length - 3]);
        }
        String month = parts.length >= 2 ? lunarMonthText(parts[parts.length - 2]) : "";
        String day = parts.length >= 1 ? lunarDayText(parts[parts.length - 1]) : "";
        String birthTime = safe(row.birthTime());
        return year + "年" + month + "月" + day + "日"
                + (birthTime.isEmpty() ? "" : birthTime + "時");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String lightNumberPrefix(String lightNumber) {
        String value = safe(lightNumber).trim();
        int index = 0;
        while (index < value.length() && !Character.isDigit(value.charAt(index))) {
            index++;
        }
        return value.substring(0, index);
    }

    private static int lightNumberSerial(String lightNumber) {
        String value = safe(lightNumber).replaceAll("\\D", "");
        if (value.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }
}
