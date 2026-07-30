package tw.org.il.dongsheng.templeapp.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

public final class PrintPreview {
    private PrintPreview() {
    }

    public static void show(Window owner, String title, List<? extends Region> pages) {
        if (pages == null || pages.isEmpty()) {
            return;
        }

        VBox pageContainer = new VBox(18);
        pageContainer.setAlignment(Pos.TOP_CENTER);
        pageContainer.setPadding(new Insets(18));
        pageContainer.setStyle("-fx-background-color: #777777;");
        pageContainer.getChildren().addAll(pages);

        ScrollPane scrollPane = new ScrollPane(pageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        Label pageCountLabel = new Label("共 " + pages.size() + " 頁");
        Button printButton = new Button("列印");
        Button closeButton = new Button("關閉");
        HBox toolbar = new HBox(10, printButton, pageCountLabel, closeButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setStyle("-fx-background-color: #eeeeee; -fx-border-color: #b0b0b0; -fx-border-width: 0 0 1 0;");

        VBox root = new VBox(toolbar, scrollPane);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(root, 930, 900));
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        printButton.setOnAction(event -> print(stage, pages));
        closeButton.setOnAction(event -> stage.close());
        stage.showAndWait();
    }

    private static void print(Window owner, List<? extends Node> pages) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(owner)) {
            return;
        }

        PageLayout layout = job.getPrinter().createPageLayout(
                Paper.A4,
                PageOrientation.PORTRAIT,
                javafx.print.Printer.MarginType.DEFAULT
        );

        boolean succeeded = true;
        for (Node page : pages) {
            page.applyCss();
            page.autosize();
            double width = page.getBoundsInParent().getWidth();
            double height = page.getBoundsInParent().getHeight();
            double scaleFactor = Math.min(
                    layout.getPrintableWidth() / Math.max(width, 1),
                    layout.getPrintableHeight() / Math.max(height, 1)
            );
            Scale scale = new Scale(scaleFactor, scaleFactor);
            page.getTransforms().add(scale);
            try {
                succeeded = job.printPage(layout, page);
            } finally {
                page.getTransforms().remove(scale);
            }
            if (!succeeded) {
                break;
            }
        }

        if (succeeded) {
            job.endJob();
        } else {
            job.cancelJob();
            AlertDialog.showError("列印", "列印失敗");
        }
    }
}
