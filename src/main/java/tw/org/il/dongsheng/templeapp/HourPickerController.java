package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.HourItem;

import java.util.function.Consumer;

public class HourPickerController {

    @FXML private TableView<HourItem> tableView;
    @FXML private TableColumn<HourItem, String> colName;
    @FXML private TableColumn<HourItem, String> colTime;

    private Consumer<HourItem> onSelected;

    public void setOnSelected(Consumer<HourItem> callback) {
        this.onSelected = callback;
    }

    @FXML
    public void initialize() {

        colName.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName()));

        colTime.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getTimeRange()));

        tableView.setItems(FXCollections.observableArrayList(
                new HourItem("吉", "00-00"),
                new HourItem("子", "23-01"),
                new HourItem("丑", "01-03"),
                new HourItem("寅", "03-05"),
                new HourItem("卯", "05-07"),
                new HourItem("辰", "07-09"),
                new HourItem("巳", "09-11"),
                new HourItem("午", "11-13"),
                new HourItem("未", "13-15"),
                new HourItem("申", "15-17"),
                new HourItem("酉", "17-19"),
                new HourItem("戌", "19-21"),
                new HourItem("亥", "21-23")
        ));

        // 雙擊選擇
        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onConfirm();
            }
        });
    }

    private void onConfirm() {
        HourItem selected = tableView.getSelectionModel().getSelectedItem();

        if (selected != null && onSelected != null) {
            onSelected.accept(selected);
        }

        close();
    }

    private void close() {
        Stage stage = (Stage) tableView.getScene().getWindow();
        stage.close();
    }

}
