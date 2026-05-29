package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

public class MergeRecordController {

    @FXML private PaginationBar mergeRecordPageBar;

    @FXML
    private void initialize() {
        mergeRecordPageBar.setTotalCount(0);
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("合併紀錄", "此功能尚未實作");
    }
}
