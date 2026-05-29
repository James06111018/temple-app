package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

public class CreateRecordController {

    @FXML private PaginationBar createRecordPageBar;

    @FXML
    private void initialize() {
        createRecordPageBar.setTotalCount(0);
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("建檔紀錄", "此功能尚未實作");
    }
}
