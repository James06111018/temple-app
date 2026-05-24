package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;

public class HouseholdLightController {
    @FXML private ComboBox<String> collectorBox;

    @FXML
    public void initialize() {
        collectorBox.setItems(FXCollections.observableArrayList("", "林暐皓"));
        collectorBox.getSelectionModel().select(1);
    }

    @FXML
    private void onPlaceholderAction() {
        // 畫面先完成，後續再接全戶點燈建立資料流程。
    }
}
