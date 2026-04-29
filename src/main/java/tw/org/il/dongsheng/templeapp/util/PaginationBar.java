package tw.org.il.dongsheng.templeapp.util;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.io.IOException;

public class PaginationBar extends HBox {
    @FXML
    private Button btnFirst, btnPrev, btnNext, btnLast;
    @FXML private TextField txtCurrentPage;
    @FXML private Label lblTotalPages, lblTotalCount;
    @FXML private ComboBox<Integer> comboPageSize;

    private int currentPage = 0; // 0-based
    private int totalCount = 0;
    private Runnable onAction; // 當頁碼改變時要執行的動作（例如去資料庫抓資料）

    public PaginationBar() {
        // 加載 FXML
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("PaginationBar.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        initComboBox();
        // 監聽文字框輸入頁碼直接跳轉
        txtCurrentPage.setOnAction(e -> jumpToPage());
    }

    private void initComboBox() {
        comboPageSize.getItems().addAll(20, 50, 100, 200);
        comboPageSize.setValue(20);
        comboPageSize.setOnAction(e -> {
            currentPage = 0; // 更換每頁筆數時回到第一頁
            if (onAction != null) onAction.run();
        });
    }

    // 更新 UI 狀態的方法，由 Controller 調用
    public void setTotalCount(int count) {
        this.totalCount = count;
        int totalPages = getTotalPages();
        lblTotalPages.setText("/ " + totalPages + " 頁");
        lblTotalCount.setText("總筆數：" + totalCount);
        updateButtonStatus();
    }

    public int getPageSize() { return comboPageSize.getValue(); }
    public int getCurrentPage() { return currentPage; }
    public int getOffset() { return currentPage * getPageSize(); } // 直接給 SQL 使用

    public void setOnAction(Runnable action) { this.onAction = action; }

    @FXML private void handleFirst() { currentPage = 0; onAction.run(); }
    @FXML private void handlePrev() { currentPage--; onAction.run(); }
    @FXML private void handleNext() { currentPage++; onAction.run(); }
    @FXML private void handleLast() { currentPage = getTotalPages() - 1; onAction.run(); }

    private void jumpToPage() {
        try {
            int target = Integer.parseInt(txtCurrentPage.getText()) - 1;
            if (target >= 0 && target < getTotalPages()) {
                currentPage = target;
                onAction.run();
            }
        } catch (Exception e) { txtCurrentPage.setText(String.valueOf(currentPage + 1)); }
    }

    private int getTotalPages() {
        return (int) Math.ceil((double) totalCount / getPageSize());
    }

    private void updateButtonStatus() {
        boolean isFirst = (currentPage == 0);
        boolean isLast = (currentPage >= getTotalPages() - 1 || getTotalPages() == 0);
        btnFirst.setDisable(isFirst);
        btnPrev.setDisable(isFirst);
        btnNext.setDisable(isLast);
        btnLast.setDisable(isLast);
        txtCurrentPage.setText(String.valueOf(currentPage + 1));
    }
}
