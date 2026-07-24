package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.service.LightMemberService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class EmptyNumberQueryController {

    @FXML private TableView<Integer> deletedNumberTable;
    @FXML private TableView<Integer> blankNameNumberTable;
    @FXML private TableColumn<Integer, String> deletedNumberColumn;
    @FXML private TableColumn<Integer, String> blankNameNumberColumn;
    @FXML private PaginationBar deletedNumberPageBar;
    @FXML private PaginationBar blankNameNumberPageBar;

    private LightMemberService lightMemberService;

    @FXML
    private void initialize() {
        SQLiteLightMemberRepository repository = new SQLiteLightMemberRepository(SQLiteDatabaseManager.getInstance());
        try {
            repository.createTable();
        } catch (SQLException e) {
            AlertDialog.showError("空號查詢", "初始化信眾資料表失敗：" + e.getMessage());
        }
        lightMemberService = new LightMemberService(repository);
        deletedNumberColumn.setCellValueFactory(cell -> new SimpleStringProperty(Util.stringFormat(cell.getValue())));
        blankNameNumberColumn.setCellValueFactory(cell -> new SimpleStringProperty(Util.stringFormat(cell.getValue())));
        loadEmptyNumbers();
    }

    private void loadEmptyNumbers() {
        try {
            List<Integer> deletedIds = lightMemberService.findDeletedIds();
            List<Integer> blankNameIds = lightMemberService.findBlankNameIds();

            deletedNumberTable.setItems(FXCollections.observableArrayList(deletedIds));
            blankNameNumberTable.setItems(FXCollections.observableArrayList(blankNameIds));
            deletedNumberPageBar.setTotalCount(deletedIds.size());
            blankNameNumberPageBar.setTotalCount(blankNameIds.size());
        } catch (SQLException e) {
            AlertDialog.showError("空號查詢", "讀取空號資料失敗：" + e.getMessage());
            deletedNumberPageBar.setTotalCount(0);
            blankNameNumberPageBar.setTotalCount(0);
        }
    }

    @FXML
    private void onOpenLoginRecord() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login-record.fxml"));
        Parent root = loader.load();
        Stage stage = new Stage();
        stage.setTitle("登入紀錄");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("空號查詢", "此功能尚未實作");
    }
}
