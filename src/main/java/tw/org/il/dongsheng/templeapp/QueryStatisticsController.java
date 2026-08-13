package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.sql.SQLException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class QueryStatisticsController {
    @FXML private ListView<DictionaryItem> availableCategoryList, selectedCategoryList;
    @FXML private ComboBox<String> amountOperatorBox, dataTypeBox;
    @FXML private RadioButton singleAmountRadio;
    @FXML private TextField startDateField, endDateField, rankingLimitField;

    private SQLiteDictionaryRepository dictionaryRepository;

    @FXML
    public void initialize() {
        dictionaryRepository = new SQLiteDictionaryRepository(SQLiteDatabaseManager.getInstance());
        amountOperatorBox.setItems(FXCollections.observableArrayList(">=", "<=", "=", ">", "<"));
        amountOperatorBox.getSelectionModel().selectFirst();
        dataTypeBox.setItems(FXCollections.observableArrayList("全部", "信眾點燈", "中元普渡"));
        dataTypeBox.getSelectionModel().selectFirst();
        singleAmountRadio.setSelected(true);
        String today = currentRocDate();
        startDateField.setText(today);
        endDateField.setText(today);

        try {
            dictionaryRepository.migrateFromLegacy();
            List<DictionaryItem> categories = dictionaryRepository.findEnabledItemsByType(SQLiteDictionaryRepository.TYPE_DONATION_LIGHT);
            availableCategoryList.setItems(FXCollections.observableArrayList(categories));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void onAddSelected() {
        DictionaryItem selected = availableCategoryList.getSelectionModel().getSelectedItem();
        if (selected != null && !selectedCategoryList.getItems().contains(selected)) {
            selectedCategoryList.getItems().add(selected);
        }
    }

    @FXML
    private void onAddAll() {
        for (DictionaryItem item : availableCategoryList.getItems()) {
            if (!selectedCategoryList.getItems().contains(item)) {
                selectedCategoryList.getItems().add(item);
            }
        }
    }

    @FXML
    private void onRemoveSelected() {
        DictionaryItem selected = selectedCategoryList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedCategoryList.getItems().remove(selected);
        }
    }

    @FXML
    private void onRemoveAll() {
        selectedCategoryList.getItems().clear();
    }

    @FXML
    private void onPlaceholderAction() {
        AlertDialog.showInfo("查詢統計", "功能尚未實作");
    }

    @FXML
    private void onOpenIncomeReport() throws IOException {
        showModal("收入報表", "income-report.fxml");
    }

    @FXML
    private void onOpenPrintLabels() throws IOException {
        showModal("列印標籤", "print-labels.fxml");
    }

    @FXML
    private void onOpenDonationRanking() throws IOException {
        int limit = parseRankingLimit();
        if (limit <= 0) {
            return;
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("donation-ranking.fxml"));
        Parent root = loader.load();
        DonationRankingController controller = loader.getController();
        controller.setLimit(limit);

        Stage stage = new Stage();
        stage.setTitle("捐款累計金額最多前 " + limit + " 名");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }

    @FXML
    private void onOpenPeopleStatistics() throws IOException {
        showModal("人數統計", "people-statistics.fxml");
    }

    @FXML
    private void onOpenDonationDetails() throws IOException {
        showModal("捐款明細", "donation-details.fxml");
    }

    private void showModal(String title, String fxmlFile) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
        Parent root = loader.load();
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }

    private String currentRocDate() {
        LocalDate today = LocalDate.now();
        return String.format("%03d.%02d.%02d", today.getYear() - 1911, today.getMonthValue(), today.getDayOfMonth());
    }

    private int parseRankingLimit() {
        try {
            int limit = Integer.parseInt(rankingLimitField.getText().trim());
            if (limit > 0) {
                return limit;
            }
        } catch (RuntimeException ignored) {
        }
        AlertDialog.showWarning("捐款排行榜", "前幾名請輸入大於 0 的整數");
        return 0;
    }
}
