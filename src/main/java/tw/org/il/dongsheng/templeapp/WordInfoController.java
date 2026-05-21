package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.CustomChar;
import tw.org.il.dongsheng.templeapp.repository.CustomCharRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteCustomCharRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class WordInfoController {
    @FXML private TabPane wordTabPane;
    @FXML private Label platformHintLabel;

    private final boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    private List<CustomChar> customChars = new ArrayList<>();
    private Consumer<String> onSelected;

    public void setOnSelected(Consumer<String> onSelected) {
        this.onSelected = onSelected;
    }

    @FXML
    public void initialize() {
        loadCustomChars();
        platformHintLabel.setText(windows
                ? "Windows 模式：顯示造字字形，更新後可開啟 TrueType 造字程式"
                : "macOS 模式：顯示造字字碼；實際字形需於 Windows EUDC/字型環境查看");

        for (int page = 1; page <= 16; page++) {
            Tab tab = new Tab(String.valueOf(page));
            tab.setClosable(false);
            tab.setContent(createWordGrid(page));
            wordTabPane.getTabs().add(tab);
        }
        wordTabPane.getSelectionModel().selectFirst();
    }

    @FXML
    private void onPrint() {
        AlertDialog.showInfo("造字資訊", "列印功能尚未設定");
    }

    @FXML
    private void onUpdate() {
        if (!windows) {
            AlertDialog.showInfo("造字資訊", "造字功能僅支援 Windows；macOS 僅顯示造字代碼。");
            return;
        }

        AlertDialog.showInfo("造字資訊", "更新成功");
        openWindowsEudcEditor();
    }

    private GridPane createWordGrid(int page) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(24, 20, 20, 20));

        int wordOffset = (page - 1) * 50;
        for (int index = 0; index < 50; index++) {
            Button button = new Button();
            button.setMinSize(76, 66);
            button.setPrefSize(76, 66);
            button.setStyle(windows ? "-fx-font-size: 32px;" : "-fx-font-size: 18px;");

            int wordIndex = wordOffset + index;
            if (wordIndex < customChars.size()) {
                CustomChar customChar = customChars.get(wordIndex);
                button.setText(windows ? customChar.getCharValue() : customChar.getCode());
                button.setTooltip(new javafx.scene.control.Tooltip(customChar.getCode()));
                button.setOnAction(event -> onSelect(customChar));
            }

            grid.add(button, index % 10, index / 10);
        }

        return grid;
    }

    private void onSelect(CustomChar customChar) {
        if (onSelected != null) {
            String value = windows ? customChar.getCharValue() : customChar.getCode();
            onSelected.accept(value);
        }

        Stage stage = (Stage) wordTabPane.getScene().getWindow();
        stage.close();
    }

    private void loadCustomChars() {
        try {
            CustomCharRepository repository = new SQLiteCustomCharRepository(SQLiteDatabaseManager.getInstance());
            repository.createTable();
            repository.seedDefaults();
            customChars = repository.findEnabled();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void openWindowsEudcEditor() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("win")) {
            return;
        }

        try {
            new ProcessBuilder("eudcedit.exe").start();
        } catch (IOException e) {
            AlertDialog.showError("造字資訊", "無法開啟 TrueType 造字程式");
        }
    }
}
