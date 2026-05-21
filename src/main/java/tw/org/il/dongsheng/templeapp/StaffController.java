package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import tw.org.il.dongsheng.templeapp.util.AreaUtil;

import java.util.List;
import java.util.Map;

public class StaffController {

    @FXML
    private ComboBox<String> genderBox, cityBox, distBox, mailBox;

    @FXML
    public void initialize() {

        Map<String, List<String>> areaMap = AreaUtil.getAllTaiwanAreas();
        cityBox.getItems().add("");
        cityBox.getItems().addAll(areaMap.keySet());
        cityBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            distBox.getItems().clear();
            distBox.getSelectionModel().clearSelection();
            if (newVal != null && !newVal.isBlank()) {
                distBox.getItems().addAll(areaMap.get(newVal));
            }
        });
    }
}
