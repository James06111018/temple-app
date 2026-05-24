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
import javafx.scene.layout.HBox;
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
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAddressRepository;
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
    private SQLiteAddressRepository addressRepository;

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

    @FXML private Button generateIdButton, saveButton, btnContact, btnWord, btnToggle;
    @FXML private HBox donationButtonBox;
    @FXML private Button donationPrimaryButton, donationSecondaryButton, donationDeleteButton, donationSupplementButton;

    @FXML private SplitPane splitPane;
    private boolean isSplitMember = true;

    @FXML
    private TableView<LightMember> memberTable;
    @FXML private TableColumn<LightMember, String> colName, colMail, colSolar, colLunar, colZodiac, colEra, colHour, colGender
            , colAddress, colPhone, colZipCode;
    @FXML private TableColumn<LightMember, Integer> colId, colAge;

    @FXML
    private TableView<Donation> donationTable;
    @FXML private TableColumn<Donation, String> colReceiptNo, colDonateDate, colExtraNo, colDonationType, colSummary, colDonateNote
            , colOtherNote, colDonorNo, colLightNo, colSeqNo, coCreator;
    @FXML private TableColumn<Donation, Integer> colAmount, colShouldPay;

    @FXML private PaginationBar memberPageBar, donationPageBar;

    private List<LightMember> allMember = new LinkedList<>(); // 查詢的信眾，所有的家屬(含自已)
    private Map<String, String> categoryMap = new LinkedHashMap<>(); // 捐款類別資料: id, code-name
    private LightMember referenceMember;
    private boolean addMode = false;
    private DonationMode donationMode = DonationMode.BROWSE;
    private boolean loadingMemberData = false;

    private String type;

    public void setType(String type) {
        this.type = type;
    }

    @FXML
    public void initialize() {
        System.out.println("View LightController 初始化中...");

        idField.setOnAction(event -> onSearch());
        nameField.setOnAction(event -> onSearch());
        phoneField.setOnAction(event -> onSearch());

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
        cityBox.getItems().add("");
        cityBox.getItems().addAll(areaMap.keySet());
        cityBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            distBox.getItems().clear();
            distBox.getSelectionModel().clearSelection();
            if (newVal != null && !newVal.isBlank()) {
                distBox.getItems().addAll(areaMap.get(newVal));
            }
        });
        distBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !loadingMemberData) {
                zipCodeField.setText(AreaUtil.getZipCode(newVal));
                addressField.setText(AreaUtil.getAddressPrefix(cityBox.getValue(), newVal));
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
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colZipCode.setCellValueFactory(new PropertyValueFactory<>("zipCode"));

        memberTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                setMemberData(newVal);
                referenceMember = newVal;
            }
        });

        memberPageBar.setTotalCount(0);
        memberPageBar.setOnAction(() -> selectTableRow(memberTable, memberPageBar.getCurrentIndex()));

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
        donationPageBar.setOnAction(() -> selectTableRow(donationTable, donationPageBar.getCurrentIndex()));
        donationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (donationMode == DonationMode.EDIT && newVal != null) {
                setDonationData(newVal);
            }
        });
        setDonationMode(DonationMode.BROWSE);
        setDonationAvailable(false);
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

                addressRepository = new SQLiteAddressRepository(manager);
                addressRepository.createTable();

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
        if (warnIfDonationEditing()) {
            return;
        }

        try {
            int nextId = lightService.getNextId();
            String result = Util.stringFormat(nextId);

            LightMember previousMember = referenceMember;
            boolean useReference = false;
            if (previousMember != null) {
                useReference = AlertDialog.showConfirm(
                        "參照地址",
                        "是否要參照前一位香客的地址和電話資料？\n\n（ " +
                                Util.emptyToDefault(previousMember.getAddress(), "無地址資料") +
                                " ）"
                );
            }

            clearForm(memberInputGrid);
            clearForm(donateInputGrid);
            clearAllErrors();
            clearDonationErrors();

            idField.setText(result);
            if (useReference) {
                phoneField.setText(previousMember.getPhone());
                zipCodeField.setText(previousMember.getZipCode());
                addressField.setText(previousMember.getAddress());
            }

            allMember.clear();
            memberTable.getItems().clear();
            memberPageBar.setTotalCount(0);
            memberTable.getSelectionModel().clearSelection();
            donationTable.getItems().clear();
            donationPageBar.setTotalCount(0);
            setAddMode(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    public void onSearch() {
        if (warnIfDonationEditing()) {
            return;
        }

        String id = idField.getText();
        String name = nameField.getText();
        String phone = phoneField.getText();
        if(Util.isEmpty(id) && Util.isEmpty(name) && Util.isEmpty(phone)) {
            AlertDialog.showInfo("信眾點燈", "請至少輸入電腦編號、姓名或電話其中一項");
            return;
        }

        executeMemberSearch();
    }

    @FXML
    public void onSave() {
        if (warnIfDonationEditing()) {
            return;
        }

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
                AreaUtil.getDistrictName(distBox.getValue()),
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
        setAddMode(false);
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
        if (warnIfDonationEditing()) {
            return;
        }

        clearForm(memberInputGrid);
        clearForm(donateInputGrid);
        clearAllErrors();
        clearDonationErrors();
        allMember.clear();
        referenceMember = null;
        memberTable.getItems().clear();
        memberTable.getSelectionModel().clearSelection();
        donationTable.getItems().clear();
        donationTable.getSelectionModel().clearSelection();
        memberPageBar.setTotalCount(0);
        donationPageBar.setTotalCount(0);
        setAddMode(false);
        setDonationMode(DonationMode.BROWSE);
        setDonationAvailable(false);
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
    public void onOpenWordInfo() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("word-info.fxml")
        );

        Parent root = loader.load();
        WordInfoController controller = loader.getController();
        controller.setOnSelected(value -> nameField.appendText(value));

        Stage stage = new Stage();
        stage.setTitle("造字資訊");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);

        stage.showAndWait();

    }

    public void onOpenAddressPicker() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("address-picker.fxml")
        );

        Parent root = loader.load();

        AddressPickerController controller = loader.getController();
        controller.setAddressRepository(addressRepository);
        controller.setInitialValues(
                zipCodeField.getText(),
                AreaUtil.normalizeCityName(cityBox.getValue()),
                AreaUtil.normalizeDistrictName(distBox.getValue()),
                addressField.getText()
        );
        controller.setOnConfirm(result -> {
            zipCodeField.setText(result.zipCode());
            addressField.setText(result.address());
        });

        Stage stage = new Stage();
        stage.setTitle("路名選取");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);

        stage.showAndWait();
    }

    @FXML
    public void onDonationAddMode() {
        if (!hasSelectedMember()) {
            return;
        }
        clearForm(donateInputGrid);
        donateDateField.setValue(LocalDate.now());
        setDonationMode(DonationMode.ADD);
        donateTypeField.requestFocus();
        donateTypeField.show();
    }

    @FXML
    public void onDonationEditMode() {
        Donation selectedDonation = donationTable.getSelectionModel().getSelectedItem();
        if (selectedDonation == null) {
            AlertDialog.showInfo("信眾點燈", "請先選擇要修改的捐款資料");
            return;
        }
        setDonationData(selectedDonation);
        setDonationMode(DonationMode.EDIT);
    }

    @FXML
    public void onDonationConfirm() {
        if (donationMode == DonationMode.ADD) {
            onDonationAdd();
        } else if (donationMode == DonationMode.EDIT) {
            onDonationUpdate();
        }
    }

    @FXML
    public void onDonationCancel() {
        clearForm(donateInputGrid);
        clearDonationErrors();
        setDonationMode(DonationMode.BROWSE);
    }

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
            donationPageBar.setTotalCount(donationTable.getItems().size());
            donationPageBar.setCurrentIndex(0);
            selectTableRow(donationTable, 0);
            setDonationMode(DonationMode.BROWSE);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        clearForm(donateInputGrid);
    }

    private void onDonationUpdate() {
        Donation selectedDonation = donationTable.getSelectionModel().getSelectedItem();
        if (selectedDonation == null) {
            AlertDialog.showInfo("信眾點燈", "請先選擇要修改的捐款資料");
            return;
        }
        if (!validateDonationForm()) {
            return;
        }

        selectedDonation.setDonateType(String.valueOf(donateTypeField.getValue().getId()));
        selectedDonation.setReceiptNo(receiptNoField.getText());
        selectedDonation.setDonateDate(donateDateField.getValue().format(dateFormatter));
        selectedDonation.setExtraNo(extraNoField.getText());
        selectedDonation.setAmount(Util.parseInteger(amountField.getText()));
        selectedDonation.setSummary(summaryField.getText());
        selectedDonation.setDonateNote(donateNoteField.getText());
        selectedDonation.setOtherNote(otherNoteField.getText());
        selectedDonation.setDonorNo(donorNoField.getText());
        selectedDonation.setLightNo(lightNoField.getText());
        selectedDonation.setShouldPay(Util.parseInteger(shouldPayField.getText()));

        try {
            donationService.update(selectedDonation);
            AlertDialog.showInfo("信眾點燈", "修改捐款作業成功");
            donationTable.refresh();
            setDonationMode(DonationMode.BROWSE);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        String id = idField.getText();
        String name = nameField.getText();
        String phone = phoneField.getText();
        try {
            List<LightMember> matches = lightService.search(id, name, phone);
            if (matches.isEmpty()) {
                memberTable.getItems().clear();
                memberPageBar.setTotalCount(0);
                donationTable.getItems().clear();
                donationPageBar.setTotalCount(0);
                AlertDialog.showInfo("信眾點燈", "查無信眾資料");
                return;
            }

            LightMember member = matches.get(0);
            referenceMember = member;
            setMemberData(member);
            setAddMode(false);

            // 查到資料後，再以地址帶出同戶家屬(含查詢的人)與捐款資料
            allMember.clear();
            String keyword = member.getAddress();
            int total = lightService.getMemberCount(keyword);
            allMember = lightService.findAllHouse(keyword, Math.max(total, 1), 0);

            memberTable.setItems(Util.toObservableList(allMember));
            memberPageBar.setTotalCount(total);
            int selectedIndex = Math.max(0, allMember.indexOf(member));
            memberPageBar.setCurrentIndex(selectedIndex);
            selectTableRow(memberTable, selectedIndex);

            memberTable.refresh();

            executeDonationSearch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeDonationSearch() throws SQLException {
        /**
         * 捐款資料
         */
        List<Integer> memberIds = allMember.stream()
                .map(LightMember::getId)
                .collect(Collectors.toList());

        int dTotal = donationService.getDonationCount(memberIds);
        List<Donation> donations = donationService.findByMemberIds(memberIds, Math.max(dTotal, 1), 0);
        donationTable.setItems(Util.toObservableList(donations));
        donationPageBar.setTotalCount(dTotal);
        donationPageBar.setCurrentIndex(0);
        selectTableRow(donationTable, 0);
        setDonationAvailable(true);

        donationTable.refresh();
    }

    private <T> void selectTableRow(TableView<T> tableView, int index) {
        if (tableView.getItems().isEmpty()) {
            tableView.getSelectionModel().clearSelection();
            return;
        }

        int safeIndex = Math.max(0, Math.min(index, tableView.getItems().size() - 1));
        tableView.getSelectionModel().select(safeIndex);
        tableView.scrollTo(safeIndex);
    }

    // 設置上方信眾資料
    private void setMemberData(LightMember member) {
        idField.setText(Util.stringFormat(member.getId()));
        loadingMemberData = true;
        try {
            nameField.setText(member.getName());
            genderBox.setValue(member.getGender());
            phoneField.setText(member.getPhone());
            cityBox.setValue(member.getCity());
            if (member.getCity() != null) {
                String areaText = AreaUtil.findAreaText(member.getCity(), member.getDist());
                distBox.setValue(areaText != null ? areaText : member.getDist());
            }
            zipCodeField.setText(member.getZipCode());
            addressField.setText(member.getAddress());
            mailBox.setValue(member.getIsMail());
            birthSunField.setText(member.getBirthDate());
            birthMoonField.setText(member.getLunarBirthDate());
            hourField.setText(member.getBirthTime());
            memberNoteField.setText(member.getNote());
        } finally {
            loadingMemberData = false;
        }
    }

    private void setDonationData(Donation donation) {
        selectDonationCategory(donation.getDonateType());
        receiptNoField.setText(donation.getReceiptNo());
        donateDateField.setValue(parseDonationDate(donation.getDonateDate()));
        extraNoField.setText(donation.getExtraNo());
        amountField.setText(donation.getAmount() == null ? "" : String.valueOf(donation.getAmount()));
        summaryField.setText(donation.getSummary());
        donateNoteField.setText(donation.getDonateNote());
        otherNoteField.setText(donation.getOtherNote());
        donorNoField.setText(donation.getDonorNo());
        lightNoField.setText(donation.getLightNo());
        shouldPayField.setText(donation.getShouldPay() == null ? "" : String.valueOf(donation.getShouldPay()));
    }

    private void openKeypad(TextField target, String value) {
        if (target == null) return;
        target.setText(value);
    }

    private void setAddMode(boolean addMode) {
        this.addMode = addMode;
        if (addMode) {
            setDonationAvailable(false);
        }

        saveButton.getStyleClass().removeAll("btn-purple", "btn-save-active");
        saveButton.getStyleClass().add(addMode ? "btn-save-active" : "btn-purple");
    }

    private boolean hasSelectedMember() {
        return !Util.isEmpty(idField.getText()) && !memberTable.getItems().isEmpty();
    }

    private boolean warnIfDonationEditing() {
        if (donationMode == DonationMode.BROWSE) {
            return false;
        }

        AlertDialog.showWarning("信眾點燈", "捐款作業進行中，請先取消，才能操作其他功能！");
        return true;
    }

    private void setDonationAvailable(boolean available) {
        donationPrimaryButton.setDisable(!available);
        donationSecondaryButton.setDisable(!available);
        donationDeleteButton.setDisable(!available);
        donationSupplementButton.setDisable(!available);
        setDonationFieldsEditable(false);
    }

    private void setDonationMode(DonationMode mode) {
        donationMode = mode;
        boolean editing = mode != DonationMode.BROWSE;

        memberInputGrid.setDisable(editing);
        memberTable.setDisable(editing);
        memberPageBar.setDisable(editing);
        donationTable.setDisable(editing);
        donationPageBar.setDisable(editing);

        donationPrimaryButton.setText(editing ? "確認" : "新增");
        donationSecondaryButton.setText(editing ? "取消" : "修改");
        donationPrimaryButton.setOnAction(editing ? event -> onDonationConfirm() : event -> onDonationAddMode());
        donationSecondaryButton.setOnAction(editing ? event -> onDonationCancel() : event -> onDonationEditMode());
        donationDeleteButton.setDisable(editing || !hasSelectedMember());
        donationSupplementButton.setDisable(editing || !hasSelectedMember());

        donationPrimaryButton.getStyleClass().removeAll("btn-purple", "btn-save-active");
        donationSecondaryButton.getStyleClass().removeAll("btn-purple", "btn-save-active");
        donationPrimaryButton.getStyleClass().add(editing ? "btn-save-active" : "btn-purple");
        donationSecondaryButton.getStyleClass().add(editing ? "btn-save-active" : "btn-purple");

        setDonationFieldsEditable(editing);
        if (mode == DonationMode.ADD) {
            receiptNoField.setDisable(true);
            donateDateField.setDisable(true);
            donorNoField.setDisable(true);
            lightNoField.setDisable(true);
        } else if (mode == DonationMode.EDIT) {
            donateTypeField.setDisable(true);
            receiptNoField.setDisable(true);
            extraNoField.setDisable(true);
            donorNoField.setDisable(true);
            lightNoField.setDisable(true);
            shouldPayField.setDisable(true);
        }
    }

    private void setDonationFieldsEditable(boolean editable) {
        donateTypeField.setDisable(!editable);
        receiptNoField.setDisable(!editable);
        donateDateField.setDisable(!editable);
        extraNoField.setDisable(!editable);
        amountField.setDisable(!editable);
        summaryField.setDisable(!editable);
        donateNoteField.setDisable(!editable);
        otherNoteField.setDisable(!editable);
        donorNoField.setDisable(!editable);
        lightNoField.setDisable(!editable);
        shouldPayField.setDisable(!editable);
    }

    private void selectDonationCategory(String categoryId) {
        if (categoryId == null) {
            donateTypeField.getSelectionModel().clearSelection();
            return;
        }
        donateTypeField.getItems().stream()
                .filter(category -> String.valueOf(category.getId()).equals(categoryId))
                .findFirst()
                .ifPresent(category -> donateTypeField.getSelectionModel().select(category));
    }

    private LocalDate parseDonationDate(String value) {
        try {
            return Util.isEmpty(value) ? null : LocalDate.parse(value, dateFormatter);
        } catch (Exception e) {
            return null;
        }
    }

    public void onBlockedDuringDonation() {
        warnIfDonationEditing();
    }

    @FXML
    public void onOpenLightDataManagement() throws IOException {
        if (warnIfDonationEditing()) {
            return;
        }
        openModal("light-data-management.fxml", "信眾點燈資料管理", 1180, 670);
    }

    @FXML
    public void onOpenTotalAmount() throws IOException {
        if (warnIfDonationEditing()) {
            return;
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("total-amount.fxml"));
        Parent root = loader.load();
        TotalAmountController controller = loader.getController();
        controller.setMemberId(idField.getText());
        showModal(root, "總金額", 570, 300);
    }

    @FXML
    public void onOpenHouseholdLight() throws IOException {
        if (warnIfDonationEditing()) {
            return;
        }
        openModal("household-light.fxml", "全戶點燈", 1220, 670);
    }

    private void openModal(String fxml, String title, double width, double height) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
        Parent root = loader.load();
        showModal(root, title, width, height);
    }

    private void showModal(Parent root, String title, double width, double height) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(root, width, height));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }

    private enum DonationMode {
        BROWSE,
        ADD,
        EDIT
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

        if (donationMode == DonationMode.EDIT && Util.isEmpty(receiptNoField.getText())) {
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
