package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.LightReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TotalAmountController {
    @FXML private TextField memberIdField, incomeCountField, incomeAmountField, expenseCountField, expenseAmountField,
            totalCountField, totalAmountField;

    private LightMember member;
    private List<Donation> donations = List.of();
    private Map<String, String> categoryMap = Map.of();

    public void setMemberId(String memberId) {
        memberIdField.setText(memberId == null ? "" : memberId);
    }

    public void setSummary(LightMember member, List<Donation> donations, Map<String, String> categoryMap) {
        this.member = member;
        this.donations = donations == null ? List.of() : List.copyOf(donations);
        this.categoryMap = categoryMap == null ? Map.of() : new LinkedHashMap<>(categoryMap);
        setMemberId(member == null || member.getId() == null ? "" : Util.stringFormat(member.getId()));

        int incomeCount = 0;
        int incomeAmount = 0;
        int expenseCount = 0;
        int expenseAmount = 0;

        for (Donation donation : this.donations) {
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
    private void onOpenDetailReport() {
        if (!hasReportData()) {
            return;
        }
        PrintPreview.show(
                memberIdField.getScene().getWindow(),
                "總金額－明細表",
                LightReportBuilder.buildTotalAmountDetailPages(member, donations, categoryMap)
        );
    }

    @FXML
    private void onOpenCategoryReport() {
        if (!hasReportData()) {
            return;
        }
        PrintPreview.show(
                memberIdField.getScene().getWindow(),
                "總金額－分類表",
                LightReportBuilder.buildTotalAmountCategoryPages(member, donations, categoryMap)
        );
    }

    @FXML
    private void onOpenStatisticsReport() {
        if (!hasReportData()) {
            return;
        }
        PrintPreview.show(
                memberIdField.getScene().getWindow(),
                "總金額－統計表",
                LightReportBuilder.buildTotalAmountStatisticsPages(member, donations, categoryMap)
        );
    }

    private boolean hasReportData() {
        if (donations.isEmpty()) {
            AlertDialog.showInfo("總金額", "查無捐款資料");
            return false;
        }
        return true;
    }
}
