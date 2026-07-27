package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.util.List;

public class MemberSelectionController {

    @FXML private TableView<LightMember> memberSelectionTable;
    @FXML private TableColumn<LightMember, String> idColumn, nameColumn, genderColumn, birthDateColumn,
            phoneColumn, zipCodeColumn, addressColumn;

    private LightMember selectedMember;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(data ->
                new SimpleStringProperty(Util.stringFormat(data.getValue().getId())));
        nameColumn.setCellValueFactory(data -> text(data.getValue().getName()));
        genderColumn.setCellValueFactory(data -> text(data.getValue().getGender()));
        birthDateColumn.setCellValueFactory(data -> text(data.getValue().getBirthDate()));
        phoneColumn.setCellValueFactory(data -> text(data.getValue().getPhone()));
        zipCodeColumn.setCellValueFactory(data -> text(data.getValue().getZipCode()));
        addressColumn.setCellValueFactory(data -> text(data.getValue().getAddress()));

        memberSelectionTable.setRowFactory(table -> {
            TableRow<LightMember> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    selectedMember = row.getItem();
                    ((Stage) memberSelectionTable.getScene().getWindow()).close();
                }
            });
            return row;
        });
    }

    public void setMembers(List<LightMember> members) {
        memberSelectionTable.setItems(FXCollections.observableArrayList(members));
    }

    public LightMember getSelectedMember() {
        return selectedMember;
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }
}
