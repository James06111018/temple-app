package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class TotalAmountController {
    @FXML private TextField memberIdField;

    public void setMemberId(String memberId) {
        memberIdField.setText(memberId == null ? "" : memberId);
    }

    @FXML
    private void onPlaceholderAction() {
        // 畫面先完成，後續再接明細表、分類表與統計表。
    }
}
