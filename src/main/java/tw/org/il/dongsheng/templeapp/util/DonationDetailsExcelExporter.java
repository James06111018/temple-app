package tw.org.il.dongsheng.templeapp.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import tw.org.il.dongsheng.templeapp.model.DonationDetailRow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public final class DonationDetailsExcelExporter {
    private static final String[] HEADERS = {
            "收據編號", "日期", "姓名", "地址", "電話", "款項類別", "金額", "摘要",
            "捐款備註", "其他註記", "補登號碼"
    };

    private DonationDetailsExcelExporter() {
    }

    public static void export(File target, List<DonationDetailRow> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("捐款明細");
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);
            CellStyle numberStyle = numberStyle(workbook);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(25);
            for (int column = 0; column < HEADERS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS[column]);
                cell.setCellStyle(headerStyle);
            }

            List<DonationDetailRow> exportRows = rows == null ? List.of() : rows;
            for (int index = 0; index < exportRows.size(); index++) {
                DonationDetailRow source = exportRows.get(index);
                Row row = sheet.createRow(index + 1);
                row.setHeightInPoints(22);
                int column = 0;
                setText(row, column++, formatReceiptNo(source.receiptNo()), textStyle);
                setText(row, column++, source.donationDate(), textStyle);
                setText(row, column++, source.name(), textStyle);
                setText(row, column++, source.address(), textStyle);
                setText(row, column++, source.phone(), textStyle);
                setText(row, column++, source.category(), textStyle);
                setNumber(row, column++, source.amount(), numberStyle);
                setText(row, column++, source.summary(), textStyle);
                setText(row, column++, source.donationNote(), textStyle);
                setText(row, column++, source.description(), textStyle);
                setText(row, column, source.supplementNo(), textStyle);
            }

            int[] widths = {14, 12, 18, 46, 18, 20, 14, 24, 30, 30, 16};
            for (int column = 0; column < widths.length; column++) {
                sheet.setColumnWidth(column, widths[column] * 256);
            }
            if (!exportRows.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, exportRows.size(), 0, HEADERS.length - 1));
            }

            try (FileOutputStream output = new FileOutputStream(target)) {
                workbook.write(output);
            }
        }
    }

    public static String formatReceiptNo(String value) {
        String receiptNo = value == null ? "" : value.trim();
        if (receiptNo.matches("\\d+") && receiptNo.length() < 6) {
            return "0".repeat(6 - receiptNo.length()) + receiptNo;
        }
        return receiptNo;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = baseStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        return style;
    }

    private static CellStyle textStyle(XSSFWorkbook workbook) {
        CellStyle style = baseStyle(workbook);
        style.setWrapText(true);
        return style;
    }

    private static CellStyle numberStyle(XSSFWorkbook workbook) {
        CellStyle style = baseStyle(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private static CellStyle baseStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private static void setText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void setNumber(Row row, int column, Integer value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value);
        cell.setCellStyle(style);
    }
}
