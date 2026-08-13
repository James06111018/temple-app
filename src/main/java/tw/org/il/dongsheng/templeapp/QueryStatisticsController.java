package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.AppUser;
import tw.org.il.dongsheng.templeapp.model.DictionaryItem;
import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationSupplement;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteAuthRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDictionaryRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDonationSupplementRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class QueryStatisticsController {
    @FXML private ListView<DictionaryItem> availableCategoryList, selectedCategoryList;
    @FXML private ComboBox<String> amountOperatorBox, dataTypeBox;
    @FXML private RadioButton singleAmountRadio;
    @FXML private CheckBox memberIdFilterCheck, categoryFilterCheck, dateFilterCheck, amountFilterCheck,
            summaryFilterCheck, donateNoteFilterCheck, operatorFilterCheck, receiptFilterCheck,
            supplementFilterCheck;
    @FXML private TextField memberIdField, startDateField, endDateField, amountField, summaryField,
            donateNoteField, operatorField, receiptStartField, receiptEndField, rankingLimitField,
            incomeCountField, incomeAmountField, expenseCountField, expenseAmountField,
            totalCountField, totalAmountField;
    @FXML private Button peopleStatisticsButton, donationDetailsButton;

    private final NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);
    private SQLiteDictionaryRepository dictionaryRepository;
    private SQLiteDonationRepository donationRepository;
    private SQLiteLightMemberRepository memberRepository;
    private SQLiteDonationSupplementRepository supplementRepository;
    private SQLiteAuthRepository authRepository;
    private List<DictionaryItem> lightCategories = List.of();
    private List<DictionaryItem> ghostCategories = List.of();
    private Map<String, DictionaryItem> categoriesById = Map.of();
    private List<Donation> queryResults = List.of();

    @FXML
    public void initialize() {
        SQLiteDatabaseManager manager = SQLiteDatabaseManager.getInstance();
        dictionaryRepository = new SQLiteDictionaryRepository(manager);
        donationRepository = new SQLiteDonationRepository(manager);
        memberRepository = new SQLiteLightMemberRepository(manager);
        supplementRepository = new SQLiteDonationSupplementRepository(manager);
        authRepository = new SQLiteAuthRepository(manager);

        amountOperatorBox.setItems(FXCollections.observableArrayList(">=", "<=", "=", ">", "<"));
        amountOperatorBox.getSelectionModel().selectFirst();
        dataTypeBox.setItems(FXCollections.observableArrayList("全部", "信眾點燈", "中元普渡"));
        dataTypeBox.getSelectionModel().selectFirst();
        dataTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> loadAvailableCategories());
        singleAmountRadio.setSelected(true);
        String today = currentRocDate();
        startDateField.setText(today);
        endDateField.setText(today);
        resetSummary();

        try {
            dictionaryRepository.migrateFromLegacy();
            donationRepository.createTable();
            supplementRepository.createTable();
            authRepository.createTables();
            reloadCategoryDefinitions();
        } catch (SQLException e) {
            AlertDialog.showError("查詢統計", "初始化查詢資料失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onAddSelected() {
        DictionaryItem selected = availableCategoryList.getSelectionModel().getSelectedItem();
        if (selected != null && !selectedCategoryList.getItems().contains(selected)) {
            selectedCategoryList.getItems().add(selected);
        }
    }

    @FXML
    private void onAddAll() {
        for (DictionaryItem item : availableCategoryList.getItems()) {
            if (!selectedCategoryList.getItems().contains(item)) {
                selectedCategoryList.getItems().add(item);
            }
        }
    }

    @FXML
    private void onRemoveSelected() {
        DictionaryItem selected = selectedCategoryList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedCategoryList.getItems().remove(selected);
        }
    }

    @FXML
    private void onRemoveAll() {
        selectedCategoryList.getItems().clear();
    }

    @FXML
    private void onStartQuery() {
        resetSummary();
        if (!hasSelectedQueryCondition()) {
            AlertDialog.showWarning("查詢統計", "請至少勾選一項查詢條件");
            return;
        }
        QueryCondition condition = buildQueryCondition();
        if (condition == null) {
            return;
        }

        try {
            Map<Integer, LightMember> membersById = memberRepository.findAll().stream()
                    .collect(Collectors.toMap(
                            LightMember::getId,
                            member -> member,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            Set<Integer> supplementDonationIds = condition.supplementOnly()
                    ? supplementRepository.findByDateRange(LocalDate.of(1912, 1, 1), LocalDate.of(9999, 12, 31))
                            .stream()
                            .map(DonationSupplement::getDonationId)
                            .collect(Collectors.toSet())
                    : Set.of();

            List<Donation> filtered = donationRepository.findAll().stream()
                    .filter(donation -> matchesBaseCondition(
                            donation, condition, membersById, supplementDonationIds
                    ))
                    .toList();
            if (condition.amountEnabled()) {
                filtered = filterByAmount(filtered, condition);
            }

            queryResults = List.copyOf(filtered);
            showSummary(queryResults);
            if (queryResults.isEmpty()) {
                AlertDialog.showInfo("查詢統計", "查無符合條件的捐款資料");
            }
        } catch (SQLException e) {
            AlertDialog.showError("查詢統計", "查詢捐款資料失敗：" + e.getMessage());
        }
    }

    private boolean hasSelectedQueryCondition() {
        return memberIdFilterCheck.isSelected()
                || categoryFilterCheck.isSelected()
                || dateFilterCheck.isSelected()
                || amountFilterCheck.isSelected()
                || summaryFilterCheck.isSelected()
                || donateNoteFilterCheck.isSelected()
                || operatorFilterCheck.isSelected()
                || receiptFilterCheck.isSelected()
                || supplementFilterCheck.isSelected();
    }

    @FXML
    private void onChooseOperator() {
        try {
            List<AppUser> users = authRepository.findAllUsers().stream()
                    .filter(AppUser::isEnabled)
                    .toList();
            ListView<AppUser> listView = new ListView<>(FXCollections.observableArrayList(users));
            listView.setPrefSize(220, 280);

            Stage stage = new Stage();
            stage.setTitle("經辦人");
            stage.setScene(new Scene(listView));
            stage.initOwner(operatorField.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            listView.setOnMouseClicked(event -> {
                AppUser selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null && event.getClickCount() == 2) {
                    operatorField.setText(selected.toString());
                    operatorFilterCheck.setSelected(true);
                    stage.close();
                }
            });
            stage.showAndWait();
        } catch (SQLException e) {
            AlertDialog.showError("查詢統計", "讀取經辦人失敗：" + e.getMessage());
        }
    }

    @FXML
    private void onOpenIncomeReport() throws IOException {
        showModal("收入報表", "income-report.fxml");
    }

    @FXML
    private void onOpenPrintLabels() throws IOException {
        showModal("列印標籤", "print-labels.fxml");
    }

    @FXML
    private void onOpenDonationRanking() throws IOException {
        int limit = parseRankingLimit();
        if (limit <= 0) {
            return;
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("donation-ranking.fxml"));
        Parent root = loader.load();
        DonationRankingController controller = loader.getController();
        controller.setLimit(limit);

        Stage stage = new Stage();
        stage.setTitle("捐款累計金額最多前 " + limit + " 名");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }

    @FXML
    private void onOpenPeopleStatistics() throws IOException {
        showModal("人數統計", "people-statistics.fxml");
    }

    @FXML
    private void onOpenDonationDetails() throws IOException {
        showModal("捐款明細", "donation-details.fxml");
    }

    private void reloadCategoryDefinitions() throws SQLException {
        lightCategories = dictionaryRepository.findEnabledItemsByType(
                SQLiteDictionaryRepository.TYPE_DONATION_LIGHT
        );
        ghostCategories = dictionaryRepository.findEnabledItemsByType(
                SQLiteDictionaryRepository.TYPE_DONATION_GHOST
        );
        Map<String, DictionaryItem> definitions = new LinkedHashMap<>();
        for (DictionaryItem item : combinedCategories()) {
            definitions.put(String.valueOf(item.getId()), item);
        }
        categoriesById = Map.copyOf(definitions);
        loadAvailableCategories();
    }

    private void loadAvailableCategories() {
        if (availableCategoryList == null || dataTypeBox == null) {
            return;
        }
        List<DictionaryItem> items = switch (dataTypeBox.getSelectionModel().getSelectedIndex()) {
            case 1 -> lightCategories;
            case 2 -> ghostCategories;
            default -> combinedCategories();
        };
        Set<Integer> allowedIds = items.stream().map(DictionaryItem::getId).collect(Collectors.toSet());
        selectedCategoryList.getItems().removeIf(item -> !allowedIds.contains(item.getId()));
        availableCategoryList.setItems(FXCollections.observableArrayList(items));
    }

    private List<DictionaryItem> combinedCategories() {
        Map<Integer, DictionaryItem> items = new LinkedHashMap<>();
        lightCategories.forEach(item -> items.put(item.getId(), item));
        ghostCategories.forEach(item -> items.put(item.getId(), item));
        return new ArrayList<>(items.values());
    }

    private QueryCondition buildQueryCondition() {
        Integer memberId = null;
        if (memberIdFilterCheck.isSelected()) {
            memberId = parsePositiveInteger(memberIdField.getText());
            if (memberId == null) {
                AlertDialog.showWarning("查詢統計", "電腦編號請輸入有效的正整數");
                return null;
            }
        }

        Set<String> allowedDataTypes = dataTypeBox.getSelectionModel().getSelectedIndex() == 0
                ? Set.of()
                : categoriesForSelectedDataType().stream()
                        .map(DictionaryItem::getId)
                        .map(String::valueOf)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> selectedCategoryIds = Set.of();
        if (categoryFilterCheck.isSelected()) {
            if (selectedCategoryList.getItems().isEmpty()) {
                AlertDialog.showWarning("查詢統計", "請至少選擇一個款項類別");
                return null;
            }
            selectedCategoryIds = selectedCategoryList.getItems().stream()
                    .map(DictionaryItem::getId)
                    .map(String::valueOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        LocalDate startDate = null;
        LocalDate endDate = null;
        if (dateFilterCheck.isSelected()) {
            startDate = parseDate(startDateField.getText());
            endDate = parseDate(endDateField.getText());
            if (startDate == null || endDate == null) {
                AlertDialog.showWarning("查詢統計", "捐款日期請輸入民國 115.05.14 或西元 2026-05-14 格式");
                return null;
            }
            if (startDate.isAfter(endDate)) {
                AlertDialog.showWarning("查詢統計", "捐款日期的起始日不可晚於結束日");
                return null;
            }
        }

        Long amount = null;
        if (amountFilterCheck.isSelected()) {
            amount = parseLong(amountField.getText());
            if (amount == null) {
                AlertDialog.showWarning("查詢統計", "金額請輸入整數");
                return null;
            }
        }

        String receiptStart = normalized(receiptStartField.getText());
        String receiptEnd = normalized(receiptEndField.getText());
        if (receiptFilterCheck.isSelected() && receiptStart.isEmpty() && receiptEnd.isEmpty()) {
            AlertDialog.showWarning("查詢統計", "請至少輸入一個收據編號範圍");
            return null;
        }

        return new QueryCondition(
                memberId,
                allowedDataTypes,
                selectedCategoryIds,
                startDate,
                endDate,
                amountFilterCheck.isSelected(),
                singleAmountRadio.isSelected(),
                amountOperatorBox.getValue(),
                amount,
                summaryFilterCheck.isSelected() ? normalized(summaryField.getText()) : "",
                donateNoteFilterCheck.isSelected() ? normalized(donateNoteField.getText()) : "",
                operatorFilterCheck.isSelected() ? normalized(operatorField.getText()) : "",
                receiptFilterCheck.isSelected(),
                receiptStart,
                receiptEnd,
                supplementFilterCheck.isSelected()
        );
    }

    private boolean matchesBaseCondition(
            Donation donation,
            QueryCondition condition,
            Map<Integer, LightMember> membersById,
            Set<Integer> supplementDonationIds
    ) {
        if (!condition.allowedDataTypeIds().isEmpty()
                && !condition.allowedDataTypeIds().contains(donation.getDonateType())) {
            return false;
        }
        if (!condition.selectedCategoryIds().isEmpty()
                && !condition.selectedCategoryIds().contains(donation.getDonateType())) {
            return false;
        }
        if (condition.memberId() != null && !condition.memberId().equals(donation.getMemberId())) {
            return false;
        }
        if (!membersById.containsKey(donation.getMemberId())) {
            return false;
        }
        if (condition.startDate() != null) {
            LocalDate donationDate = parseDate(donation.getDonateDate());
            if (donationDate == null
                    || donationDate.isBefore(condition.startDate())
                    || donationDate.isAfter(condition.endDate())) {
                return false;
            }
        }
        if (!condition.summary().isEmpty() && !contains(donation.getSummary(), condition.summary())) {
            return false;
        }
        if (!condition.donateNote().isEmpty()
                && !contains(donation.getDonateNote(), condition.donateNote())) {
            return false;
        }
        if (!condition.operator().isEmpty() && !contains(donation.getCreator(), condition.operator())) {
            return false;
        }
        if (condition.receiptEnabled()
                && !isWithinReceiptRange(donation.getReceiptNo(), condition.receiptStart(), condition.receiptEnd())) {
            return false;
        }
        return !condition.supplementOnly()
                || donation.getId() != null && supplementDonationIds.contains(donation.getId());
    }

    private List<Donation> filterByAmount(List<Donation> donations, QueryCondition condition) {
        Predicate<Long> matches = value -> compareAmount(value, condition.amountOperator(), condition.amount());
        if (condition.singleAmount()) {
            return donations.stream()
                    .filter(donation -> matches.test(amountOf(donation)))
                    .toList();
        }

        Set<Integer> matchingMemberIds = donations.stream()
                .collect(Collectors.groupingBy(
                        Donation::getMemberId,
                        HashMap::new,
                        Collectors.summingLong(this::amountOf)
                ))
                .entrySet()
                .stream()
                .filter(entry -> matches.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return donations.stream()
                .filter(donation -> matchingMemberIds.contains(donation.getMemberId()))
                .toList();
    }

    private void showSummary(List<Donation> donations) {
        int incomeCount = 0;
        long incomeAmount = 0;
        int expenseCount = 0;
        long expenseAmount = 0;
        long grossAmount = 0;

        for (Donation donation : donations) {
            long amount = amountOf(donation);
            grossAmount += Math.abs(amount);
            if (isExpense(donation)) {
                expenseCount++;
                expenseAmount += Math.abs(amount);
            } else {
                incomeCount++;
                incomeAmount += amount;
            }
        }

        incomeCountField.setText(String.valueOf(incomeCount));
        incomeAmountField.setText(integerFormat.format(incomeAmount));
        expenseCountField.setText(String.valueOf(expenseCount));
        expenseAmountField.setText(integerFormat.format(expenseAmount));
        totalCountField.setText(String.valueOf(incomeCount - expenseCount));
        totalAmountField.setText(integerFormat.format(incomeAmount - expenseAmount));

        boolean hasUsableResult = !donations.isEmpty() && grossAmount > 0;
        peopleStatisticsButton.setDisable(!hasUsableResult);
        donationDetailsButton.setDisable(!hasUsableResult);
    }

    private boolean isExpense(Donation donation) {
        DictionaryItem category = categoriesById.get(donation.getDonateType());
        if (category != null && "-".equals(category.getDirection())) {
            return true;
        }
        return amountOf(donation) < 0;
    }

    private long amountOf(Donation donation) {
        return donation.getShouldPay() == null ? 0L : donation.getShouldPay().longValue();
    }

    private List<DictionaryItem> categoriesForSelectedDataType() {
        return switch (dataTypeBox.getSelectionModel().getSelectedIndex()) {
            case 1 -> lightCategories;
            case 2 -> ghostCategories;
            default -> combinedCategories();
        };
    }

    private boolean isWithinReceiptRange(String value, String start, String end) {
        String receipt = normalized(value);
        if (receipt.isEmpty()) {
            return false;
        }
        return (start.isEmpty() || compareReceipt(receipt, start) >= 0)
                && (end.isEmpty() || compareReceipt(receipt, end) <= 0);
    }

    private int compareReceipt(String left, String right) {
        if (left.matches("\\d+") && right.matches("\\d+")) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        return left.compareToIgnoreCase(right);
    }

    private boolean compareAmount(long actual, String operator, long expected) {
        return switch (operator == null ? "=" : operator) {
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            default -> actual == expected;
        };
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private void resetSummary() {
        queryResults = List.of();
        for (TextField field : List.of(
                incomeCountField, incomeAmountField, expenseCountField,
                expenseAmountField, totalCountField, totalAmountField
        )) {
            field.clear();
        }
        peopleStatisticsButton.setDisable(true);
        donationDetailsButton.setDisable(true);
    }

    private void showModal(String title, String fxmlFile) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
        Parent root = loader.load();
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.initOwner(incomeCountField.getScene().getWindow());
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().replace('/', '.').replace('-', '.').split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return LocalDate.of(year < 1912 ? year + 1911 : year, month, day);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String currentRocDate() {
        LocalDate today = LocalDate.now();
        return String.format("%03d.%02d.%02d", today.getYear() - 1911, today.getMonthValue(), today.getDayOfMonth());
    }

    private int parseRankingLimit() {
        Integer limit = parsePositiveInteger(rankingLimitField.getText());
        if (limit != null) {
            return limit;
        }
        AlertDialog.showWarning("捐款排行榜", "前幾名請輸入大於 0 的整數");
        return 0;
    }

    private Integer parsePositiveInteger(String text) {
        try {
            int value = Integer.parseInt(normalized(text));
            return value > 0 ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long parseLong(String text) {
        try {
            return Long.parseLong(normalized(text).replace(",", ""));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private record QueryCondition(
            Integer memberId,
            Set<String> allowedDataTypeIds,
            Set<String> selectedCategoryIds,
            LocalDate startDate,
            LocalDate endDate,
            boolean amountEnabled,
            boolean singleAmount,
            String amountOperator,
            Long amount,
            String summary,
            String donateNote,
            String operator,
            boolean receiptEnabled,
            String receiptStart,
            String receiptEnd,
            boolean supplementOnly
    ) {
    }
}
