package tw.org.il.dongsheng.templeapp.util;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.io.IOException;

public class PaginationBar extends HBox {
    @FXML
    private Button btnFirst, btnPrev, btnNext, btnLast;
    @FXML private TextField txtCurrentPage;
    @FXML private Label lblTotalPages;

    private int currentIndex = 0; // 0-based
    private int totalCount = 0;
    private Runnable onAction; // 當筆次改變時要執行的動作（例如選取表格資料列）

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

        // 監聽文字框輸入筆次直接跳轉
        txtCurrentPage.setOnAction(e -> jumpToRecord());
    }

    // 更新 UI 狀態的方法，由 Controller 調用
    public void setTotalCount(int count) {
        this.totalCount = Math.max(count, 0);
        if (totalCount == 0) {
            currentIndex = 0;
        } else if (currentIndex >= totalCount) {
            currentIndex = totalCount - 1;
        }
        lblTotalPages.setText("/ " + totalCount + " 筆");
        updateButtonStatus();
    }

    public int getPageSize() { return Math.max(totalCount, 1); }
    public int getCurrentPage() { return currentIndex; }
    public int getCurrentIndex() { return currentIndex; }
    public int getOffset() { return 0; }

    public void setCurrentIndex(int index) {
        if (totalCount == 0) {
            currentIndex = 0;
        } else {
            currentIndex = Math.max(0, Math.min(index, totalCount - 1));
        }
        updateButtonStatus();
    }

    public void setOnAction(Runnable action) { this.onAction = action; }

    @FXML private void handleFirst() { moveTo(0); }
    @FXML private void handlePrev() { moveTo(currentIndex - 1); }
    @FXML private void handleNext() { moveTo(currentIndex + 1); }
    @FXML private void handleLast() { moveTo(totalCount - 1); }

    private void jumpToRecord() {
        try {
            int target = Integer.parseInt(txtCurrentPage.getText()) - 1;
            if (target >= 0 && target < totalCount) {
                moveTo(target);
            } else {
                updateButtonStatus();
            }
        } catch (Exception e) {
            updateButtonStatus();
        }
    }

    private void moveTo(int index) {
        setCurrentIndex(index);
        if (onAction != null) {
            onAction.run();
        }
    }

    private void updateButtonStatus() {
        boolean isEmpty = totalCount == 0;
        boolean isFirst = isEmpty || currentIndex == 0;
        boolean isLast = isEmpty || currentIndex >= totalCount - 1;
        btnFirst.setDisable(isFirst);
        btnPrev.setDisable(isFirst);
        btnNext.setDisable(isLast);
        btnLast.setDisable(isLast);
        txtCurrentPage.setText(isEmpty ? "0" : String.valueOf(currentIndex + 1));
    }
}
