package tw.org.il.dongsheng.templeapp;

import javafx.fxml.FXML;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;

public class DonationRankingController {

    @FXML private PaginationBar rankingPageBar;

    @FXML
    private void initialize() {
        rankingPageBar.setTotalCount(0);
    }
}
