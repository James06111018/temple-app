package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

public class LoginRecordController {

    @FXML private PaginationBar loginRecordPageBar;

    @FXML
    private void initialize() {
        loginRecordPageBar.setTotalCount(0);
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("登入紀錄", "此功能尚未實作");
    }
}
