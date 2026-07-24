package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

import java.io.IOException;

public class EmptyNumberQueryController {

    @FXML private PaginationBar deletedNumberPageBar;
    @FXML private PaginationBar blankNameNumberPageBar;

    @FXML
    private void initialize() {
        deletedNumberPageBar.setTotalCount(0);
        blankNameNumberPageBar.setTotalCount(0);
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
