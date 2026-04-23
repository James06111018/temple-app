package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import tw.org.il.dongsheng.templeapp.util.AreaUtil;

import java.util.List;
import java.util.Map;

public class TempleController {

    @FXML
    private ComboBox<String> cityBox, distBox, mailBox;

    @FXML
    public void initialize() {

        Map<String, List<String>> areaMap = AreaUtil.getAllTaiwanAreas();
        cityBox.getItems().addAll(areaMap.keySet());
        cityBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                distBox.getItems().clear();
                distBox.getItems().addAll(areaMap.get(newVal));
                distBox.getSelectionModel().selectFirst();
            }
        });
    }
}
