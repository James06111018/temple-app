package tw.org.il.dongsheng.templeapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ResourceBundle;

public class TempleApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        ResourceBundle bundle = ResourceBundle.getBundle("tw.org.il.dongsheng.templeapp.strings");

        FXMLLoader fxmlLoader = new FXMLLoader(TempleApplication.class.getResource("view-index.fxml"), bundle);
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root, 1200, 800, Color.WHITE);
        stage.setTitle(bundle.getString("app.title"));
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        System.setProperty("glass.accessible.force", "false");
        launch(args);
    }
}
