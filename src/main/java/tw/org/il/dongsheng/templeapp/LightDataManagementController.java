package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;

public class LightDataManagementController {
    @FXML private ComboBox<String> filterTypeBox, statusBox, taiSuiTypeBox, lightTypeBox;

    @FXML
    public void initialize() {
        filterTypeBox.setItems(FXCollections.observableArrayList("", "太", "光", "虎", "媽", "宮"));
        statusBox.setItems(FXCollections.observableArrayList("", "未使用", "已使用", "已刪除"));
        taiSuiTypeBox.setItems(FXCollections.observableArrayList("太"));
        lightTypeBox.setItems(FXCollections.observableArrayList("三"));
        taiSuiTypeBox.getSelectionModel().selectFirst();
        lightTypeBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void onPlaceholderAction() {
        // 畫面先完成，後續再接實際管理功能。
    }
}
