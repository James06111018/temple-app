package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

public class PrintLabelsController {

    @FXML private RadioButton memberIdRadio;
    @FXML private ComboBox<String> reportTypeBox;
    @FXML private ComboBox<String> startPositionBox;

    @FXML
    private void initialize() {
        memberIdRadio.setSelected(true);
        reportTypeBox.setItems(FXCollections.observableArrayList(
                "1. 郵寄標籤(橫式)",
                "2. 郵寄標籤(直式)",
                "3. 名冊",
                "4. 通信函"
        ));
        reportTypeBox.getSelectionModel().selectFirst();
        startPositionBox.setItems(FXCollections.observableArrayList("1", "2", "3", "4", "5"));
        startPositionBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("列印標籤", "此功能尚未實作");
    }
}
