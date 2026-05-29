package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

public class DonationDetailsController {

    @FXML private ComboBox<String> sortBox;
    @FXML private PaginationBar donationDetailsPageBar;
    @FXML private TableView<?> donationDetailsTable;

    @FXML
    private void initialize() {
        sortBox.setItems(FXCollections.observableArrayList("順序", "日期", "姓名", "電腦編號", "金額"));
        sortBox.getSelectionModel().selectFirst();
        donationDetailsPageBar.setTotalCount(0);
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("捐款明細", "此功能尚未實作");
    }
}
