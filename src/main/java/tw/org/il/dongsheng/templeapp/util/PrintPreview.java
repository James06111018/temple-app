package tw.org.il.dongsheng.templeapp.util;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

public final class PrintPreview {
    private static final String PAGE_ORIENTATION_KEY = "print.pageOrientation";

    private PrintPreview() {
    }

    static void setPageOrientation(Node page, PageOrientation orientation) {
        page.getProperties().put(PAGE_ORIENTATION_KEY, orientation);
    }

    public static void show(Window owner, String title, List<? extends Region> pages) {
        if (pages == null || pages.isEmpty()) {
            return;
        }

        List<Pane> previewPages = new ArrayList<>();
        List<Group> scaledPages = new ArrayList<>();
        for (Region page : pages) {
            Group scaledPage = new Group(page);
            Pane holder = new Pane(scaledPage);
            holder.setStyle("-fx-background-color: transparent;");
            previewPages.add(holder);
            scaledPages.add(scaledPage);
        }

        StackPane currentPageContainer = new StackPane();
        currentPageContainer.setAlignment(Pos.TOP_CENTER);
        currentPageContainer.getChildren().addAll(previewPages);

        VBox pageContainer = new VBox();
        pageContainer.setAlignment(Pos.TOP_CENTER);
        pageContainer.setPadding(new Insets(18));
        pageContainer.setStyle("-fx-background-color: #777777;");
        pageContainer.getChildren().add(currentPageContainer);

        ScrollPane scrollPane = new ScrollPane(pageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        Button firstPageButton = new Button("|<");
        Button previousPageButton = new Button("<");
        TextField pageField = new TextField("1");
        pageField.setPrefColumnCount(3);
        pageField.setMaxWidth(64);
        pageField.setAlignment(Pos.CENTER);
        Label pageTotalLabel = new Label("/ " + pages.size() + "頁");
        Button nextPageButton = new Button(">");
        Button lastPageButton = new Button(">|");

        Spinner<Integer> zoomSpinner = new Spinner<>(25, 200, 100, 5);
        zoomSpinner.setEditable(true);
        zoomSpinner.setPrefWidth(88);

        Button printButton = new Button("列印");
        Button closeButton = new Button("關閉");
        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, javafx.scene.layout.Priority.ALWAYS);
        HBox toolbar = new HBox(
                10,
                firstPageButton,
                previousPageButton,
                new Separator(Orientation.VERTICAL),
                new Label("第"),
                pageField,
                pageTotalLabel,
                new Separator(Orientation.VERTICAL),
                nextPageButton,
                lastPageButton,
                new Separator(Orientation.VERTICAL),
                new Label("縮放"),
                zoomSpinner,
                new Label("%"),
                toolbarSpacer,
                printButton,
                closeButton
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setStyle("-fx-background-color: #eeeeee; -fx-border-color: #b0b0b0; -fx-border-width: 0 0 1 0;");

        VBox root = new VBox(toolbar, scrollPane);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        boolean landscape = pageOrientation(pages.get(0)) == PageOrientation.LANDSCAPE;
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(root, landscape ? 1180 : 930, landscape ? 850 : 900));
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        IntegerProperty currentPage = new SimpleIntegerProperty(0);
        Runnable refreshPage = () -> {
            int selectedPage = currentPage.get();
            for (int index = 0; index < previewPages.size(); index++) {
                boolean selected = index == selectedPage;
                previewPages.get(index).setVisible(selected);
                previewPages.get(index).setManaged(selected);
            }
            pageField.setText(String.valueOf(selectedPage + 1));
            firstPageButton.setDisable(selectedPage == 0);
            previousPageButton.setDisable(selectedPage == 0);
            nextPageButton.setDisable(selectedPage == pages.size() - 1);
            lastPageButton.setDisable(selectedPage == pages.size() - 1);
            Platform.runLater(() -> {
                scrollPane.setHvalue(0);
                scrollPane.setVvalue(0);
            });
        };
        Runnable applyPageField = () -> {
            try {
                int requestedPage = Integer.parseInt(pageField.getText().trim());
                currentPage.set(Math.max(0, Math.min(pages.size() - 1, requestedPage - 1)));
                refreshPage.run();
            } catch (NumberFormatException exception) {
                pageField.setText(String.valueOf(currentPage.get() + 1));
            }
        };
        Runnable applyZoom = () -> {
            double zoom = zoomSpinner.getValue() / 100.0;
            for (int index = 0; index < pages.size(); index++) {
                Region page = pages.get(index);
                double width = pageDimension(page.getPrefWidth(), page.getLayoutBounds().getWidth());
                double height = pageDimension(page.getPrefHeight(), page.getLayoutBounds().getHeight());
                scaledPages.get(index).getTransforms().setAll(new Scale(zoom, zoom, 0, 0));
                previewPages.get(index).setPrefSize(width * zoom, height * zoom);
                previewPages.get(index).setMinSize(width * zoom, height * zoom);
                previewPages.get(index).setMaxSize(width * zoom, height * zoom);
            }
            currentPageContainer.requestLayout();
        };
        Runnable applyZoomField = () -> {
            try {
                int requestedZoom = Integer.parseInt(zoomSpinner.getEditor().getText().trim());
                int zoom = Math.max(25, Math.min(200, requestedZoom));
                int previousZoom = zoomSpinner.getValue();
                zoomSpinner.getValueFactory().setValue(zoom);
                zoomSpinner.getEditor().setText(String.valueOf(zoom));
                if (zoom == previousZoom) {
                    applyZoom.run();
                }
            } catch (NumberFormatException exception) {
                zoomSpinner.getEditor().setText(String.valueOf(zoomSpinner.getValue()));
            }
        };

        firstPageButton.setOnAction(event -> currentPage.set(0));
        previousPageButton.setOnAction(event -> currentPage.set(Math.max(0, currentPage.get() - 1)));
        nextPageButton.setOnAction(event -> currentPage.set(Math.min(pages.size() - 1, currentPage.get() + 1)));
        lastPageButton.setOnAction(event -> currentPage.set(pages.size() - 1));
        currentPage.addListener((observable, oldValue, newValue) -> refreshPage.run());
        pageField.setOnAction(event -> applyPageField.run());
        pageField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) {
                applyPageField.run();
            }
        });
        zoomSpinner.valueProperty().addListener((observable, oldValue, newValue) -> applyZoom.run());
        zoomSpinner.getEditor().setOnAction(event -> applyZoomField.run());
        zoomSpinner.getEditor().focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) {
                applyZoomField.run();
            }
        });

        applyZoom.run();
        refreshPage.run();
        printButton.setOnAction(event -> print(stage, pages));
        closeButton.setOnAction(event -> stage.close());
        stage.showAndWait();
    }

    private static void print(Window owner, List<? extends Node> pages) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(owner)) {
            return;
        }

        boolean succeeded = true;
        for (Node page : pages) {
            page.applyCss();
            page.autosize();
            double width = page.getBoundsInParent().getWidth();
            double height = page.getBoundsInParent().getHeight();
            PageLayout layout = job.getPrinter().createPageLayout(
                    Paper.A4,
                    pageOrientation(page),
                    javafx.print.Printer.MarginType.DEFAULT
            );
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

    private static PageOrientation pageOrientation(Node page) {
        Object configured = page.getProperties().get(PAGE_ORIENTATION_KEY);
        if (configured instanceof PageOrientation orientation) {
            return orientation;
        }
        if (page instanceof Region region) {
            return region.getPrefWidth() > region.getPrefHeight()
                    ? PageOrientation.LANDSCAPE
                    : PageOrientation.PORTRAIT;
        }
        return page.getLayoutBounds().getWidth() > page.getLayoutBounds().getHeight()
                ? PageOrientation.LANDSCAPE
                : PageOrientation.PORTRAIT;
    }

    private static double pageDimension(double preferred, double measured) {
        return preferred > 0 ? preferred : Math.max(1, measured);
    }
}
