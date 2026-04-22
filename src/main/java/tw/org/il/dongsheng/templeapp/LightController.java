package tw.org.il.dongsheng.templeapp;

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
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.DonationRepository;
import tw.org.il.dongsheng.templeapp.repository.LightMemberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.service.DonationService;
import tw.org.il.dongsheng.templeapp.service.LightMemberService;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.Util;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.UnaryOperator;

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

    @FXML
    public void initialize() throws Exception {
        System.out.println("View LightController 初始化中...");

        LightMemberRepository lightMemberRepo = new SQLiteLightMemberRepository(SQLiteDatabaseManager.getInstance());
        lightMemberRepo.createTable();
        lightService = new LightMemberService(lightMemberRepo);

        DonationRepository donationRepo = new SQLiteDonationRepository(SQLiteDatabaseManager.getInstance());
        donationRepo.createTable();
        donationService = new DonationService(donationRepo);

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

        Map<String, List<String>> areaMap = getAllTaiwanAreas();
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
                                List<LightMember> all = lightService.findAllHouse(member.getPhone());
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

    public static Map<String, List<String>> getAllTaiwanAreas() {
        Map<String, List<String>> areaMap = new LinkedHashMap<>(); // 使用 LinkedHashMap 維護縣市排序

        areaMap.put("臺北市", Arrays.asList("中正區", "大同區", "中山區", "萬華區", "信義區", "松山區", "大安區", "南港區", "北投區", "內湖區", "士林區", "文山區"));
        areaMap.put("新北市", Arrays.asList("板橋區", "三重區", "中和區", "永和區", "新莊區", "新店區", "土城區", "蘆洲區", "汐止區", "樹林區", "淡水區", "鶯歌區", "三峽區", "瑞芳區", "五股區", "泰山區", "林口區", "深坑區", "石碇區", "坪林區", "三芝區", "石門區", "八里區", "平溪區", "雙溪區", "貢寮區", "金山區", "萬里區", "烏來區"));
        areaMap.put("桃園市", Arrays.asList("桃園區", "中壢區", "大溪區", "楊梅區", "蘆竹區", "大園區", "龜山區", "八德區", "龍潭區", "平鎮區", "新屋區", "觀音區", "復興區"));
        areaMap.put("臺中市", Arrays.asList("中區", "東區", "南區", "西區", "北區", "北屯區", "西屯區", "南屯區", "太平區", "大里區", "霧峰區", "烏日區", "豐原區", "后里區", "東勢區", "石岡區", "新社區", "和平區", "神岡區", "潭子區", "大雅區", "大肚區", "龍井區", "沙鹿區", "梧棲區", "清水區", "大甲區", "外埔區", "大安區"));
        areaMap.put("臺南市", Arrays.asList("中西區", "東區", "南區", "北區", "安平區", "安南區", "永康區", "歸仁區", "新化區", "左鎮區", "玉井區", "楠西區", "南化區", "仁德區", "關廟區", "龍崎區", "官田區", "麻豆區", "佳里區", "西港區", "七股區", "將軍區", "學甲區", "北門區", "新營區", "後壁區", "白河區", "東山區", "六甲區", "下營區", "柳營區", "鹽水區", "善化區", "大內區", "山上區", "新市區", "安定區"));
        areaMap.put("高雄市", Arrays.asList("楠梓區", "左營區", "鼓山區", "三民區", "鹽埕區", "前金區", "新興區", "苓雅區", "前鎮區", "旗津區", "小港區", "鳳山區", "大寮區", "鳥松區", "林園區", "仁武區", "大樹區", "大社區", "岡山區", "路竹區", "阿蓮區", "田寮區", "燕巢區", "橋頭區", "梓官區", "彌陀區", "永安區", "湖內區", "鳳山區", "旗山區", "美濃區", "六龜區", "內門區", "杉林區", "甲仙區", "桃源區", "那瑪夏區", "茂林區"));
        areaMap.put("基隆市", Arrays.asList("仁愛區", "信義區", "中正區", "中山區", "安樂區", "暖暖區", "七堵區"));
        areaMap.put("新竹市", Arrays.asList("東區", "北區", "香山區"));
        areaMap.put("嘉義市", Arrays.asList("東區", "西區"));
        areaMap.put("新竹縣", Arrays.asList("竹北市", "竹東鎮", "新埔鎮", "關西鎮", "湖口鄉", "新豐鄉", "芎林鄉", "橫山鄉", "北埔鄉", "寶山鄉", "峨眉鄉", "尖石鄉", "五峰鄉"));
        areaMap.put("苗栗縣", Arrays.asList("苗栗市", "頭份市", "竹南鎮", "後龍鎮", "通霄鎮", "苑裡鎮", "卓蘭鎮", "造橋鄉", "西湖鄉", "頭屋鄉", "公館鄉", "銅鑼鄉", "三義鄉", "大湖鄉", "獅潭鄉", "三灣鄉", "南庄鄉", "泰安鄉"));
        areaMap.put("彰化縣", Arrays.asList("彰化市", "員林市", "和美鎮", "鹿港鎮", "溪湖鎮", "二林鎮", "田中鎮", "北斗鎮", "花壇鄉", "芬園鄉", "大村鄉", "永靖鄉", "伸港鄉", "線西鄉", "福興鄉", "秀水鄉", "埔鹽鄉", "埔心鄉", "大城鄉", "竹塘鄉", "埤頭鄉", "溪州鄉", "二水鄉", "社頭鄉"));
        areaMap.put("南投縣", Arrays.asList("南投市", "埔里鎮", "草屯鎮", "竹山鎮", "集集鎮", "名間鄉", "鹿谷鄉", "中寮鄉", "魚池鄉", "國姓鄉", "水里鄉", "信義鄉", "仁愛鄉"));
        areaMap.put("雲林縣", Arrays.asList("斗六市", "斗南鎮", "虎尾鎮", "西螺鎮", "土庫鎮", "北港鎮", "古坑鄉", "大埤鄉", "莿桐鄉", "林內鄉", "二崙鄉", "崙背鄉", "麥寮鄉", "東勢鄉", "褒忠鄉", "臺西鄉", "元長鄉", "四湖鄉", "口湖鄉", "水林鄉"));
        areaMap.put("嘉義縣", Arrays.asList("太保市", "朴子市", "布袋鎮", "大林鎮", "民雄鄉", "溪口鄉", "新港鄉", "六腳鄉", "東石鄉", "義竹鄉", "鹿草鄉", "水上鄉", "中埔鄉", "竹崎鄉", "梅山鄉", "番路鄉", "大埔鄉", "阿里山鄉"));
        areaMap.put("屏東縣", Arrays.asList("屏東市", "潮州鎮", "東港鎮", "恆春鎮", "萬丹鄉", "長治鄉", "麟洛鄉", "九如鄉", "里港鄉", "高樹鄉", "鹽埔鄉", "內埔鄉", "竹田鄉", "萬巒鄉", "內埔鄉", "新園鄉", "崁頂鄉", "林邊鄉", "南州鄉", "佳冬鄉", "琉球鄉", "車城鄉", "滿州鄉", "枋寮鄉", "枋山鄉", "霧臺鄉", "瑪家鄉", "泰武鄉", "來義鄉", "春日鄉", "獅子鄉", "牡丹鄉", "三地門鄉"));
        areaMap.put("宜蘭縣", Arrays.asList("宜蘭市", "羅東鎮", "蘇澳鎮", "頭城鎮", "礁溪鄉", "壯圍鄉", "員山鄉", "冬山鄉", "五結鄉", "三星鄉", "大同鄉", "南澳鄉"));
        areaMap.put("花蓮縣", Arrays.asList("花蓮市", "鳳林鎮", "玉里鎮", "新城鄉", "吉安鄉", "壽豐鄉", "光復鄉", "豐濱鄉", "瑞穗鄉", "富里鄉", "秀林鄉", "萬榮鄉", "卓溪鄉"));
        areaMap.put("臺東縣", Arrays.asList("臺東市", "成功鎮", "關山鎮", "卑南鄉", "大武鄉", "太麻里鄉", "東河鄉", "長濱鄉", "鹿野鄉", "池上鄉", "綠島鄉", "延平鄉", "海端鄉", "達仁鄉", "金峰鄉", "蘭嶼鄉"));
        areaMap.put("澎湖縣", Arrays.asList("馬公市", "湖西鄉", "白沙鄉", "西嶼鄉", "望安鄉", "七美鄉"));
        areaMap.put("金門縣", Arrays.asList("金城鎮", "金湖鎮", "金沙鎮", "金寧鄉", "烈嶼鄉", "烏坵鄉"));
        areaMap.put("連江縣", Arrays.asList("南竿鄉", "北竿鄉", "莒光鄉", "東引鄉"));

        return areaMap;
    }

}
