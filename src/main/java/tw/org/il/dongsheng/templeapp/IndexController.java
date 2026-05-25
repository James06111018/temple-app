package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
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
    @FXML private Menu menuWindow;
    @FXML MenuItem menuItemAbout;

    private final Map<String, OpenPage> openPages = new java.util.LinkedHashMap<>();
    private boolean updatingTabSelection = false;

    private final Map<String, String> pageMap = Map.of(
            "light", "view-light.fxml",
//            "staff", "view-staff.fxml",
//            "temple", "view-temple.fxml",
            "ghost", "view-light.fxml",
            "merit", "view-merit.fxml"
    );

    @FXML
    public void initialize() {
        mainTabPane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !updatingTabSelection) {
                Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
                if (selectedTab != null) {
                    loadPageByTab(selectedTab);
                }
            }
        });

        clearSelectedTab();

        menuItemAbout.setText("版本 (v" + AppConfig.getVersion() + ")");
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
        // 已經開過 → 切換到最上層
        if (openPages.containsKey(id)) {
            bringToFront(id);
            return;
        }

        try {
            // 1. 載入功能頁面的內容 (例如 BorderPane)
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Node functionNode = loader.load();

            if (id.equals("light") || id.equals("ghost")) {
                LightController controller = loader.getController();
                controller.setType(id);
                controller.initData();
            }

            VBox windowWrapper = createWindowWrapper(id, title, functionNode);

            // 5. 將外殼塞入紅框容器中 (AnchorPane)
//            contentArea.getChildren().clear();
//            AnchorPane.setTopAnchor(windowWrapper, 0.0);
//            AnchorPane.setLeftAnchor(windowWrapper, 0.0);
//            AnchorPane.setBottomAnchor(windowWrapper, 0.0);
//            AnchorPane.setRightAnchor(windowWrapper, 0.0);

            windowWrapper.relocate(
                    5 + openPages.size() * 25,
                    5 + openPages.size() * 25
            );

            contentArea.getChildren().add(windowWrapper);

            // 6. 將開啟的頁面加入
            CheckMenuItem menuItem = new CheckMenuItem(title);
            menuItem.setSelected(true);
            menuItem.setOnAction(e -> bringToFront(id));

            OpenPage page = new OpenPage(id, title, windowWrapper, menuItem);
            openPages.put(id, page);

            menuWindow.getItems().add(menuItem);

            bringToFront(id);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("無法載入頁面: " + fxmlFile);
        }
    }

    private VBox createWindowWrapper(String id, String title, Node functionNode) {
        // 2. 建立標題列容器 (HBox)
        HBox titleBar = new HBox(10); // 間距為 10
        titleBar.setPrefHeight(35); // 設定固定高度
        titleBar.setAlignment(Pos.CENTER_LEFT); // 內容靠左垂直置中
        // 設定背景色 (例如淡藍色，像照片那樣)
        titleBar.setStyle(
                "-fx-background-color: #A6B8D4; " +
                "-fx-padding: 0 10 0 10; " +
                "-fx-border-color: #708090; " +
                "-fx-border-width: 0 0 1px 0;"
        );

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
        closeButton.setOnAction(event -> closePage(id));

        // 3. 將內容加入標題列
        titleBar.getChildren().addAll(iconView, titleLabel, spacer, closeButton);

        // 4. 建立功能視窗外殼 (VBox)，將標題列和功能內容包起來
        VBox windowWrapper = new VBox();
        VBox.setVgrow(functionNode, Priority.ALWAYS); // 讓功能內容填滿剩餘高度
        windowWrapper.getChildren().addAll(titleBar, functionNode);

        final Delta dragDelta = new Delta();

        titleBar.setOnMousePressed(e -> {
            dragDelta.x = e.getSceneX() - windowWrapper.getLayoutX();
            dragDelta.y = e.getSceneY() - windowWrapper.getLayoutY();
            windowWrapper.toFront();
        });

        titleBar.setOnMouseDragged(e -> {
            double newX = e.getSceneX() - dragDelta.x;
            double newY = e.getSceneY() - dragDelta.y;

            // 🔥 父容器大小
            double parentWidth = contentArea.getWidth();
            double parentHeight = contentArea.getHeight();

            // 🔥 視窗大小
            double nodeWidth = windowWrapper.getWidth();
            double nodeHeight = windowWrapper.getHeight();

            // 🔥 限制 X 範圍
            if (newX < 0) newX = 0;
            if (newX > parentWidth - nodeWidth) {
                newX = parentWidth - nodeWidth;
            }

            // 🔥 限制 Y 範圍
            if (newY < 0) newY = 0;
            if (newY > parentHeight - nodeHeight) {
                newY = parentHeight - nodeHeight;
            }

            windowWrapper.setLayoutX(newX);
            windowWrapper.setLayoutY(newY);
        });

        return windowWrapper;
    }

    private void closePage(String id) {
        OpenPage page = openPages.remove(id);

        if (page == null) return;

        contentArea.getChildren().remove(page.wrapper);
        menuWindow.getItems().remove(page.menuItem);

        // 關閉後，切到最後一個仍開著的頁面
        if (!openPages.isEmpty()) {
            String lastId = null;
            for (String key : openPages.keySet()) {
                lastId = key;
            }
            bringToFront(lastId);
        } else {
            clearSelectedTab();
        }
    }

    private void bringToFront(String id) {
        OpenPage target = openPages.get(id);
        if (target == null) return;

        target.wrapper.toFront();

        for (OpenPage page : openPages.values()) {
            page.menuItem.setSelected(page.id.equals(id));
        }
        selectTab(id);
    }

    private void selectTab(String id) {
        updatingTabSelection = true;
        try {
            mainTabPane.getTabs().stream()
                    .filter(tab -> id.equals(tab.getId()))
                    .findFirst()
                    .ifPresent(tab -> mainTabPane.getSelectionModel().select(tab));
        } finally {
            updatingTabSelection = false;
        }
    }

    private void clearSelectedTab() {
        updatingTabSelection = true;
        try {
            mainTabPane.getSelectionModel().clearSelection();
        } finally {
            updatingTabSelection = false;
        }
    }

    @FXML
    private void handleExit() {
        javafx.application.Platform.exit();
    }

    @FXML
    public void handleDictionary() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("dictionary.fxml")
        );
        Parent root = loader.load();

        Stage stage = new Stage();
        stage.setTitle("詞彙設定");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);

        stage.showAndWait();
    }

    @FXML
    public void handleParameterSettings() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("parameter-settings.fxml")
        );
        Parent root = loader.load();

        Stage stage = new Stage();
        stage.setTitle("參數設定");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);

        stage.showAndWait();
    }

    private static class OpenPage {
        String id;
        String title;
        VBox wrapper;
        CheckMenuItem menuItem;

        OpenPage(String id, String title, VBox wrapper, CheckMenuItem menuItem) {
            this.id = id;
            this.title = title;
            this.wrapper = wrapper;
            this.menuItem = menuItem;
        }
    }

    private static class Delta {
        double x;
        double y;
    }

}
