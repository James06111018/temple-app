package tw.org.il.dongsheng.templeapp;

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
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.DonationRepository;
import tw.org.il.dongsheng.templeapp.repository.LightMemberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.service.DonationService;
import tw.org.il.dongsheng.templeapp.service.LightMemberService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.AreaUtil;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 信眾點燈 / 中元普渡 共用頁面
 */
public class LightController {

    private LightMemberService lightService;
    private DonationService donationService;

    @FXML GridPane memberInputGrid;

    @FXML private TextField idField, nameField, phoneField, zipCodeField, addressField
            , birthSunField, birthMoonField, ageField, zodiacField, yearCycleField, hourField
            , memberNoteField, contactField, idNumberField, sortField, aField, bField;
    @FXML private ComboBox<String> genderBox, cityBox, distBox, mailBox;

    @FXML private Button btnContact, btnWord;

    @FXML
    private TableView<LightMember> memberTable;
    @FXML private TableColumn<LightMember, String> colName, colMail, colSolar, colLunar, colZodiac, colEra, colHour, colGender;
    @FXML private TableColumn<LightMember, Integer> colId, colAge;

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


        // 設定每個欄位對應 Member 類別的屬性名稱 (變數名)
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
    }

    public void initData() {
        try {
            if (type.equals("light")) {
                LightMemberRepository lightMemberRepo = new SQLiteLightMemberRepository(SQLiteDatabaseManager.getInstance());
                lightMemberRepo.createTable();
                lightService = new LightMemberService(lightMemberRepo);

                DonationRepository donationRepo = new SQLiteDonationRepository(SQLiteDatabaseManager.getInstance());
                donationRepo.createTable();
                donationService = new DonationService(donationRepo);
            } else if (type.equals("ghost")) {

            }
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

        try {
            Optional<LightMember> list = lightService.findByName(name);
//            System.out.println("onSearch : "+name+ " , list : "+list.stream().count());
            if (list.isPresent()) {
                list.ifPresentOrElse(
                        member -> {
                            idField.setText(Util.stringFormat(member.getId()));
                            genderBox.setValue(member.getGender());
                            phoneField.setText(member.getPhone());
                            zipCodeField.setText(member.getZipCode());
                            addressField.setText(member.getAddress());
                            birthSunField.setText(member.getBirthDate());
                            birthMoonField.setText(member.getLunarBirthDate());
                            hourField.setText(member.getBirthTime());
                            memberNoteField.setText(member.getNote());

                            // todo 查到資料後，再去查一次家屬資料(依地址or電話?)
                            try {
                                List<LightMember> all = lightService.findAllHouse(member.getAddress());
                                // 把資料塞進表格
                                memberTable.setItems(Util.toObservableList(all));

                                memberTable.refresh();
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        ()-> {}
                );
            } else {
                memberTable.getItems().clear();
                clearForm(memberInputGrid);
                AlertDialog.showInfo("信眾點燈", "查無資料");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
                parseInteger(ageField.getText()),
                zodiacField.getText(),
                yearCycleField.getText(),
                hourField.getText(),
                memberNoteField.getText(),
                contactField.getText(),
                idNumberField.getText(),
                parseInteger(sortField.getText()),
                parseInteger(aField.getText()),
                parseInteger(bField.getText()),
                mailBox.getValue(),
                genderBox.getValue()
        );

        try {
            // 判斷新增或修改
            if (id == null || !lightService.exists(id)) {
                lightService.save(member);
                AlertDialog.showInfo("信眾點燈", "新增成功");
            } else {
                lightService.update(member);
                AlertDialog.showInfo("信眾點燈", "修改成功");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        clearForm(memberInputGrid);
    }

    @FXML
    public void onClearField() {
        clearForm(memberInputGrid);
        memberTable.getItems().clear();
    }

    @FXML
    public void onOpenKeypad() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("number-keypad.fxml")
        );

        Parent root = loader.load();

        NumberKeypadController controller = loader.getController();

        // 回傳值（填入電話欄位）
        controller.setOnConfirm(value -> {
            phoneField.setText(value);
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

        if (isEmpty(idField.getText())) {
            setError(idField, "請輸入電腦編號");
            valid = false;
        }

        if (isEmpty(nameField.getText())) {
            setError(nameField, "請輸入姓名");
            valid = false;
        }

        if (genderBox.getValue() == null) {
            setError(genderBox, "請選擇性別");
            valid = false;
        }

        if (isEmpty(zipCodeField.getText())) {
            setError(zipCodeField, "請輸入郵遞區號");
            valid = false;
        }

        if (isEmpty(addressField.getText())) {
            setError(addressField, "請輸入地址");
            valid = false;
        }

        if (isEmpty(birthSunField.getText() )) {
            setError(birthSunField, "請輸入國曆生日");
            valid = false;
        }

        if (isEmpty(birthMoonField.getText())) {
            setError(birthMoonField, "請輸入農曆生日");
            valid = false;
        }

        return valid;
    }

    private void setError(Control field, String message) {
        field.getStyleClass().add("error");

        showTooltip(field, message);
    }

    private void clearError(Control field) {
        field.getStyleClass().remove("error");
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

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private Integer parseInteger(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
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
