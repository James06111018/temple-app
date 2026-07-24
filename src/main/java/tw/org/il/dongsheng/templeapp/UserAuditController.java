package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import tw.org.il.dongsheng.templeapp.model.UserAuditRecord;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

import java.sql.SQLException;

public class UserAuditController {

    @FXML private TableView<UserAuditRecord> auditTable;
    @FXML private TableColumn<UserAuditRecord, String> idColumn, userIdColumn, actionColumn, changedByColumn, changedAtColumn, snapshotColumn;
    @FXML private PaginationBar auditPageBar;
    private SQLiteAuthRepository repository;

    @FXML
    private void initialize() {
        repository = new SQLiteAuthRepository(SQLiteDatabaseManager.getInstance());
        idColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getId())));
        userIdColumn.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getUserId())));
        actionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAction()));
        changedByColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getChangedBy()));
        changedAtColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getChangedAt()));
        snapshotColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSnapshot()));
        onSearch();
    }

    @FXML
    private void onSearch() {
        try {
            var records = repository.findUserAudits();
            auditTable.setItems(FXCollections.observableArrayList(records));
            auditPageBar.setTotalCount(records.size());
        } catch (SQLException e) {
            AlertDialog.showError("使用者異動紀錄", "查詢失敗：" + e.getMessage());
        }
    }
}
