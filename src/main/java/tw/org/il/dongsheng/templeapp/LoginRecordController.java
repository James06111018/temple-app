package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import tw.org.il.dongsheng.templeapp.model.LoginRecord;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

import java.sql.SQLException;

public class LoginRecordController {

    @FXML private ComboBox<String> operatorBox;
    @FXML private TableView<LoginRecord> loginRecordTable;
    @FXML private TableColumn<LoginRecord, String> loginDateColumn, loginTimeColumn, computerNameColumn,
            operatorColumn, logoutDateColumn, logoutTimeColumn;
    @FXML private PaginationBar loginRecordPageBar;

    private SQLiteAuthRepository repository;

    @FXML
    private void initialize() {
        repository = new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance());
        loginDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLoginDate()));
        loginTimeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLoginTime()));
        computerNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getComputerName()));
        operatorColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getOperator()));
        logoutDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLogoutDate()));
        logoutTimeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLogoutTime()));
        try {
            repository.createTables();
            operatorBox.setItems(FXCollections.observableArrayList(
                    repository.findAllUsers().stream().map(user -> user.getDisplayName()).toList()
            ));
        } catch (SQLException e) {
            AlertDialog.showError("登入紀錄", "初始化登入紀錄失敗：" + e.getMessage());
        }
        onSearch();
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("登入紀錄", "此功能尚未實作");
    }

    @FXML
    private void onSearch() {
        try {
            var records = repository.findLoginRecords();
            loginRecordTable.setItems(FXCollections.observableArrayList(records));
            loginRecordPageBar.setTotalCount(records.size());
        } catch (SQLException e) {
            AlertDialog.showError("登入紀錄", "查詢登入紀錄失敗：" + e.getMessage());
        }
    }
}
