package tw.org.il.dongsheng.templeapp.util;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import tw.org.il.dongsheng.templeapp.model.DonationDetailRow;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class DonationDetailsExcelPreview {
    private static final NumberFormat AMOUNT_FORMAT = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    private DonationDetailsExcelPreview() {
    }

    public static void show(Window owner, List<DonationDetailRow> sourceRows) {
        List<DonationDetailRow> rows = sourceRows == null ? List.of() : sourceRows;
        Stage stage = new Stage();
        stage.setTitle("捐款明細 Excel 預覽");
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        TableView<DonationDetailRow> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        addColumn(table, "收據編號", 110, row -> DonationDetailsExcelExporter.formatReceiptNo(row.receiptNo()));
        addColumn(table, "日期", 100, DonationDetailRow::donationDate);
        addColumn(table, "姓名", 150, DonationDetailRow::name);
        addColumn(table, "地址", 330, DonationDetailRow::address);
        addColumn(table, "電話", 150, DonationDetailRow::phone);
        addColumn(table, "款項類別", 150, DonationDetailRow::category);
        TableColumn<DonationDetailRow, String> amountColumn = addColumn(
                table,
                "金額",
                110,
                row -> row.amount() == null ? "0" : AMOUNT_FORMAT.format(row.amount())
        );
        amountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        addColumn(table, "摘要", 180, DonationDetailRow::summary);
        addColumn(table, "捐款備註", 220, DonationDetailRow::donationNote);
        addColumn(table, "其他註記", 220, DonationDetailRow::description);
        addColumn(table, "補登號碼", 130, DonationDetailRow::supplementNo);

        Button saveButton = new Button("另存新檔");
        saveButton.getStyleClass().add("btn-orange");
        Button closeButton = new Button("關閉");
        closeButton.setOnAction(event -> stage.close());
        saveButton.setOnAction(event -> save(stage, rows));

        HBox actions = new HBox(12, saveButton, closeButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12));

        BorderPane root = new BorderPane(table);
        root.setBottom(actions);
        Scene scene = new Scene(root, 1220, 720);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static TableColumn<DonationDetailRow, String> addColumn(
            TableView<DonationDetailRow> table,
            String title,
            double width,
            Function<DonationDetailRow, String> valueProvider
    ) {
        TableColumn<DonationDetailRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(safe(valueProvider.apply(cell.getValue()))));
        table.getColumns().add(column);
        return column;
    }

    private static void save(Window owner, List<DonationDetailRow> rows) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("儲存捐款明細");
        chooser.setInitialFileName("捐款明細.xlsx");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 活頁簿 (*.xlsx)", "*.xlsx")
        );
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            target = new File(target.getParentFile(), target.getName() + ".xlsx");
        }
        try {
            DonationDetailsExcelExporter.export(target, rows);
            AlertDialog.showInfo("匯出EXCEL", "匯出完成：\n" + target.getAbsolutePath());
        } catch (IOException exception) {
            AlertDialog.showError("匯出EXCEL", "匯出失敗：" + exception.getMessage());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
