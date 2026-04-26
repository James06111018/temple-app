package tw.org.il.dongsheng.templeapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Map;
import java.util.ResourceBundle;

public class IndexController {
    @FXML private ResourceBundle resources;
    @FXML private TabPane mainTabPane;
    @FXML private AnchorPane contentArea;

    private final Map<String, String> pageMap = Map.of(
            "light", "view-light.fxml",
            "staff", "view-staff.fxml",
            "temple", "view-temple.fxml",
            "ghost", "view-light.fxml",
            "merit", "view-merit.fxml"
    );

    @FXML
    public void initialize() {
        mainTabPane.getSelectionModel().selectedItemProperty().addListener(((observable, oldTab, newTab) -> {
            if (newTab != null) {
                loadPageByTab(newTab);
            }
        }));

        if (!mainTabPane.getTabs().isEmpty()) {
            Tab firstTab = mainTabPane.getTabs().get(0);
            mainTabPane.getSelectionModel().select(firstTab);
            loadPageByTab(firstTab);
        }
    }

    private void loadPageByTab(Tab tab) {
        String id = tab.getId();
        String fxmlFile = pageMap.get(id);
        if (fxmlFile != null) {
            String langKey = "tab." + id;
            if (id.equals("merit")) {
                langKey += ".page";
            }
            String title = resources.getString(langKey);
            switchContent(id, fxmlFile, title);
            System.out.println("切換到分頁: " + title);
        }
    }

    private void switchContent(String id, String fxmlFile, String title) {
        try {
            // 1. 載入功能頁面的內容 (例如 BorderPane)
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Node functionNode = loader.load();

            if (id.equals("light") || id.equals("ghost")) {
                LightController controller = loader.getController();
                controller.setType(id);
                controller.initData();
            }

            // 2. 建立標題列容器 (HBox)
            HBox titleBar = new HBox(10); // 間距為 10
            titleBar.setPrefHeight(35); // 設定固定高度
            titleBar.setAlignment(Pos.CENTER_LEFT); // 內容靠左垂直置中
            // 設定背景色 (例如淡藍色，像照片那樣)
            titleBar.setStyle("-fx-background-color: #A6B8D4; -fx-padding: 0 10 0 10; -fx-border-color: #708090; -fx-border-width: 0 0 1px 0;");

            // --- 建立標題列內容 ---

            // a. 專案圖示 (從照片看起來是一個小圖示)
            ImageView iconView = new ImageView();
            try {
                // 請確保您有將專案圖示放到 resources 的正確路徑下
                iconView.setImage(new Image(getClass().getResourceAsStream("icon_light.png")));
            } catch (Exception e) {
                // 如果找不到圖示，可以不設定
            }
            iconView.setFitHeight(20);
            iconView.setFitWidth(20);

            // b. 視窗標題 (Label)
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333333;");
            // 讓標題自動填滿中間空間，將關閉按鈕推到最右邊
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            // c. 彈性空間
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // d. 關閉按鈕 (X)
            Button closeButton = new Button();
            try {
                // 請確保您有將關閉圖示 (times) 放到 resources 下
                ImageView closeIcon = new ImageView(new Image(getClass().getResourceAsStream("icon_close.png")));
                closeIcon.setFitHeight(18);
                closeIcon.setFitWidth(18);
                closeButton.setGraphic(closeIcon);
            } catch (Exception e) {
                closeButton.setText("X"); // 如果找不到圖示則顯示 X
            }
            // 讓關閉按鈕看起來像圓形或小正方形
            closeButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
            // 設定關閉事件 (例如：清空紅框內容)
            closeButton.setOnAction(event -> {
                contentArea.getChildren().clear();

                mainTabPane.getSelectionModel().select(null);
                mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
                System.out.println("功能頁已關閉");
            });

            // 3. 將內容加入標題列
            titleBar.getChildren().addAll(iconView, titleLabel, spacer, closeButton);

            // 4. 建立功能視窗外殼 (VBox)，將標題列和功能內容包起來
            VBox windowWrapper = new VBox();
            VBox.setVgrow(functionNode, Priority.ALWAYS); // 讓功能內容填滿剩餘高度
            windowWrapper.getChildren().addAll(titleBar, functionNode);

            // 5. 將外殼塞入紅框容器中 (AnchorPane)
            contentArea.getChildren().clear();
            AnchorPane.setTopAnchor(windowWrapper, 0.0);
            AnchorPane.setBottomAnchor(windowWrapper, 0.0);
            AnchorPane.setLeftAnchor(windowWrapper, 0.0);
            AnchorPane.setRightAnchor(windowWrapper, 0.0);
            contentArea.getChildren().add(windowWrapper);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("無法載入頁面: " + fxmlFile);
        }
    }

    @FXML
    private void handleExit() {
        javafx.application.Platform.exit();
    }

    @FXML
    public void handelDonationCategory() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("view-donation-category.fxml")
        );
        Parent root = loader.load();
//        DonationCategoryController controller = loader.getController();

        Stage stage = new Stage();
        stage.setTitle("款項類別維護");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);

        stage.showAndWait();
    }
}
