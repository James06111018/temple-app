package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.MemberBatchUpdateRequest;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class MemberContactUpdateController {

    @FXML private TableView<LightMember> memberTable;
    @FXML private TableColumn<LightMember, String> idColumn;
    @FXML private TableColumn<LightMember, String> nameColumn;
    @FXML private TableColumn<LightMember, String> birthDateColumn;
    @FXML private TableColumn<LightMember, String> lunarBirthDateColumn;
    @FXML private TableColumn<LightMember, String> ageColumn;
    @FXML private TableColumn<LightMember, String> zodiacColumn;
    @FXML private TableColumn<LightMember, String> birthTimeColumn;
    @FXML private TableColumn<LightMember, String> genderColumn;
    @FXML private Label selectedCountLabel;
    @FXML private ListView<String> selectedMemberList;
    @FXML private TextField originalPhoneField;
    @FXML private TextField newPhoneField;
    @FXML private TextField originalZipCodeField;
    @FXML private TextField originalAddressField;
    @FXML private TextField newZipCodeField;
    @FXML private TextField newAddressField;

    private Function<MemberBatchUpdateRequest, Boolean> updateHandler;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(data ->
                text(Util.stringFormat(data.getValue().getId())));
        nameColumn.setCellValueFactory(data -> text(data.getValue().getName()));
        birthDateColumn.setCellValueFactory(data -> text(data.getValue().getBirthDate()));
        lunarBirthDateColumn.setCellValueFactory(data -> text(data.getValue().getLunarBirthDate()));
        ageColumn.setCellValueFactory(data ->
                text(data.getValue().getAge() == null ? "" : String.valueOf(data.getValue().getAge())));
        zodiacColumn.setCellValueFactory(data -> text(data.getValue().getZodiac()));
        birthTimeColumn.setCellValueFactory(data -> text(data.getValue().getBirthTime()));
        genderColumn.setCellValueFactory(data -> text(data.getValue().getGender()));

        memberTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        memberTable.setRowFactory(table -> {
            TableRow<LightMember> row = new TableRow<>();
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() != MouseButton.PRIMARY || row.isEmpty()) {
                    return;
                }

                int rowIndex = row.getIndex();
                if (memberTable.getSelectionModel().isSelected(rowIndex)) {
                    memberTable.getSelectionModel().clearSelection(rowIndex);
                } else {
                    memberTable.getSelectionModel().select(rowIndex);
                }
                memberTable.requestFocus();
                event.consume();
            });
            return row;
        });
        memberTable.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<LightMember>) change -> updateSelectionSummary()
        );
    }

    public void setData(List<LightMember> members, LightMember selectedMember,
                        Function<MemberBatchUpdateRequest, Boolean> updateHandler) {
        this.updateHandler = updateHandler;
        List<LightMember> sortedMembers = members == null
                ? List.of()
                : members.stream()
                        .sorted(Comparator.comparing(
                                LightMember::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                        .toList();
        memberTable.setItems(FXCollections.observableArrayList(sortedMembers));

        LightMember initialMember = selectedMember;
        if (initialMember == null && !sortedMembers.isEmpty()) {
            initialMember = sortedMembers.get(0);
        }
        if (initialMember != null) {
            memberTable.getSelectionModel().select(initialMember);
            memberTable.scrollTo(initialMember);
        }
        updateSelectionSummary();
    }

    @FXML
    private void onConfirm() {
        List<LightMember> selectedMembers = List.copyOf(
                memberTable.getSelectionModel().getSelectedItems()
        );
        if (selectedMembers.isEmpty()) {
            AlertDialog.showWarning("家屬資訊", "請至少選擇一位信眾");
            return;
        }

        String phone = newPhoneField.getText().trim();
        String zipCode = newZipCodeField.getText().trim();
        String address = newAddressField.getText().trim();
        boolean updatePhone = !Util.isBlank(phone);
        boolean hasZipCode = !Util.isBlank(zipCode);
        boolean hasAddress = !Util.isBlank(address);
        boolean updateAddress = hasZipCode && hasAddress;

        if (!updatePhone && !hasZipCode && !hasAddress) {
            AlertDialog.showWarning("家屬資訊", "請輸入新電話或新地址");
            return;
        }
        if (hasZipCode != hasAddress) {
            AlertDialog.showWarning("家屬資訊", "新郵遞區號及新地址需完整輸入");
            if (!hasZipCode) {
                newZipCodeField.requestFocus();
            } else {
                newAddressField.requestFocus();
            }
            return;
        }
        if (!AlertDialog.showConfirm(
                "家屬資訊",
                "確定要批次修改選取的 " + selectedMembers.size() + " 筆信眾資料？"
        )) {
            return;
        }

        MemberBatchUpdateRequest request = new MemberBatchUpdateRequest(
                selectedMembers.stream().map(LightMember::getId).toList(),
                phone,
                zipCode,
                address,
                updatePhone,
                updateAddress
        );
        if (updateHandler != null && updateHandler.apply(request)) {
            close();
        }
    }

    private void updateSelectionSummary() {
        List<LightMember> selectedMembers = List.copyOf(
                memberTable.getSelectionModel().getSelectedItems()
        );
        selectedCountLabel.setText(selectedMembers.size() + " 人");
        selectedMemberList.setItems(FXCollections.observableArrayList(
                selectedMembers.stream()
                        .map(member -> value(member.getName()) + "  " + Util.stringFormat(member.getId()))
                        .toList()
        ));

        if (selectedMembers.size() == 1) {
            LightMember selected = selectedMembers.get(0);
            originalPhoneField.setText(value(selected.getPhone()));
            originalZipCodeField.setText(value(selected.getZipCode()));
            originalAddressField.setText(value(selected.getAddress()));
        } else if (selectedMembers.size() > 1) {
            originalPhoneField.setText("多筆資料");
            originalZipCodeField.setText("多筆");
            originalAddressField.setText("多筆資料");
        } else {
            originalPhoneField.clear();
            originalZipCodeField.clear();
            originalAddressField.clear();
        }
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value(value));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void close() {
        ((Stage) memberTable.getScene().getWindow()).close();
    }
}
