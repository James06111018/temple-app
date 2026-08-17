package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.MeritBoxOpening;
import tw.org.il.dongsheng.templeapp.model.MeritCategory;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteMeritBoxOpeningRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteMeritCategoryRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.MeritBoxOpeningReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MeritController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @FXML private ComboBox<MeritCategory> categoryCombo;
    @FXML private RadioButton allRadio;
    @FXML private RadioButton dateRadio;
    @FXML private TextField startDateField;
    @FXML private TextField endDateField;
    @FXML private TableView<MeritBoxOpening> tableView;
    @FXML private TableColumn<MeritBoxOpening, String> dateColumn, serialColumn, openerColumn
            , noteColumn, categoryColumn, creatorColumn;
    @FXML private TableColumn<MeritBoxOpening, Long> amountColumn;

    private final SQLiteMeritCategoryRepository categoryRepository = new SQLiteMeritCategoryRepository();
    private final SQLiteMeritBoxOpeningRepository openingRepository = new SQLiteMeritBoxOpeningRepository();

    @FXML
    public void initialize() {
        configureTable();
        LocalDate today = LocalDate.now();
        if (startDateField != null) {
            startDateField.setText(today.format(DATE_FORMAT));
        }
        if (endDateField != null) {
            endDateField.setText(today.format(DATE_FORMAT));
        }
        if (allRadio != null) {
            allRadio.setSelected(true);
        }
        reloadCategories();
        onQuery();
    }

    @FXML
    private void onQuery() {
        try {
            String categoryCode = null;
            MeritCategory selected = categoryCombo == null ? null : categoryCombo.getValue();
            if (selected != null && selected.getCode() != null && !selected.getCode().isBlank()) {
                categoryCode = selected.getCode();
            }

            LocalDate startDate = null;
            LocalDate endDate = null;
            if (dateRadio != null && dateRadio.isSelected()) {
                startDate = parseDate(startDateField == null ? null : startDateField.getText(), "起始日期");
                endDate = parseDate(endDateField == null ? null : endDateField.getText(), "結束日期");
                if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                    AlertDialog.showWarning("查詢", "起始日期不可大於結束日期");
                    return;
                }
            }

            List<MeritBoxOpening> openings = openingRepository.search(categoryCode, startDate, endDate);
            tableView.setItems(FXCollections.observableArrayList(openings));

        } catch (SQLException e) {
            AlertDialog.showError("功德箱查詢", "查詢失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onOpenBox() {
        if (AuthSession.getCurrentUser() != null && !AuthSession.canManageSystem()) {
            AlertDialog.showWarning("開箱", "只有管理者以上權限可以操作開箱");
            return;
        }

        MeritCategory selected = categoryCombo == null ? null : categoryCombo.getValue();
        if (selected == null || selected.getCode() == null || selected.getCode().isBlank()) {
            AlertDialog.showWarning("開箱", "請先選擇一個分類，不能使用「全部」進行開箱");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("開箱");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (categoryCombo != null && categoryCombo.getScene() != null) {
            dialog.initOwner(categoryCombo.getScene().getWindow());
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setHeaderText(null);

        TextField dateField = new TextField(LocalDate.now().format(DATE_FORMAT));
        dateField.setPrefColumnCount(12);
        final String nextSerialNo;
        try {
            nextSerialNo = openingRepository.nextSerialNo();
        } catch (SQLException e) {
            AlertDialog.showError("開箱", "無法取得流水號：" + e.getMessage());
            return;
        }
        TextField serialField = new TextField(nextSerialNo);
        serialField.setEditable(false);
        TextField amountField = new TextField();
        TextField openerField = new TextField(valueOrEmpty(AuthSession.getCurrentOperatorName()));
        TextField noteField = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        int row = 0;
        grid.add(new Label("開箱日期"), 0, row);
        grid.add(dateField, 1, row++);
        grid.add(new Label("流水號"), 0, row);
        grid.add(serialField, 1, row++);
        grid.add(new Label("金額"), 0, row);
        grid.add(amountField, 1, row++);
        grid.add(new Label("開箱人員"), 0, row);
        grid.add(openerField, 1, row++);
        grid.add(new Label("備註"), 0, row);
        grid.add(noteField, 1, row++);
        grid.add(new Label("分類"), 0, row);
        grid.add(new Label(selected.getName()), 1, row++);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(420);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        LocalDate openingDate;
        try {
            openingDate = parseDate(dateField.getText(), "開箱日期");
        } catch (IllegalArgumentException ex) {
            AlertDialog.showWarning("開箱", ex.getMessage());
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(amountField.getText().trim());
            if (amount <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            AlertDialog.showWarning("開箱", "金額請輸入正整數");
            return;
        }

        String opener = openerField.getText() == null ? "" : openerField.getText().trim();
        String note = noteField.getText() == null ? "" : noteField.getText().trim();
        String createdBy = valueOrEmpty(AuthSession.getCurrentOperatorName());
        LocalDateTime now = LocalDateTime.now();

        try {
            openingRepository.save(new MeritBoxOpening(
                    null,
                    openingDate,
                    serialField.getText().trim(),
                    amount,
                    opener,
                    note,
                    selected.getCode(),
                    selected.getName(),
                    createdBy,
                    now
            ));
            AlertDialog.showInfo("開箱", "開箱完成");
            onQuery();
        } catch (SQLException e) {
            AlertDialog.showError("開箱", "儲存失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onCategoryManagement() {
        if (AuthSession.getCurrentUser() != null && !AuthSession.canManageSystem()) {
            AlertDialog.showWarning("分類管理", "只有管理者以上權限可以操作分類管理");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(TempleApplication.class.getResource("merit-category-management.fxml"));
            Parent root = loader.load();
            MeritCategoryManagementController controller = loader.getController();
            controller.setRepository(categoryRepository);

            Stage stage = new Stage();
            stage.setTitle("分類管理");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(categoryCombo.getScene().getWindow());
            stage.setScene(new Scene(root));
            stage.showAndWait();
            reloadCategories();
        } catch (IOException e) {
            AlertDialog.showError("分類管理", "無法開啟分類管理：" + e.getMessage());
        }
    }

    @FXML
    public void onPrint() {
        List<MeritBoxOpening> rows = tableView.getItems();
        if (rows.isEmpty()) {
            AlertDialog.showWarning("功德箱", "依目前條件查無可列印的功德箱資料");
            return;
        }
        PrintPreview.show(tableView.getScene().getWindow(),
                "開 箱 明 細 表",
                MeritBoxOpeningReportBuilder.buildPages(rows));

    }

    private void configureTable() {
        if (dateColumn != null) {
            dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                    cell.getValue().openingDate() == null ? "" : cell.getValue().openingDate().format(DATE_FORMAT)
            ));
        }
        if (serialColumn != null) {
            serialColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                    cell.getValue().serialNo() == null ? "" : cell.getValue().serialNo()
            ));
        }
        if (amountColumn != null) {
            amountColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(
                    cell.getValue().amount()
            ));
        }
        if (openerColumn != null) {
            openerColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                    cell.getValue().opener() == null ? "" : cell.getValue().opener()
            ));
        }
        if (noteColumn != null) {
            noteColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                    cell.getValue().note() == null ? "" : cell.getValue().note()
            ));
        }
        if (categoryColumn != null) {
            categoryColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                    cell.getValue().categoryName() == null ? "" : cell.getValue().categoryName()
            ));
        }
        if (creatorColumn != null) {
            creatorColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                    cell.getValue().createdBy() == null ? "" : cell.getValue().createdBy()
            ));
        }
    }

    private void reloadCategories() {
        try {
            List<MeritCategory> categories = new ArrayList<>(categoryRepository.findAll(false));
            categories.add(MeritCategory.allOption());
            categoryCombo.setItems(FXCollections.observableArrayList(categories));
            categoryCombo.getSelectionModel().selectLast();
        } catch (SQLException e) {
            AlertDialog.showError("功德箱管理", "讀取分類失敗：" + e.getMessage());
        }
    }

    private LocalDate parseDate(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不可空白");
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + "格式錯誤，請使用 yyyy.MM.dd");
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

}
