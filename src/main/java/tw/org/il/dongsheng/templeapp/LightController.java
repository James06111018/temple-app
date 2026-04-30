package tw.org.il.dongsheng.templeapp;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationCategory;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.DonationCategoryRepository;
import tw.org.il.dongsheng.templeapp.repository.DonationRepository;
import tw.org.il.dongsheng.templeapp.repository.LightMemberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationCategoryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.service.DonationCategoryService;
import tw.org.il.dongsheng.templeapp.service.DonationService;
import tw.org.il.dongsheng.templeapp.service.LightMemberService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.AreaUtil;
import tw.org.il.dongsheng.templeapp.util.PaginationBar;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * 信眾點燈 / 中元普渡 共用頁面
 */
public class LightController {

    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private LightMemberService lightService;
    private DonationService donationService;
    private DonationCategoryService donationCategoryService;

    @FXML GridPane memberInputGrid;
    @FXML private TextField idField, nameField, phoneField, zipCodeField, addressField
            , birthSunField, birthMoonField, ageField, zodiacField, yearCycleField, hourField
            , memberNoteField, contactField, idNumberField, sortField, aField, bField;
    @FXML private ComboBox<String> genderBox, cityBox, distBox, mailBox;

    @FXML GridPane donateInputGrid;
    @FXML private ComboBox<DonationCategory> donateTypeField;
    @FXML private TextField receiptNoField, extraNoField, amountField, summaryField, donateNoteField
            , otherNoteField, donorNoField, lightNoField, shouldPayField;
    @FXML private DatePicker donateDateField;

    @FXML private Button btnContact, btnWord, btnToggle;

    @FXML private SplitPane splitPane;
    private boolean isSplitMember = true;

    @FXML
    private TableView<LightMember> memberTable;
    @FXML private TableColumn<LightMember, String> colName, colMail, colSolar, colLunar, colZodiac, colEra, colHour, colGender;
    @FXML private TableColumn<LightMember, Integer> colId, colAge;

    @FXML
    private TableView<Donation> donationTable;
    @FXML private TableColumn<Donation, String> colReceiptNo, colDonateDate, colExtraNo, colDonationType, colSummary, colDonateNote
            , colOtherNote, colDonorNo, colLightNo, colSeqNo, coCreator;
    @FXML private TableColumn<Donation, Integer> colAmount, colShouldPay;

    @FXML private PaginationBar memberPageBar, donationPageBar;

    private List<LightMember> allMember = new LinkedList<>(); // 查詢的信眾，所有的家屬(含自已)
    private Map<String, String> categoryMap = new LinkedHashMap<>(); // 捐款類別資料: id, code-name

    private String type;

    public void setType(String type) {
        this.type = type;
    }

    @FXML
    public void initialize() {
        System.out.println("View LightController 初始化中...");

        UnaryOperator<TextFormatter.Change> dateFilter = change -> {
            String text = change.getControlNewText();
            if (text.matches("\\d{0,3}(\\.\\d{0,2}){0,2}")) {
                return change;
            }
            return null;
        };

        birthSunField.setTextFormatter(new TextFormatter<>(dateFilter));
        birthMoonField.setTextFormatter(new TextFormatter<>(dateFilter));

        birthSunField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                birthSunField.setText(normalizeRocDate(birthSunField.getText()));
            }
        });
        birthMoonField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (!focused) {
                birthMoonField.setText(normalizeRocDate(birthMoonField.getText()));
            }
        });
        birthMoonField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isValidRocDateFormat(newVal)) {
                return;
            }
            LocalDate date = parseRocDate(newVal);
            if (date != null) {
                // 年齡
                Integer age = calculateTraditionAge(date.getYear());
                ageField.setText(age != null ? String.valueOf(age) : "");
                // 生肖
                zodiacField.setText(getZodiac(date.getYear()));
                // 歲次
                yearCycleField.setText(getYearCycle(date.getYear()));
            } else {
                ageField.clear();
                zodiacField.clear();
                yearCycleField.clear();
            }
        });

        Map<String, List<String>> areaMap = AreaUtil.getAllTaiwanAreas();
        cityBox.getItems().addAll(areaMap.keySet());
        cityBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                distBox.getItems().clear();
                distBox.getItems().addAll(areaMap.get(newVal));
                distBox.getSelectionModel().selectFirst();
            }
        });

        showTooltip(btnContact, "連結往來寺廟");
        showTooltip(btnWord, "造字資訊");

        // 讓分割線不接受滑鼠事件
        Platform.runLater(() -> {
            splitPane.lookupAll(".split-pane-divider").forEach(node -> {
                node.setMouseTransparent(true);
            });
        });
        // 初始比例（左40% 右60%）
        splitPane.setDividerPositions(0.9);

        // 視窗變動時維持比例（重要）
        splitPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            splitPane.setDividerPositions(0.9);
        });

        // 設定每個欄位對應 LightMember 類別的屬性名稱 (變數名)
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) { // 這裡改為 Integer
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(Util.stringFormat(item));
                }
            }
        });
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colMail.setCellValueFactory(new PropertyValueFactory<>("isMail"));
        colSolar.setCellValueFactory(new PropertyValueFactory<>("birthDate"));
        colLunar.setCellValueFactory(new PropertyValueFactory<>("lunarBirthDate"));
        colAge.setCellValueFactory(new PropertyValueFactory<>("age"));
        colZodiac.setCellValueFactory(new PropertyValueFactory<>("zodiac"));
        colEra.setCellValueFactory(new PropertyValueFactory<>("zodiacYear"));
        colHour.setCellValueFactory(new PropertyValueFactory<>("birthTime"));
        colGender.setCellValueFactory(new PropertyValueFactory<>("gender"));

        memberTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                setMemberData(newVal);
            }
        });

        memberPageBar.setTotalCount(0);
        memberPageBar.setOnAction(()-> executeMemberSearch());

        // 設定每個欄位對應 Donation 類別的屬性名稱 (變數名)
        colReceiptNo.setCellValueFactory(new PropertyValueFactory<>("receiptNo"));
        colReceiptNo.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(Util.stringFormat(Integer.parseInt(item)));
                }
            }
        });
        colDonateDate.setCellValueFactory(new PropertyValueFactory<>("donateDate"));
        colExtraNo.setCellValueFactory(new PropertyValueFactory<>("extraNo"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colDonationType.setCellValueFactory(new PropertyValueFactory<>("donateType"));
        colDonationType.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if(empty || item == null) {
                    setText(null);
                } else {
                    setText(categoryMap.get(item));
                }
            }
        });
        colSummary.setCellValueFactory(new PropertyValueFactory<>("summary"));
        colDonateNote.setCellValueFactory(new PropertyValueFactory<>("donateNote"));
        colOtherNote.setCellValueFactory(new PropertyValueFactory<>("otherNote"));
        colDonorNo.setCellValueFactory(new PropertyValueFactory<>("donorNo"));
        colLightNo.setCellValueFactory(new PropertyValueFactory<>("lightNo"));
        colShouldPay.setCellValueFactory(new PropertyValueFactory<>("shouldPay"));
//        colSeqNo.setCellValueFactory(new PropertyValueFactory<>(""));
        coCreator.setCellValueFactory(new PropertyValueFactory<>("creator"));

        donationPageBar.setTotalCount(0);
        donationPageBar.setOnAction(()-> {
            try {
                executeDonationSearch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void initData() {
        try {
            if (type.equals("light")) {
                SQLiteDatabaseManager manager = SQLiteDatabaseManager.getInstance();
                LightMemberRepository lightMemberRepo = new SQLiteLightMemberRepository(manager);
                lightMemberRepo.createTable();
                lightService = new LightMemberService(lightMemberRepo);

                DonationRepository donationRepo = new SQLiteDonationRepository(manager);
                donationRepo.createTable();
                donationService = new DonationService(donationRepo);

                DonationCategoryRepository donationCategoryRepo = new SQLiteDonationCategoryRepository(manager);
                donationCategoryRepo.createTable();
                donationCategoryService = new DonationCategoryService(donationCategoryRepo);

                // 捐款作業
                initDonation();

            } else if (type.equals("ghost")) {

            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void initDonation() {
        try {
            List<DonationCategory> categories = donationCategoryService.findAll().stream()
                    .filter(DonationCategory::isEnabled).toList();

            donateTypeField.setItems(Util.toObservableList(categories));

            categories.stream().forEach(action-> categoryMap.put(String.valueOf(action.getId()), action.getCode() + " - " + action.getName()));

            donateTypeField.setOnAction(e -> {
                DonationCategory d = donateTypeField.getValue();
                if (d != null) {
                    amountField.setText(String.valueOf(d.getAmount()));
                }
            });
            donateDateField.setConverter(new StringConverter<>() {
                @Override
                public String toString(LocalDate date) {
                    if (date != null) {
                        return dateFormatter.format(date);
                    }
                    return "";
                }

                @Override
                public LocalDate fromString(String string) {
                    if (string != null && !string.isEmpty()) {
                        return LocalDate.parse(string, dateFormatter);
                    }
                    return null;
                }
            });
            donateDateField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    try {
                        donateDateField.setValue(
                                donateDateField.getConverter().fromString(donateDateField.getEditor().getText()));
                    } catch (Exception e) {
                        donateDateField.getEditor().clear();
                    }
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @FXML
    public void onGenerateId() {
        try {
            int nextId = lightService.getNextId();
            String result = Util.stringFormat(nextId);
            idField.setText(result);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    public void onSearch() {
        String name = nameField.getText();
        if( name.isEmpty() || name.isBlank()) {
            AlertDialog.showInfo("信眾點燈", "請輸入欲查詢姓名");
            return;
        }

        executeMemberSearch();
    }

    @FXML
    public void onSave() {
        if (!validateForm()) {
            return;
        }

        Integer id = null;

        if (!idField.getText().isEmpty()) {
            id = Integer.parseInt(idField.getText());
        }

        LightMember member = new LightMember(
                id,
                nameField.getText(),
                phoneField.getText(),
                cityBox.getValue(),
                distBox.getValue(),
                addressField.getText(),
                zipCodeField.getText(),
                birthSunField.getText(),
                birthMoonField.getText(),
                Util.parseInteger(ageField.getText()),
                zodiacField.getText(),
                yearCycleField.getText(),
                hourField.getText(),
                memberNoteField.getText(),
                contactField.getText(),
                idNumberField.getText(),
                Util.parseInteger(sortField.getText()),
                Util.parseInteger(aField.getText()),
                Util.parseInteger(bField.getText()),
                mailBox.getValue(),
                genderBox.getValue()
        );

        try {
            // 判斷新增或修改
            if (id == null || !lightService.exists(id)) {
                lightService.save(member);
                AlertDialog.showInfo("信眾點燈", "新增信眾成功");
            } else {
                lightService.update(member);
                AlertDialog.showInfo("信眾點燈", "修改信眾成功");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        resetUI();
    }

    private void resetUI() {
        clearForm((memberInputGrid));

        memberTable.setItems(FXCollections.observableArrayList());
        memberPageBar.setTotalCount(0);
        memberTable.getSelectionModel().clearSelection();

        donationTable.setItems(FXCollections.observableArrayList());
        donationPageBar.setTotalCount(0);
    }

    @FXML
    public void onClearField() {
        clearForm(memberInputGrid);
        clearForm(donateInputGrid);
        clearAllErrors();
        clearDonationErrors();
        memberTable.getItems().clear();
    }

    @FXML
    public void onOpenKeypad(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("number-keypad.fxml")
        );

        Parent root = loader.load();

        NumberKeypadController controller = loader.getController();

        Button source = (Button) event.getSource();
        String targetId = (String) source.getUserData();
        // 回傳值
        controller.setOnConfirm(value -> {
            TextField target = null;
            switch (targetId) {
                case "txtPhone":
                    target = phoneField;
                    break;
                case "txtAmount":
                    target = amountField;
                    break;
            }
            openKeypad(target, value);
        });

        Stage stage = new Stage();
        stage.setTitle("數字輸入器");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);

        stage.showAndWait();
    }

    @FXML
    public void onOpenHourPicker() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("hour-picker.fxml")
        );

        Parent root = loader.load();

        HourPickerController controller = loader.getController();

        controller.setOnSelected(item -> {
            hourField.setText(item.getName());
        });

        Stage stage = new Stage();
        stage.setTitle("時辰");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);

        stage.showAndWait();
    }

    @FXML
    public void onDonationAdd() {
        boolean hasId = true;
        String id = idField.getText();
        if (Util.isEmpty(id)) {
            hasId = false;
        } else {
            try {
                if(!lightService.exists(Util.parseInteger(id))) {
                    hasId = false;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        if (!hasId) {
            AlertDialog.showError(null, "無此信眾的電腦編號");
            return;
        }

        if (!validateDonationForm()) {
            return;
        }

        // TODO 新增 creator登入的姓名
        Donation donation = new Donation(
                null,
                Util.parseInteger(Util.stringReplaceZero(id)),
                receiptNoField.getText(),
                donateDateField.getValue().format(dateFormatter),
                extraNoField.getText(),
                Util.parseInteger(amountField.getText()),
                summaryField.getText(),
                donateNoteField.getText(),
                otherNoteField.getText(),
                donorNoField.getText(),
                lightNoField.getText(),
                Util.parseInteger(shouldPayField.getText()),
                String.valueOf(donateTypeField.getValue().getId()),
                "LOGIN"
        );

        try {
            Donation newDonation = donationService.save(donation);
            AlertDialog.showInfo("信眾點燈", "新增捐款作業成功");
            donationTable.getItems().add(0, newDonation);
//            donationTable.getSelectionModel().select(0);
            donationTable.scrollTo(0);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        clearForm(donateInputGrid);
    }

    @FXML
    public void onSplitChange() {
        if (isSplitMember) {
            isSplitMember = false;
            splitPane.setDividerPositions(0.0);
        } else {
            isSplitMember = true;
            splitPane.setDividerPositions(0.9);
            memberTable.refresh();
        }
        updateToggleButtonStyle();
    }

    private void executeMemberSearch() {
        String name = nameField.getText();
        try {
            Optional<LightMember> list = lightService.findByName(name);
            if (list.isPresent()) {
                list.ifPresentOrElse(
                        member -> {
                            setMemberData(member);

                            // todo 查到資料後，再去查一次家屬資料(依地址or電話?)
                            // 家屬(含查詢的人)、捐款資料
                            try {
                                allMember.clear();

                                int limit = memberPageBar.getPageSize();
                                int offset = memberPageBar.getOffset();

                                String keyword = member.getAddress();
                                allMember = lightService.findAllHouse(keyword, limit, offset);
                                int total = lightService.getMemberCount(keyword);

                                // 把資料塞進表格
                                memberTable.setItems(Util.toObservableList(allMember));
                                memberPageBar.setTotalCount(total);

                                memberTable.refresh();

                                executeDonationSearch();

                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        ()-> {}
                );
            } else {
                memberTable.getItems().clear();
                clearForm(memberInputGrid);
                AlertDialog.showInfo("信眾點燈", "查無信眾資料");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeDonationSearch() throws SQLException {
        /**
         * 捐款資料
         */
        int dLimit = donationPageBar.getPageSize();
        int dOffset = donationPageBar.getOffset();
        List<Integer> memberIds = allMember.stream()
                .map(LightMember::getId)
                .collect(Collectors.toList());

        List<Donation> donations = donationService.findByMemberIds(memberIds, dLimit, dOffset);
        int dTotal = donationService.getDonationCount(memberIds);
        donationTable.setItems(Util.toObservableList(donations));
        donationPageBar.setTotalCount(dTotal);

        donationTable.refresh();
    }

    // 設置上方信眾資料
    private void setMemberData(LightMember member) {
        idField.setText(Util.stringFormat(member.getId()));
        nameField.setText(member.getName());
        genderBox.setValue(member.getGender());
        phoneField.setText(member.getPhone());
        zipCodeField.setText(member.getZipCode());
        addressField.setText(member.getAddress());
        mailBox.setValue(member.getIsMail());
        birthSunField.setText(member.getBirthDate());
        birthMoonField.setText(member.getLunarBirthDate());
        hourField.setText(member.getBirthTime());
        memberNoteField.setText(member.getNote());
    }

    private void openKeypad(TextField target, String value) {
        if (target == null) return;
        target.setText(value);
    }

    private void updateToggleButtonStyle() {
        if (isSplitMember) {
            btnToggle.getStyleClass().removeAll("btn-yellow"); // 安全起見用 removeAll
            if (!btnToggle.getStyleClass().contains("btn-pink")) {
                btnToggle.getStyleClass().add("btn-pink");
            }
        } else {
            btnToggle.getStyleClass().removeAll("btn-pink");
            if (!btnToggle.getStyleClass().contains("btn-yellow")) {
                btnToggle.getStyleClass().add("btn-yellow");
            }
        }
        btnToggle.setText(isSplitMember ? "←" : "→");
    }

    private void clearForm(Pane container) {
        for (Node node : container.getChildren()) {
            // 處理 HBox 或 VBox 嵌套的情況 (例如地址欄位包在 HBox 裡)
            if (node instanceof Pane) {
                clearForm((Pane) node);
            }
            // 清除 TextField
            else if (node instanceof TextField) {
                ((TextField) node).clear();
            }
            // 清除 ComboBox
            else if (node instanceof ComboBox) {
                ((ComboBox<?>) node).getSelectionModel().clearSelection();
            }
            // 清除 DatePicker
            else if (node instanceof DatePicker) {
                ((DatePicker) node).setValue(null);
            }
        }
    }

    private boolean validateForm() {
        clearAllErrors();
        boolean valid = true;

        if (Util.isEmpty(idField.getText())) {
            setError(idField, "請輸入電腦編號");
            valid = false;
        }

        if (Util.isEmpty(nameField.getText())) {
            setError(nameField, "請輸入姓名");
            valid = false;
        }

        if (genderBox.getValue() == null) {
            setError(genderBox, "請選擇性別");
            valid = false;
        }

        if (Util.isEmpty(zipCodeField.getText())) {
            setError(zipCodeField, "請輸入郵遞區號");
            valid = false;
        }

        if (Util.isEmpty(addressField.getText())) {
            setError(addressField, "請輸入地址");
            valid = false;
        }

        if (Util.isEmpty(birthSunField.getText() )) {
            setError(birthSunField, "請輸入國曆生日");
            valid = false;
        }

        if (Util.isEmpty(birthMoonField.getText())) {
            setError(birthMoonField, "請輸入農曆生日");
            valid = false;
        }

        return valid;
    }

    private boolean validateDonationForm() {
        clearDonationErrors();
        boolean valid = true;
        if (donateTypeField.getSelectionModel().isEmpty()) {
            setError(donateTypeField, "請選擇款項類別");
            valid = false;
        }

        if (Util.isEmpty(receiptNoField.getText())) {
            setError(receiptNoField, "請輸入收據編號");
            valid = false;
        }

        if (donateDateField.getValue() == null) {
            setError(donateDateField, "請輸入捐款日期");
            valid = false;
        }

        if (Util.isEmpty(amountField.getText())) {
            setError(amountField, "請輸入金額");
            valid = false;
        }
        return valid;
    }

    private void setError(Control field, String message) {
        if (field instanceof DatePicker) {
            field.setStyle("-fx-border-color: red;");
        } else {
            field.getStyleClass().add("error");
        }

        showTooltip(field, message);
    }

    private void clearError(Control field) {
        if (field instanceof DatePicker) {
            field.setStyle(null);
        } else {
            field.getStyleClass().remove("error");
        }
        field.setTooltip(null);
    }

    private void showTooltip(Control field, String message) {
        Tooltip tooltip = new Tooltip(message);
        field.setTooltip(tooltip);
    }

    private void clearAllErrors() {
        clearError(idField);
        clearError(nameField);
        clearError(genderBox);
        clearError(zipCodeField);
        clearError(addressField);
        clearError(birthSunField);
        clearError(birthMoonField);
    }

    private void clearDonationErrors() {
        clearError(donateTypeField);
        clearError(receiptNoField);
        clearError(donateDateField);
        clearError(amountField);
    }

    private String normalizeRocDate(String text) {

        try {
            String[] parts = text.split("\\.");

            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int d = Integer.parseInt(parts[2]);

            return String.format("%03d.%02d.%02d", y, m, d);

        } catch (Exception e) {
            return text;
        }
    }

    private boolean isValidRocDateFormat(String text) {
        return text.matches("\\d{2,3}\\.\\d{1,2}\\.\\d{1,2}");
    }

    private LocalDate parseRocDate(String text) {

        try {
            if (text == null || text.trim().isEmpty()) return null;

            String[] parts = text.split("\\.");

            int rocYear = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            int year = rocYear + 1911;

            return LocalDate.of(year, month, day);

        } catch (Exception e) {
            return null;
        }
    }

    private Integer calculateTraditionAge(int year) {
        int currentYear = LocalDate.now().getYear();
        return currentYear - year + 1;
    }

    private final String[] ZODIAC = {
            "猴","雞","狗","豬","鼠","牛","虎","兔","龍","蛇","馬","羊"
    };

    private String getZodiac(int year) {
        return ZODIAC[year % 12];
    }

    private final String[] GAN = {
            "甲","乙","丙","丁","戊","己","庚","辛","壬","癸"
    };

    private final String[] ZHI = {
            "子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"
    };

    private String getYearCycle(int year) {
        return GAN[(year - 4) % 10] + ZHI[(year - 4) % 12];
    }

}
