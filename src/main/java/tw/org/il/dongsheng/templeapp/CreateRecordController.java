package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.model.CreateRecord;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteCreateRecordRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class CreateRecordController {

    @FXML private TextField memberNumberField;
    @FXML private TextField startDateField;
    @FXML private TextField endDateField;
    @FXML private TextField memberNameField;

    @FXML private TableView<CreateRecord> createRecordTable;
    @FXML private TableColumn<CreateRecord, String> selectorColumn;
    @FXML private TableColumn<CreateRecord, String> dateColumn;
    @FXML private TableColumn<CreateRecord, String> timeColumn;
    @FXML private TableColumn<CreateRecord, String> statusColumn;
    @FXML private TableColumn<CreateRecord, String> operatorColumn;
    @FXML private TableColumn<CreateRecord, String> memberNumberColumn;
    @FXML private TableColumn<CreateRecord, String> nameColumn;
    @FXML private TableColumn<CreateRecord, String> birthDateColumn;
    @FXML private TableColumn<CreateRecord, String> lunarBirthDateColumn;
    @FXML private TableColumn<CreateRecord, String> zodiacColumn;
    @FXML private TableColumn<CreateRecord, String> zodiacYearColumn;
    @FXML private TableColumn<CreateRecord, String> birthTimeColumn;
    @FXML private TableColumn<CreateRecord, String> genderColumn;
    @FXML private TableColumn<CreateRecord, String> addressColumn;
    @FXML private TableColumn<CreateRecord, String> phoneColumn;
    @FXML private TableColumn<CreateRecord, String> zipCodeColumn;
    @FXML private TableColumn<CreateRecord, String> isMailColumn;
    @FXML private TableColumn<CreateRecord, String> noteColumn;
    @FXML private TableColumn<CreateRecord, String> categoryColumn;
    @FXML private TableColumn<CreateRecord, String> idNumberColumn;
    @FXML private TableColumn<CreateRecord, String> sortOrderColumn;
    @FXML private PaginationBar createRecordPageBar;

    private final SQLiteCreateRecordRepository repository = new SQLiteCreateRecordRepository();
    private final ObservableList<CreateRecord> records = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        bindColumns();
        configureSelectionMarker();
        createRecordTable.setItems(records);
        createRecordPageBar.setTotalCount(0);
        createRecordPageBar.setOnAction(this::selectRecordFromNavigator);

        createRecordTable.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldIndex, newIndex) -> {
                    int selectedIndex = newIndex == null ? -1 : newIndex.intValue();
                    if (selectedIndex >= 0) {
                        createRecordPageBar.setCurrentIndex(selectedIndex);
                    }
                    createRecordTable.refresh();
                });

        String today = formatRocDate(LocalDate.now());
        startDateField.setText(today);
        endDateField.setText(today);
        memberNumberField.setOnAction(event -> onSearchByMemberNumber());
        startDateField.setOnAction(event -> onSearchByDate());
        endDateField.setOnAction(event -> onSearchByDate());
        memberNameField.setOnAction(event -> onSearchByName());
    }

    private void bindColumns() {
        bind(dateColumn, CreateRecord::date);
        bind(timeColumn, CreateRecord::time);
        bind(statusColumn, CreateRecord::status);
        bind(operatorColumn, CreateRecord::operator);
        bind(memberNumberColumn, CreateRecord::memberNumber);
        bind(nameColumn, CreateRecord::name);
        bind(birthDateColumn, CreateRecord::birthDate);
        bind(lunarBirthDateColumn, CreateRecord::lunarBirthDate);
        bind(zodiacColumn, CreateRecord::zodiac);
        bind(zodiacYearColumn, CreateRecord::zodiacYear);
        bind(birthTimeColumn, CreateRecord::birthTime);
        bind(genderColumn, CreateRecord::gender);
        bind(addressColumn, CreateRecord::address);
        bind(phoneColumn, CreateRecord::phone);
        bind(zipCodeColumn, CreateRecord::zipCode);
        bind(isMailColumn, CreateRecord::isMail);
        bind(noteColumn, CreateRecord::note);
        bind(categoryColumn, CreateRecord::category);
        bind(idNumberColumn, CreateRecord::idNumber);
        bind(sortOrderColumn, CreateRecord::sortOrder);
    }

    private void bind(TableColumn<CreateRecord, String> column,
                      Function<CreateRecord, String> valueProvider) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueProvider.apply(cell.getValue())));
    }

    private void configureSelectionMarker() {
        selectorColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(""));
        selectorColumn.setSortable(false);
        selectorColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                CreateRecord rowItem = getTableRow() == null ? null : getTableRow().getItem();
                setText(!empty && rowItem != null
                        && rowItem == createRecordTable.getSelectionModel().getSelectedItem() ? "▶" : "");
            }
        });
    }

    @FXML
    private void onSearchByMemberNumber() {
        String input = memberNumberField.getText() == null ? "" : memberNumberField.getText().trim();
        if (input.isEmpty()) {
            AlertDialog.showWarning("建檔記錄", "請輸入電腦編號");
            return;
        }
        try {
            int memberId = Integer.parseInt(input);
            showResults(repository.findByMemberId(memberId));
        } catch (NumberFormatException ex) {
            AlertDialog.showWarning("建檔記錄", "電腦編號格式不正確");
        } catch (RuntimeException ex) {
            showQueryError(ex);
        }
    }

    @FXML
    private void onSearchByDate() {
        try {
            LocalDate start = parseRocDate(startDateField.getText());
            LocalDate end = parseRocDate(endDateField.getText());
            if (start.isAfter(end)) {
                AlertDialog.showWarning("建檔記錄", "起始日期不可晚於結束日期");
                return;
            }
            showResults(repository.findByDateRange(start, end));
        } catch (IllegalArgumentException ex) {
            AlertDialog.showWarning("建檔記錄", ex.getMessage());
        } catch (RuntimeException ex) {
            showQueryError(ex);
        }
    }

    @FXML
    private void onSearchByName() {
        String name = memberNameField.getText() == null ? "" : memberNameField.getText().trim();
        if (name.isEmpty()) {
            AlertDialog.showWarning("建檔記錄", "請輸入姓名");
            return;
        }
        try {
            showResults(repository.findByName(name));
        } catch (RuntimeException ex) {
            showQueryError(ex);
        }
    }

    private void showResults(List<CreateRecord> queryResults) {
        records.setAll(queryResults);
        createRecordPageBar.setTotalCount(records.size());
        if (records.isEmpty()) {
            createRecordTable.getSelectionModel().clearSelection();
            AlertDialog.showInfo("建檔記錄", "查無建檔記錄");
            return;
        }
        createRecordTable.getSelectionModel().selectFirst();
        createRecordTable.scrollTo(0);
    }

    private void selectRecordFromNavigator() {
        if (records.isEmpty()) {
            return;
        }
        int index = createRecordPageBar.getCurrentIndex();
        createRecordTable.getSelectionModel().select(index);
        createRecordTable.scrollTo(index);
    }

    private void showQueryError(RuntimeException ex) {
        AlertDialog.showError("建檔記錄", ex.getMessage() == null ? "查詢失敗" : ex.getMessage());
    }

    static LocalDate parseRocDate(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("請輸入完整的日期區間");
        }
        String[] parts = text.replace('/', '.').replace('-', '.').split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("日期格式應為民國年.月.日，例如 115.05.14");
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            int westernYear = year < 1911 ? year + 1911 : year;
            return LocalDate.of(westernYear, month, day);
        } catch (NumberFormatException | DateTimeException ex) {
            throw new IllegalArgumentException("日期格式應為民國年.月.日，例如 115.05.14");
        }
    }

    private static String formatRocDate(LocalDate date) {
        return String.format(Locale.ROOT, "%03d.%02d.%02d",
                date.getYear() - 1911, date.getMonthValue(), date.getDayOfMonth());
    }
}
