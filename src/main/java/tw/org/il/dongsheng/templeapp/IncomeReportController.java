package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.time.LocalDate;

public class IncomeReportController {

    @FXML private TextField startDateField;
    @FXML private TextField endDateField;

    @FXML
    private void initialize() {
        String today = currentRocDate();
        startDateField.setText(today);
        endDateField.setText(today);
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("收入報表", "此功能尚未實作");
    }

    private String currentRocDate() {
        LocalDate today = LocalDate.now();
        return String.format("%03d.%02d.%02d", today.getYear() - 1911, today.getMonthValue(), today.getDayOfMonth());
    }
}
