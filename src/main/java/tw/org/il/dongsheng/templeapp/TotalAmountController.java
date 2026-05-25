package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.model.Donation;

import java.util.List;
import java.util.Map;

public class TotalAmountController {
    @FXML private TextField memberIdField, incomeCountField, incomeAmountField, expenseCountField, expenseAmountField,
            totalCountField, totalAmountField;

    public void setMemberId(String memberId) {
        memberIdField.setText(memberId == null ? "" : memberId);
    }

    public void setSummary(String memberId, List<Donation> donations, Map<String, String> categoryMap) {
        setMemberId(memberId);

        int incomeCount = 0;
        int incomeAmount = 0;
        int expenseCount = 0;
        int expenseAmount = 0;

        for (Donation donation : donations) {
            int shouldPay = donation.getShouldPay() == null ? 0 : donation.getShouldPay();
            if (isExpense(donation, categoryMap)) {
                expenseCount++;
                expenseAmount += Math.abs(shouldPay);
            } else {
                incomeCount++;
                incomeAmount += shouldPay;
            }
        }

        incomeCountField.setText(String.valueOf(incomeCount));
        incomeAmountField.setText(String.valueOf(incomeAmount));
        expenseCountField.setText(String.valueOf(expenseCount));
        expenseAmountField.setText(String.valueOf(expenseAmount));
        totalCountField.setText(String.valueOf(incomeCount - expenseCount));
        totalAmountField.setText(String.valueOf(incomeAmount - expenseAmount));
    }

    private boolean isExpense(Donation donation, Map<String, String> categoryMap) {
        Integer shouldPay = donation.getShouldPay();
        if (shouldPay != null && shouldPay < 0) {
            return true;
        }

        String category = categoryMap.getOrDefault(donation.getDonateType(), "");
        return category.startsWith("Z-") || category.contains("支出");
    }

    @FXML
    private void onPlaceholderAction() {
        // 畫面先完成，後續再接明細表、分類表與統計表。
    }
}
