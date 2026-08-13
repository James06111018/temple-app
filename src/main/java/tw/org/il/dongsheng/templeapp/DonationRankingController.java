package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import tw.org.il.dongsheng.templeapp.model.DonationRankingRow;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.service.DonationService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.DonationRankingReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;

public class DonationRankingController {

    @FXML private TableView<DonationRankingRow> rankingTable;
    @FXML private TableColumn<DonationRankingRow, String> rankColumn;
    @FXML private TableColumn<DonationRankingRow, String> nameColumn;
    @FXML private TableColumn<DonationRankingRow, String> memberIdColumn;
    @FXML private TableColumn<DonationRankingRow, String> totalAmountColumn;
    @FXML private TableColumn<DonationRankingRow, String> totalCountColumn;
    @FXML private PaginationBar rankingPageBar;

    private final DonationService donationService = new DonationService(
            new SQLiteDonationRepository(SQLiteDatabaseManager.getInstance())
    );
    private List<DonationRankingRow> rows = List.of();

    @FXML
    private void initialize() {
        rankColumn.setCellValueFactory(cell -> stringValue(""));
        nameColumn.setCellValueFactory(cell -> stringValue(cell.getValue().memberName()));
        memberIdColumn.setCellValueFactory(cell -> stringValue(Util.stringFormat(cell.getValue().memberId())));
        totalAmountColumn.setCellValueFactory(cell -> stringValue(formatAmount(cell.getValue().totalAmount())));
        totalCountColumn.setCellValueFactory(cell -> stringValue(cell.getValue().totalCount()));
        totalAmountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        totalCountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        rankingPageBar.setTotalCount(0);
        rankingPageBar.setOnAction(() -> selectRow(rankingPageBar.getCurrentIndex()));
        rankingTable.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, selectedIndex) -> {
            if (selectedIndex.intValue() >= 0) {
                rankingPageBar.setCurrentIndex(selectedIndex.intValue());
            }
        });
    }

    public void setLimit(int limit) {
        try {
            rows = donationService.findRanking(limit);
            rankingTable.setItems(FXCollections.observableArrayList(rows));
            rankingPageBar.setTotalCount(rows.size());
            selectRow(rows.isEmpty() ? -1 : 0);
        } catch (SQLException exception) {
            rows = List.of();
            rankingTable.getItems().clear();
            rankingPageBar.setTotalCount(0);
            AlertDialog.showError("捐款排行榜", "讀取捐款排行榜失敗：" + exception.getMessage());
        }
    }

    @FXML
    private void onPrintRoster() {
        if (rows.isEmpty()) {
            AlertDialog.showInfo("捐款排行榜", "沒有可列印的捐款資料");
            return;
        }
        PrintPreview.show(
                rankingTable.getScene().getWindow(),
                "捐款排行榜",
                DonationRankingReportBuilder.buildPages(rows)
        );
    }

    private void selectRow(int index) {
        if (index < 0 || index >= rankingTable.getItems().size()) {
            rankingTable.getSelectionModel().clearSelection();
            return;
        }
        rankingTable.getSelectionModel().select(index);
        rankingTable.scrollTo(index);
    }

    private SimpleStringProperty stringValue(Object value) {
        return new SimpleStringProperty(value == null ? "" : String.valueOf(value));
    }

    private String formatAmount(long amount) {
        return NumberFormat.getIntegerInstance().format(amount);
    }
}
