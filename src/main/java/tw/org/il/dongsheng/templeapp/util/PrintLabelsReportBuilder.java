package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Pos;
import javafx.print.PageOrientation;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.util.ArrayList;
import java.util.List;

public final class PrintLabelsReportBuilder {
    private static final double PAGE_WIDTH = 794;
    private static final double PAGE_HEIGHT = 1123;
    private static final double LABEL_SIDE_MARGIN = 22;
    private static final double LABEL_VERTICAL_MARGIN = 20;
    private static final double LABEL_COLUMN_GAP = 34;
    private static final double LABEL_ROW_GAP = 8;
    private static final int HORIZONTAL_LABEL_COLUMNS = 2;
    private static final int HORIZONTAL_LABEL_ROWS = 7;
    private static final double HORIZONTAL_LABEL_LEFT = LABEL_SIDE_MARGIN;
    private static final double HORIZONTAL_LABEL_TOP = LABEL_VERTICAL_MARGIN;
    private static final double HORIZONTAL_LABEL_CELL_WIDTH = (
            PAGE_WIDTH - HORIZONTAL_LABEL_LEFT * 2 - LABEL_COLUMN_GAP
    ) / HORIZONTAL_LABEL_COLUMNS;
    private static final double HORIZONTAL_LABEL_CELL_HEIGHT = (
            PAGE_HEIGHT - HORIZONTAL_LABEL_TOP * 2 - LABEL_ROW_GAP * (HORIZONTAL_LABEL_ROWS - 1)
    ) / HORIZONTAL_LABEL_ROWS;
    private static final int HORIZONTAL_LABELS_PER_PAGE = HORIZONTAL_LABEL_COLUMNS * HORIZONTAL_LABEL_ROWS;
    private static final double VERTICAL_PAGE_WIDTH = PAGE_WIDTH;
    private static final double VERTICAL_PAGE_HEIGHT = PAGE_HEIGHT;
    private static final int VERTICAL_LABEL_COLUMNS = 2;
    private static final int VERTICAL_LABEL_ROWS = 7;
    private static final double VERTICAL_LABEL_LEFT = LABEL_SIDE_MARGIN;
    private static final double VERTICAL_LABEL_TOP = LABEL_VERTICAL_MARGIN;
    private static final double VERTICAL_LABEL_CELL_WIDTH =
            (VERTICAL_PAGE_WIDTH - VERTICAL_LABEL_LEFT * 2 - LABEL_COLUMN_GAP) / VERTICAL_LABEL_COLUMNS;
    private static final double VERTICAL_LABEL_CELL_HEIGHT =
            (VERTICAL_PAGE_HEIGHT - VERTICAL_LABEL_TOP * 2 - LABEL_ROW_GAP * (VERTICAL_LABEL_ROWS - 1))
                    / VERTICAL_LABEL_ROWS;
    private static final int VERTICAL_LABELS_PER_PAGE = VERTICAL_LABEL_COLUMNS * VERTICAL_LABEL_ROWS;
    private static final int ROSTER_ROWS_PER_PAGE = 30;
    private static final String REPORT_FONT_FAMILY = resolveReportFontFamily();

    private PrintLabelsReportBuilder() {
    }

    public static List<Region> buildHorizontalLabelPages(List<LightMember> members, int startPosition) {
        return buildLabelPages(members, startPosition, false);
    }

    public static List<Region> buildVerticalLabelPages(List<LightMember> members, int startPosition) {
        return buildLabelPages(members, startPosition, true);
    }

    public static int horizontalLabelSlotsPerPage() {
        return HORIZONTAL_LABELS_PER_PAGE;
    }

    public static int verticalLabelSlotsPerPage() {
        return VERTICAL_LABELS_PER_PAGE;
    }

    public static List<Region> buildRosterPages(List<LightMember> members) {
        List<LightMember> rows = printableMembers(members);
        int pageCount = Math.max(1, (rows.size() + ROSTER_ROWS_PER_PAGE - 1) / ROSTER_ROWS_PER_PAGE);
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage();
            addText(page, "名　冊", 0, 44, PAGE_WIDTH, 36, 26, Pos.CENTER, true);
            addRosterHeader(page);

            int from = pageIndex * ROSTER_ROWS_PER_PAGE;
            int to = Math.min(rows.size(), from + ROSTER_ROWS_PER_PAGE);
            double y = 150;
            for (int index = from; index < to; index++) {
                addRosterRow(page, rows.get(index), y);
                y += 27;
            }
            addFooter(page, pageIndex + 1, pageCount);
            pages.add(page);
        }
        return pages;
    }

    public static List<Region> buildLetterPages(List<LightMember> members) {
        List<Region> pages = new ArrayList<>();
        for (LightMember member : printableMembers(members)) {
            Pane page = createPage();
            addText(page, safe(member.getZipCode()), 170, 755, 430, 28, 17, Pos.CENTER_LEFT, true);
            addText(page, fullAddress(member), 170, 785, 470, 34, 17, Pos.CENTER_LEFT, true);
            addText(page, safe(member.getName()), 205, 845, 260, 50, 28, Pos.CENTER_LEFT, true);
            addText(page, "大德", 500, 845, 105, 50, 25, Pos.CENTER, true);
            addText(page, "*" + memberId(member) + "*", 496, 900, 110, 24, 12, Pos.CENTER, true);
            addText(page, memberId(member), 496, 922, 110, 18, 9, Pos.CENTER, false);
            pages.add(page);
        }
        return pages;
    }

    private static List<Region> buildLabelPages(
            List<LightMember> members,
            int requestedStartPosition,
            boolean vertical
    ) {
        List<LightMember> rows = printableMembers(members);
        int labelsPerPage = vertical ? VERTICAL_LABELS_PER_PAGE : HORIZONTAL_LABELS_PER_PAGE;
        double pageWidth = vertical ? VERTICAL_PAGE_WIDTH : PAGE_WIDTH;
        double pageHeight = vertical ? VERTICAL_PAGE_HEIGHT : PAGE_HEIGHT;
        double left = vertical ? VERTICAL_LABEL_LEFT : HORIZONTAL_LABEL_LEFT;
        double top = vertical ? VERTICAL_LABEL_TOP : HORIZONTAL_LABEL_TOP;
        double cellWidth = vertical ? VERTICAL_LABEL_CELL_WIDTH : HORIZONTAL_LABEL_CELL_WIDTH;
        double cellHeight = vertical ? VERTICAL_LABEL_CELL_HEIGHT : HORIZONTAL_LABEL_CELL_HEIGHT;
        int labelColumns = vertical ? VERTICAL_LABEL_COLUMNS : HORIZONTAL_LABEL_COLUMNS;
        PageOrientation orientation = PageOrientation.PORTRAIT;

        int startPosition = Math.max(1, Math.min(labelsPerPage, requestedStartPosition));
        int leadingBlanks = startPosition - 1;
        int occupiedSlots = leadingBlanks + rows.size();
        int pageCount = Math.max(1, (occupiedSlots + labelsPerPage - 1) / labelsPerPage);
        List<Region> pages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Pane page = createPage(pageWidth, pageHeight, orientation);
            for (int slot = 0; slot < labelsPerPage; slot++) {
                int globalSlot = pageIndex * labelsPerPage + slot;
                int memberIndex = globalSlot - leadingBlanks;
                if (memberIndex < 0 || memberIndex >= rows.size()) {
                    continue;
                }
                int column = slot % labelColumns;
                int row = slot / labelColumns;
                Pane cell = vertical
                        ? verticalLabel(rows.get(memberIndex), cellWidth, cellHeight)
                        : horizontalLabel(rows.get(memberIndex), cellWidth, cellHeight);
                cell.relocate(
                        left + column * (cellWidth + LABEL_COLUMN_GAP),
                        top + row * (cellHeight + LABEL_ROW_GAP)
                );
                page.getChildren().add(cell);
            }
            pages.add(page);
        }
        return pages;
    }

    private static Pane horizontalLabel(LightMember member, double width, double height) {
        Pane cell = fixedPane(width, height);
        addText(cell, safe(member.getZipCode()), 0, 5, 90, 26, 16, Pos.CENTER_LEFT, true);
        addText(cell, "*" + memberId(member) + "*", width - 132, 5, 122, 26, 16, Pos.CENTER_RIGHT, true);
        addText(cell, fullAddress(member), 0, 31, width - 8, 34, 16, Pos.TOP_LEFT, true);
        addText(cell, safe(member.getName()), 0, 70, width - 132, 42, 25, Pos.CENTER_LEFT, true);
        addText(cell, "大德", width - 126, 72, 112, 32, 22, Pos.CENTER, true);
        addText(cell, memberId(member), width - 126, 105, 112, 20, 10, Pos.CENTER, false);
        return cell;
    }

    private static Pane verticalLabel(LightMember member, double width, double height) {
        Pane cell = fixedPane(width, height);

        // Build a narrow vertical label first, then rotate it into the landscape sheet.
        Pane content = fixedPane(height, width);
        String address = fullAddress(member);
        double contentWidth = height;
        double contentHeight = width;

        addText(
                content,
                safe(member.getZipCode()),
                contentWidth * 0.48,
                7,
                contentWidth * 0.48,
                24,
                16,
                Pos.CENTER,
                true
        );
        double addressY = 25;
        addVerticalCharacters(
                content,
                address,
                contentWidth * 0.48,
                addressY,
                contentWidth * 0.48,
                contentHeight - addressY - 4,
                16,
                16,
                true
        );
        addVerticalCharacters(
                content,
                safe(member.getName()),
                2,
                addressY,
                contentWidth * 0.46,
                contentHeight - addressY - 112,
                25,
                25,
                true
        );
        addVerticalCharacters(
                content,
                "大德",
                2,
                contentHeight - 101,
                contentWidth * 0.46,
                48,
                22,
                22,
                true
        );
        addText(
                content,
                memberId(member),
                2,
                contentHeight - 55,
                contentWidth * 0.46,
                20,
                10,
                Pos.CENTER,
                false
        );

        content.setRotate(-90);
        content.relocate((width - height) / 2, (height - width) / 2);
        cell.getChildren().add(content);
        return cell;
    }

    private static Pane fixedPane(double width, double height) {
        Pane pane = new Pane();
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
    }

    private static void addVerticalCharacters(
            Pane parent,
            String text,
            double x,
            double y,
            double width,
            double availableHeight,
            double fontSize,
            double preferredStep,
            boolean bold
    ) {
        int[] codePoints = safe(text).codePoints().toArray();
        if (codePoints.length == 0) {
            return;
        }

        double layoutGlyphHeight = fontSize + 2;
        double renderHeight = fontSize + 10;
        double renderOffset = (renderHeight - layoutGlyphHeight) / 2;
        double step = preferredStep;
        if (codePoints.length > 1) {
            step = Math.min(preferredStep, (availableHeight - layoutGlyphHeight) / (codePoints.length - 1));
        }
        step = Math.max(1, step);

        for (int index = 0; index < codePoints.length; index++) {
            addText(
                    parent,
                    new String(Character.toChars(codePoints[index])),
                    x,
                    y + index * step - renderOffset,
                    width,
                    renderHeight,
                    fontSize,
                    Pos.CENTER,
                    bold
            );
        }
    }

    private static void addRosterHeader(Pane page) {
        addText(page, "電腦編號", 48, 112, 90, 30, 16, Pos.CENTER_LEFT, true);
        addText(page, "名稱", 142, 112, 135, 30, 16, Pos.CENTER_LEFT, true);
        addText(page, "地址", 278, 112, 320, 30, 16, Pos.CENTER_LEFT, true);
        addText(page, "電話", 602, 112, 145, 30, 16, Pos.CENTER_LEFT, true);
        Region line = new Region();
        line.setStyle("-fx-border-color: #202020; -fx-border-width: 0 0 1.2 0;");
        line.setPrefSize(700, 1);
        line.relocate(47, 142);
        page.getChildren().add(line);
    }

    private static void addRosterRow(Pane page, LightMember member, double y) {
        addText(page, memberId(member), 48, y, 90, 25, 15, Pos.CENTER_LEFT, true);
        addText(page, safe(member.getName()), 142, y, 135, 25, 15, Pos.CENTER_LEFT, true);
        addText(page, fullAddress(member), 278, y, 320, 25, 15, Pos.CENTER_LEFT, true);
        addText(page, safe(member.getPhone()), 602, y, 145, 25, 15, Pos.CENTER_LEFT, true);
    }

    private static void addFooter(Pane page, int pageNumber, int pageCount) {
        Region line = new Region();
        line.setStyle("-fx-border-color: #202020; -fx-border-width: 0 0 1.2 0;");
        line.setPrefSize(700, 1);
        line.relocate(47, 1042);
        page.getChildren().add(line);
        addText(
                page,
                "Page " + pageNumber + " of " + pageCount,
                585,
                1052,
                160,
                24,
                12,
                Pos.CENTER_RIGHT,
                false
        );
    }

    private static Pane createPage() {
        return createPage(PAGE_WIDTH, PAGE_HEIGHT, PageOrientation.PORTRAIT);
    }

    private static Pane createPage(double width, double height, PageOrientation orientation) {
        Pane page = new Pane();
        page.setPrefSize(width, height);
        page.setMinSize(width, height);
        page.setMaxSize(width, height);
        page.setStyle("-fx-background-color: white; -fx-border-color: #202020; -fx-border-width: 2;");
        PrintPreview.setPageOrientation(page, orientation);
        return page;
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
        label.setWrapText(false);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        label.setPrefSize(width, height);
        label.setMinSize(width, height);
        label.setMaxSize(width, height);
        label.relocate(x, y);
        parent.getChildren().add(label);
        return label;
    }

    private static List<LightMember> printableMembers(List<LightMember> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .filter(member -> member != null && member.getId() != null)
                .filter(member -> !safe(member.getName()).isBlank())
                .toList();
    }

    private static String memberId(LightMember member) {
        return member == null || member.getId() == null ? "" : Util.stringFormat(member.getId());
    }

    private static String fullAddress(LightMember member) {
        if (member == null) {
            return "";
        }
        String address = safe(member.getAddress()).trim();
        String city = safe(member.getCity()).trim();
        String district = safe(member.getDist()).trim();
        StringBuilder result = new StringBuilder();
        if (!city.isEmpty() && !containsAddressPart(address, city)) {
            result.append(city);
        }
        if (!district.isEmpty() && !containsAddressPart(address, district)) {
            result.append(district);
        }
        result.append(address);
        return result.toString();
    }

    private static boolean containsAddressPart(String address, String part) {
        return address.replace('台', '臺').contains(part.replace('台', '臺'));
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
