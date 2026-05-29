package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

public class PeopleStatisticsController {

    @FXML private RadioButton allZipRadio;
    @FXML private ComboBox<String> reportTypeBox;
    @FXML private PaginationBar peopleStatisticsPageBar;
    @FXML private TableView<?> peopleStatisticsTable;

    @FXML
    private void initialize() {
        allZipRadio.setSelected(true);
        reportTypeBox.setItems(FXCollections.observableArrayList(
                "1. 郵寄標籤(橫式)",
                "2. 郵寄標籤(直式)",
                "3. 名冊",
                "4. 通信函"
        ));
        reportTypeBox.getSelectionModel().selectFirst();
        peopleStatisticsPageBar.setTotalCount(0);
    }
}
