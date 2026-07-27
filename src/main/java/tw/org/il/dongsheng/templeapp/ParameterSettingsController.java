package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteSystemSettingsRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParameterSettingsController {
    private static final String GROUP_GENERAL = "GENERAL";
    private static final String GROUP_PRAYER = "PRAYER";

    @FXML private TextField directorField, ageStartField, ageEndField, changeDeadlineField;
    @FXML private CheckBox allowEditDonationBox, allowDeleteSingleBox;
    @FXML private TextField taiSuiYearField, taiSuiMonthField, taiSuiDayField, taiSuiStarField;

    private SQLiteSystemSettingsRepository repository;

    @FXML
    public void initialize() {
        repository = new SQLiteSystemSettingsRepository(SQLiteDatabaseManager.getInstance());
        try {
            repository.createTable();
            loadSettings();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadSettings() throws SQLException {
        Map<String, String> general = repository.findByGroup(GROUP_GENERAL);
        directorField.setText(general.getOrDefault("director", ""));
        ageStartField.setText(general.getOrDefault("age_start", ""));
        ageEndField.setText(general.getOrDefault("age_end", ""));
        changeDeadlineField.setText(general.getOrDefault("change_deadline", ""));
        allowEditDonationBox.setSelected(Boolean.parseBoolean(general.getOrDefault("allow_edit_donation", "true")));
        allowDeleteSingleBox.setSelected(Boolean.parseBoolean(general.getOrDefault("allow_delete_single", "false")));

        Map<String, String> prayer = repository.findByGroup(GROUP_PRAYER);
        taiSuiYearField.setText(prayer.getOrDefault("tai_sui_year", ""));
        taiSuiMonthField.setText(prayer.getOrDefault("tai_sui_month", ""));
        taiSuiDayField.setText(prayer.getOrDefault("tai_sui_day", ""));
        taiSuiStarField.setText(prayer.getOrDefault("tai_sui_star", ""));
    }

    @FXML
    private void onSaveGeneral() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("director", directorField.getText());
        values.put("age_start", ageStartField.getText());
        values.put("age_end", ageEndField.getText());
        values.put("change_deadline", changeDeadlineField.getText());
        values.put("allow_edit_donation", String.valueOf(allowEditDonationBox.isSelected()));
        values.put("allow_delete_single", String.valueOf(allowDeleteSingleBox.isSelected()));
        save(GROUP_GENERAL, values);
    }

    @FXML
    private void onSavePrayer() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("tai_sui_year", taiSuiYearField.getText());
        values.put("tai_sui_month", taiSuiMonthField.getText());
        values.put("tai_sui_day", taiSuiDayField.getText());
        values.put("tai_sui_star", taiSuiStarField.getText());
        save(GROUP_PRAYER, values);
    }

    private void save(String group, Map<String, String> values) {
        try {
            repository.saveGroup(group, values, AuthSession.getCurrentOperatorName());
            AlertDialog.showInfo("參數設定", "儲存成功");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
