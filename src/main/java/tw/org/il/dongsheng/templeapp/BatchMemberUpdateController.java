package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.MemberBatchUpdateRequest;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.util.List;
import java.util.function.Function;

public class BatchMemberUpdateController {

    @FXML private Label memberCountLabel;
    @FXML private TextField originalPhoneField;
    @FXML private TextField newPhoneField;
    @FXML private TextField originalZipCodeField;
    @FXML private TextField originalAddressField;
    @FXML private TextField newZipCodeField;
    @FXML private TextField newAddressField;

    private List<LightMember> members = List.of();
    private Function<MemberBatchUpdateRequest, Boolean> updateHandler;

    public void setData(List<LightMember> members, LightMember referenceMember,
                        Function<MemberBatchUpdateRequest, Boolean> updateHandler) {
        this.members = members == null ? List.of() : List.copyOf(members);
        this.updateHandler = updateHandler;
        memberCountLabel.setText("目前查詢共 " + this.members.size() + " 筆信眾資料");
        if (referenceMember != null) {
            originalPhoneField.setText(value(referenceMember.getPhone()));
            originalZipCodeField.setText(value(referenceMember.getZipCode()));
            originalAddressField.setText(value(referenceMember.getAddress()));
        }
    }

    @FXML
    private void onUpdatePhone() {
        String phone = newPhoneField.getText().trim();
        if (Util.isBlank(phone)) {
            AlertDialog.showWarning("整批修改", "請輸入新電話");
            newPhoneField.requestFocus();
            return;
        }
        if (!confirm("確定要將目前查詢的 " + members.size() + " 筆信眾電話整批修改？")) {
            return;
        }
        submit(new MemberBatchUpdateRequest(memberIds(), phone, null, null, true, false));
    }

    @FXML
    private void onUpdateAddress() {
        String zipCode = newZipCodeField.getText().trim();
        String address = newAddressField.getText().trim();
        if (Util.isBlank(zipCode) || Util.isBlank(address)) {
            AlertDialog.showWarning("整批修改", "請完整輸入新郵遞區號及新地址");
            if (Util.isBlank(zipCode)) {
                newZipCodeField.requestFocus();
            } else {
                newAddressField.requestFocus();
            }
            return;
        }
        if (!confirm("確定要將目前查詢的 " + members.size() + " 筆信眾地址整批修改？")) {
            return;
        }
        submit(new MemberBatchUpdateRequest(memberIds(), null, zipCode, address, false, true));
    }

    private void submit(MemberBatchUpdateRequest request) {
        if (updateHandler != null && updateHandler.apply(request)) {
            close();
        }
    }

    private boolean confirm(String message) {
        return !members.isEmpty() && AlertDialog.showConfirm("整批修改", message);
    }

    private List<Integer> memberIds() {
        return members.stream()
                .map(LightMember::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private void close() {
        ((Stage) memberCountLabel.getScene().getWindow()).close();
    }
}
