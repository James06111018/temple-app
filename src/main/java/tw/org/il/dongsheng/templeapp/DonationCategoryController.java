package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import tw.org.il.dongsheng.templeapp.model.DonationCategory;
import tw.org.il.dongsheng.templeapp.repository.DonationCategoryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationCategoryRepository;
import tw.org.il.dongsheng.templeapp.service.DonationCategoryService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.sql.SQLException;

public class DonationCategoryController {

    private DonationCategoryService service;

    // ===== 左側 Table =====
    @FXML
    private TableView<DonationCategory> tblCategory;
    @FXML private TableColumn<DonationCategory, String> colCode;
    @FXML private TableColumn<DonationCategory, String> colName;
    @FXML private TableColumn<DonationCategory, Boolean> colEnabled;

    @FXML private TextField txtSearch;

    // ===== 右側表單 =====
    @FXML private TextField txtCode;
    @FXML private TextField txtName;
    @FXML private ComboBox<String> cmbType;
    @FXML private TextField txtAmount;
    @FXML private CheckBox chkEnabled;
    @FXML private TextField txtSort;
    @FXML private TextArea txtRemark;

    // ===== 按鈕 =====
    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnSave;
    @FXML private Button btnDisabled;
    @FXML private Button btnCancel;

    // ===== 狀態 =====
    private enum Mode { VIEW, ADD, EDIT }
    private Mode currentMode = Mode.VIEW;

    // ===== 資料 =====
    private ObservableList<DonationCategory> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() throws SQLException {
        try {
            DonationCategoryRepository repo = new SQLiteDonationCategoryRepository(SQLiteDatabaseManager.getInstance());
            repo.createTable();
            service = new DonationCategoryService(repo);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Table 欄位綁定
        colCode.setCellValueFactory(data -> data.getValue().codeProperty());
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colEnabled.setCellValueFactory(data -> data.getValue().enabledProperty());

        // ComboBox TODO 先用固定，後續再看需不需要抽出來或是不需要
        cmbType.setItems(FXCollections.observableArrayList(
                "點燈", "法會", "捐款"
        ));

        // 初始化資料
//        initMockData();
        masterData = Util.toObservableList(service.findAll());

        tblCategory.setItems(masterData);

        // 點擊列 → 帶入表單
        tblCategory.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadToForm(newVal);
                setMode(Mode.VIEW);
            }
        });

        // 搜尋（簡單版）
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            filterData(newVal);
        });

        // 按鈕事件 TODO 後續加入帳號權限控管
        btnAdd.setOnAction(e -> onAdd());
        btnEdit.setOnAction(e -> onEdit());
        btnSave.setOnAction(e -> onSave());
        btnDisabled.setOnAction(e -> onDisabled());
        btnCancel.setOnAction(e -> onCancel());

        setMode(Mode.VIEW);
    }

    // ===== 初始化假資料 =====
    private void initMockData() {
        masterData.addAll(
                new DonationCategory("L001", "光明燈", "點燈", 600, true, 1, ""),
                new DonationCategory("T001", "安太歲", "點燈", 800, true, 2, ""),
                new DonationCategory("M001", "媽祖燈", "點燈", 500, true, 3, ""),
                new DonationCategory("F001", "中元普渡", "法會", 0, true, 4, ""),
                new DonationCategory("O001", "其他收入", "捐款", 0, true, 5, "")
        );
    }

    // ===== 載入表單 =====
    private void loadToForm(DonationCategory item) {
        txtCode.setText(item.getCode());
        txtName.setText(item.getName());
        cmbType.setValue(item.getType());
        txtAmount.setText(String.valueOf(item.getAmount()));
        chkEnabled.setSelected(item.isEnabled());
        txtSort.setText(String.valueOf(item.getSort()));
        txtRemark.setText(item.getRemark());
    }

    // ===== 清空表單 =====
    private void clearForm() {
        txtCode.clear();
        txtName.clear();
        cmbType.setValue(null);
        txtAmount.clear();
        chkEnabled.setSelected(true);
        txtSort.clear();
        txtRemark.clear();
    }

    // ===== 狀態控制 =====
    private void setMode(Mode mode) {
        this.currentMode = mode;

        boolean editable = (mode == Mode.ADD || mode == Mode.EDIT);

        txtCode.setDisable(!editable);
        txtName.setDisable(!editable);
        cmbType.setDisable(!editable);
        txtAmount.setDisable(!editable);
        chkEnabled.setDisable(!editable);
        txtSort.setDisable(!editable);
        txtRemark.setDisable(!editable);

        btnSave.setDisable(!editable);
        btnCancel.setDisable(!editable);

        btnAdd.setDisable(editable);
        btnEdit.setDisable(editable);
        btnDisabled.setDisable(editable);
    }

    // ===== 按鈕行為 =====
    private void onAdd() {
        clearForm();
        setMode(Mode.ADD);
    }

    private void onEdit() {
        if (tblCategory.getSelectionModel().getSelectedItem() == null) return;
        setMode(Mode.EDIT);
    }

    private void onSave() {
        try {
            if (currentMode == Mode.ADD) {
                DonationCategory newItem = buildFromForm();

                if (newItem != null) {
                    masterData.add(service.save(newItem));
                }
            } else if (currentMode == Mode.EDIT) {
                DonationCategory selected = tblCategory.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    if (service.update(selected)) {
                        updateItem(selected);
                        tblCategory.refresh();
                    }
                }
            }
            setMode(Mode.VIEW);
        } catch (SQLException e) {
            AlertDialog.showError(null, "儲存失敗!!");
            e.printStackTrace();
        }
    }

    private void onDisabled() {
        try {
            DonationCategory selected = tblCategory.getSelectionModel().getSelectedItem();
            if (selected != null) {
                service.disable(selected.getCode());
                selected.setEnabled(false);
                tblCategory.refresh();
            }
        } catch (SQLException e) {
            AlertDialog.showError(null, "停用失敗!!");
            e.printStackTrace();
        }
    }

    private void onCancel() {
        DonationCategory selected = tblCategory.getSelectionModel().getSelectedItem();
        if (selected != null) {
            loadToForm(selected);
        } else {
            clearForm();
        }
        setMode(Mode.VIEW);
    }

    // ===== 表單 → 物件 =====
    private DonationCategory buildFromForm() {
        if(Util.isEmpty(txtCode.getText())) {
            AlertDialog.showInfo(null, "請輸入類別代碼");
            return null;
        }
        if(Util.isEmpty(txtName.getText())) {
            AlertDialog.showInfo(null, "請輸入類別名稱");
            return null;
        }

        return new DonationCategory(
                txtCode.getText(),
                txtName.getText(),
                cmbType.getValue(),
                parseInt(txtAmount.getText()),
                chkEnabled.isSelected(),
                parseInt(txtSort.getText()),
                txtRemark.getText()
        );
    }

    private void updateItem(DonationCategory item) {
        item.setCode(txtCode.getText());
        item.setName(txtName.getText());
        item.setType(cmbType.getValue());
        item.setAmount(parseInt(txtAmount.getText()));
        item.setEnabled(chkEnabled.isSelected());
        item.setSort(parseInt(txtSort.getText()));
        item.setRemark(txtRemark.getText());
    }

    private int parseInt(String val) {
        try { return Integer.parseInt(val); }
        catch (Exception e) { return 0; }
    }

    // ===== 搜尋 =====
    private void filterData(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            tblCategory.setItems(masterData);
            return;
        }

        ObservableList<DonationCategory> filtered = FXCollections.observableArrayList();

        for (DonationCategory item : masterData) {
            if (item.getName().contains(keyword) || item.getCode().contains(keyword)) {
                filtered.add(item);
            }
        }

        tblCategory.setItems(filtered);
    }

}
