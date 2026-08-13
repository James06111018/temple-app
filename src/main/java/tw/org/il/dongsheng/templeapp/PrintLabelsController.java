package tw.org.il.dongsheng.templeapp;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.LightMemberRepository;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteDatabaseManager;
import tw.org.il.dongsheng.templeapp.repository.sqlite.SQLiteLightMemberRepository;
import tw.org.il.dongsheng.templeapp.util.AlertDialog;
import tw.org.il.dongsheng.templeapp.util.PrintLabelsReportBuilder;
import tw.org.il.dongsheng.templeapp.util.PrintPreview;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class PrintLabelsController {

    @FXML private RadioButton memberIdRadio;
    @FXML private RadioButton selectedIdsRadio;
    @FXML private TextField memberIdRangeField;
    @FXML private TextField selectedIdsField;
    @FXML private ComboBox<String> reportTypeBox;
    @FXML private ComboBox<String> startPositionBox;
    @FXML private CheckBox sortByZipCheck;
    @FXML private CheckBox includeAllMailCheck;
    @FXML private CheckBox zipFilterCheck;
    @FXML private TextField zipStartField;
    @FXML private TextField zipEndField;

    private final LightMemberRepository memberRepository = new SQLiteLightMemberRepository(
            SQLiteDatabaseManager.getInstance()
    );

    @FXML
    private void initialize() {
        memberIdRadio.setSelected(true);
        reportTypeBox.setItems(FXCollections.observableArrayList(
                "1. 郵寄標籤(橫式)",
                "2. 郵寄標籤(直式)",
                "3. 名冊",
                "4. 通信函"
        ));
        reportTypeBox.getSelectionModel().selectFirst();
        refreshStartPositions(0);

        selectedIdsField.setDisable(true);
        memberIdRadio.selectedProperty().addListener((observable, oldValue, selected) -> {
            memberIdRangeField.setDisable(!selected);
            selectedIdsField.setDisable(selected);
        });
        zipStartField.disableProperty().bind(zipFilterCheck.selectedProperty().not());
        zipEndField.disableProperty().bind(zipFilterCheck.selectedProperty().not());
        reportTypeBox.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, selectedIndex) -> refreshStartPositions(selectedIndex.intValue())
        );
    }

    private void refreshStartPositions(int reportType) {
        boolean labelReport = reportType == 0 || reportType == 1;
        startPositionBox.setDisable(!labelReport);
        if (!labelReport) {
            return;
        }

        int previousPosition = startPositionBox.getValue() == null
                ? 1
                : Integer.parseInt(startPositionBox.getValue());
        int slots = reportType == 1
                ? PrintLabelsReportBuilder.verticalLabelSlotsPerPage()
                : PrintLabelsReportBuilder.horizontalLabelSlotsPerPage();
        startPositionBox.setItems(FXCollections.observableArrayList(
                IntStream.rangeClosed(1, slots).mapToObj(String::valueOf).toList()
        ));
        startPositionBox.getSelectionModel().select(Math.min(previousPosition, slots) - 1);
    }

    @FXML
    private void onPrint() {
        try {
            IdCriteria idCriteria = parseIdCriteria();
            if (idCriteria == null) {
                return;
            }
            ZipCriteria zipCriteria = parseZipCriteria();
            if (zipCriteria == null) {
                return;
            }

            List<LightMember> members = memberRepository.findAll().stream()
                    .filter(this::hasPrintableName)
                    .filter(member -> idCriteria.matches(member.getId()))
                    .filter(member -> includeAllMailCheck.isSelected() || isMarkedForMail(member))
                    .filter(member -> zipCriteria.matches(member.getZipCode()))
                    .sorted(memberComparator())
                    .toList();
            if (members.isEmpty()) {
                AlertDialog.showWarning("列印標籤", "依目前條件查無可列印的信眾資料");
                return;
            }

            int reportType = reportTypeBox.getSelectionModel().getSelectedIndex();
            int startPosition = Integer.parseInt(startPositionBox.getValue());
            var pages = switch (reportType) {
                case 0 -> PrintLabelsReportBuilder.buildHorizontalLabelPages(members, startPosition);
                case 1 -> PrintLabelsReportBuilder.buildVerticalLabelPages(members, startPosition);
                case 2 -> PrintLabelsReportBuilder.buildRosterPages(members);
                case 3 -> PrintLabelsReportBuilder.buildLetterPages(members);
                default -> List.<javafx.scene.layout.Region>of();
            };
            PrintPreview.show(reportTypeBox.getScene().getWindow(), reportTypeBox.getValue(), pages);
        } catch (SQLException e) {
            AlertDialog.showError("列印標籤", "讀取信眾資料失敗：" + e.getMessage());
        }
    }

    private IdCriteria parseIdCriteria() {
        if (selectedIdsRadio.isSelected()) {
            String input = selectedIdsField.getText() == null ? "" : selectedIdsField.getText().trim();
            if (input.isEmpty()) {
                AlertDialog.showWarning("列印標籤", "請輸入單選編號，例如 1,3,5,100");
                return null;
            }
            Set<Integer> ids = new HashSet<>();
            for (String value : input.split("[,，\\s]+")) {
                Integer id = parsePositiveNumber(value);
                if (id == null) {
                    AlertDialog.showWarning("列印標籤", "單選編號格式不正確：" + value);
                    return null;
                }
                ids.add(id);
            }
            return new IdCriteria(null, null, ids);
        }

        String input = memberIdRangeField.getText() == null ? "" : memberIdRangeField.getText().trim();
        if (input.isEmpty()) {
            return IdCriteria.all();
        }
        String[] parts = input.split("[-~～]", -1);
        if (parts.length == 1) {
            Integer id = parsePositiveNumber(parts[0]);
            if (id != null) {
                return new IdCriteria(id, id, Set.of());
            }
        } else if (parts.length == 2) {
            Integer start = parsePositiveNumber(parts[0]);
            Integer end = parsePositiveNumber(parts[1]);
            if (start != null && end != null && start <= end) {
                return new IdCriteria(start, end, Set.of());
            }
        }
        AlertDialog.showWarning("列印標籤", "電腦編號請輸入單一編號或起訖範圍，例如 1-100");
        return null;
    }

    private ZipCriteria parseZipCriteria() {
        if (!zipFilterCheck.isSelected()) {
            return ZipCriteria.all();
        }
        Integer start = parsePositiveNumber(zipStartField.getText());
        Integer end = parsePositiveNumber(zipEndField.getText());
        if (start == null || end == null || start > end) {
            AlertDialog.showWarning("列印標籤", "郵遞區號篩選請輸入正確的起訖區號");
            return null;
        }
        return new ZipCriteria(start, end);
    }

    private Comparator<LightMember> memberComparator() {
        Comparator<LightMember> byId = Comparator.comparing(
                LightMember::getId,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
        if (!sortByZipCheck.isSelected()) {
            return byId;
        }
        return Comparator.comparingInt((LightMember member) -> zipNumber(member.getZipCode()))
                .thenComparing(byId);
    }

    private boolean hasPrintableName(LightMember member) {
        return member != null && member.getId() != null
                && member.getName() != null && !member.getName().isBlank();
    }

    private boolean isMarkedForMail(LightMember member) {
        return "Y".equalsIgnoreCase(safe(member.getIsMail()).trim());
    }

    private static int zipNumber(String zipCode) {
        Integer value = parsePositiveNumber(zipCode);
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static Integer parsePositiveNumber(String value) {
        if (value == null || !value.trim().matches("\\d+")) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record IdCriteria(Integer start, Integer end, Set<Integer> selectedIds) {
        static IdCriteria all() {
            return new IdCriteria(null, null, Set.of());
        }

        boolean matches(Integer id) {
            if (id == null) {
                return false;
            }
            if (!selectedIds.isEmpty()) {
                return selectedIds.contains(id);
            }
            return start == null || (id >= start && id <= end);
        }
    }

    private record ZipCriteria(Integer start, Integer end) {
        static ZipCriteria all() {
            return new ZipCriteria(null, null);
        }

        boolean matches(String zipCode) {
            if (start == null) {
                return true;
            }
            int value = zipNumber(zipCode);
            return value >= start && value <= end;
        }
    }
}
