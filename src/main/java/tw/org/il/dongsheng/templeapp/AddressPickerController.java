package tw.org.il.dongsheng.templeapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import tw.org.il.dongsheng.templeapp.model.AddressPreset;
import tw.org.il.dongsheng.templeapp.model.AddressRoad;
import tw.org.il.dongsheng.templeapp.model.AddressVillage;
import tw.org.il.dongsheng.templeapp.repository.AddressRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class AddressPickerController {

    @FXML private TextField zipCodeField, prefixField, presetPageField, addressField;
    @FXML private Label presetTotalLabel;
    @FXML private TableView<PresetRow> presetTable;
    @FXML private TableColumn<PresetRow, String> presetNoColumn, presetAddressColumn;
    @FXML private ListView<String> villageList, prefixList, roadList;

    private final List<PresetRow> presets = new ArrayList<>();
    private Consumer<AddressResult> onConfirm;
    private AddressRepository addressRepository;
    private String city = "";
    private String district = "";

    public void setOnConfirm(Consumer<AddressResult> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public void setAddressRepository(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public void setInitialValues(String zipCode, String city, String district, String address) {
        this.city = valueOrEmpty(city);
        this.district = valueOrEmpty(district);
        zipCodeField.setText(valueOrEmpty(zipCode));
        addressField.setText(valueOrEmpty(address));
        loadAddressData();
        villageList.getSelectionModel().selectFirst();
        prefixList.getSelectionModel().selectFirst();
        roadList.getSelectionModel().selectFirst();
        if (addressField.getText().isBlank()) {
            rebuildAddressPreview();
        }
    }

    @FXML
    public void initialize() {
        presetNoColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().number()));
        presetAddressColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().address()));

        loadFallbackData();

        villageList.getSelectionModel().selectFirst();
        prefixList.getSelectionModel().selectFirst();
        roadList.getSelectionModel().selectFirst();

        villageList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> rebuildAddressPreview());
        roadList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> rebuildAddressPreview());
        prefixList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            prefixField.setText(valueOrEmpty(newVal));
            filterRoadsByPrefix(newVal);
        });

        roadList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onConfirm();
            }
        });
        presetTable.setOnMouseClicked(event -> {
            PresetRow selected = presetTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                addressField.setText(selected.address());
            }
            if (event.getClickCount() == 2) {
                onConfirm();
            }
        });
    }

    @FXML
    private void onAppendAddressToken(ActionEvent event) {
        Button source = (Button) event.getSource();
        addressField.appendText(source.getText());
    }

    @FXML
    private void onBackspace() {
        String text = addressField.getText();
        if (!text.isEmpty()) {
            addressField.setText(text.substring(0, text.length() - 1));
        }
    }

    @FXML
    private void onClearAddress() {
        addressField.clear();
        rebuildAddressPreview();
    }

    @FXML
    private void onSavePreset() {
        String address = addressField.getText();
        if (address == null || address.isBlank()) {
            return;
        }
        PresetRow preset = new PresetRow(String.format("%02d", presets.size() + 1), address);
        try {
            if (addressRepository != null) {
                addressRepository.savePreset(new AddressPreset(
                        null,
                        zipCodeField.getText(),
                        city,
                        district,
                        villageList.getSelectionModel().getSelectedItem(),
                        roadList.getSelectionModel().getSelectedItem(),
                        address,
                        presets.size() + 1
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        presets.add(preset);
        presetTable.getItems().add(preset);
        presetTable.getSelectionModel().select(preset);
        presetTotalLabel.setText("總筆數： " + presets.size());
    }

    @FXML
    private void onFirstPreset() {
        selectPreset(0);
    }

    @FXML
    private void onPreviousPreset() {
        selectPreset(Math.max(0, presetTable.getSelectionModel().getSelectedIndex() - 1));
    }

    @FXML
    private void onNextPreset() {
        selectPreset(Math.min(presetTable.getItems().size() - 1, presetTable.getSelectionModel().getSelectedIndex() + 1));
    }

    @FXML
    private void onLastPreset() {
        selectPreset(presetTable.getItems().size() - 1);
    }

    @FXML
    private void onConfirm() {
        if (onConfirm != null) {
            onConfirm.accept(new AddressResult(
                    zipCodeField.getText(),
                    addressField.getText()
            ));
        }
        close();
    }

    private void loadAddressData() {
        if (addressRepository == null) {
            return;
        }
        try {
            List<AddressVillage> villages = addressRepository.findVillages(city, district);
            if (!villages.isEmpty()) {
                villageList.setItems(FXCollections.observableArrayList(
                        villages.stream().map(AddressVillage::getVillage).toList()
                ));
            }

            List<AddressRoad> roads = addressRepository.findRoads(city, district);
            if (!roads.isEmpty()) {
                roadList.setItems(FXCollections.observableArrayList(
                        roads.stream().map(AddressRoad::getRoad).toList()
                ));
                prefixList.setItems(FXCollections.observableArrayList(
                        roads.stream().map(AddressRoad::getPrefix).distinct().sorted().toList()
                ));
            }

            List<AddressPreset> savedPresets = addressRepository.findPresets(city, district);
            if (!savedPresets.isEmpty()) {
                presets.clear();
                for (int i = 0; i < savedPresets.size(); i++) {
                    presets.add(new PresetRow(String.format("%02d", i + 1), savedPresets.get(i).getAddress()));
                }
                presetTable.setItems(FXCollections.observableArrayList(presets));
                presetTotalLabel.setText("總筆數： " + presets.size());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void loadFallbackData() {
        if (villageList.getItems().isEmpty()) {
            villageList.setItems(FXCollections.observableArrayList(
                    "七張里", "七結里", "大新里", "小東里", "中山里", "中興里", "文化里", "北門里",
                    "民權里", "成功里", "西門里", "孝廉里", "東村里", "南津里", "建業里", "思源里",
                    "泰山里", "神農里", "菜園里", "進士里"
            ));
        }
        if (prefixList.getItems().isEmpty()) {
            prefixList.setItems(FXCollections.observableArrayList(
                    "一", "七", "力", "三", "大", "女", "小", "中", "北", "民", "成", "西", "孝", "東"
            ));
        }
        if (roadList.getItems().isEmpty()) {
            roadList.setItems(FXCollections.observableArrayList(defaultRoads()));
        }
        if (presets.isEmpty()) {
            seedPresets();
        }
        presetTable.setItems(FXCollections.observableArrayList(presets));
        presetTotalLabel.setText("總筆數： " + presets.size());
    }

    private void seedPresets() {
        presets.clear();
        presets.add(new PresetRow("01", "宜蘭縣宜蘭市凱旋路123號3樓之1"));
        for (int i = 2; i <= 20; i++) {
            presets.add(new PresetRow(String.format("%02d", i), ""));
        }
    }

    private void selectPreset(int index) {
        if (presetTable.getItems().isEmpty() || index < 0) {
            return;
        }
        presetTable.getSelectionModel().select(index);
        presetTable.scrollTo(index);
        PresetRow selected = presetTable.getSelectionModel().getSelectedItem();
        if (selected != null && !selected.address().isBlank()) {
            addressField.setText(selected.address());
        }
    }

    private void filterRoadsByPrefix(String prefix) {
        List<String> allRoads = defaultRoads();
        try {
            if (addressRepository != null) {
                List<AddressRoad> roads = prefix == null || prefix.isBlank()
                        ? addressRepository.findRoads(city, district)
                        : addressRepository.findRoadsByPrefix(city, district, prefix);
                if (!roads.isEmpty()) {
                    allRoads = roads.stream().map(AddressRoad::getRoad).toList();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (prefix == null || prefix.isBlank()) {
            roadList.setItems(FXCollections.observableArrayList(allRoads));
        } else {
            roadList.setItems(FXCollections.observableArrayList(
                    allRoads.stream().filter(road -> road.startsWith(prefix)).toList()
            ));
        }
        roadList.getSelectionModel().selectFirst();
        rebuildAddressPreview();
    }

    private void rebuildAddressPreview() {
        String village = valueOrEmpty(villageList.getSelectionModel().getSelectedItem());
        String road = valueOrEmpty(roadList.getSelectionModel().getSelectedItem());
        String prefix = city + district + village + road;
        if (addressField.getText() == null || addressField.getText().isBlank()
                || startsWithLocationPrefix(addressField.getText())) {
            addressField.setText(prefix);
            addressField.positionCaret(addressField.getText().length());
        }
    }

    private boolean startsWithLocationPrefix(String value) {
        return value.startsWith(city + district)
                || value.startsWith(city)
                || value.startsWith(district);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<String> defaultRoads() {
        return Arrays.asList(
                "一路", "七張路", "力行街", "力行巷", "力行路", "中興路", "三清路",
                "大坡路一段", "大坡路二段", "大福路一段", "大福路二段", "女中路",
                "女中路一段", "女中路二段", "女中路三段", "小東路", "中山路一段",
                "中山路二段", "中山路三段", "中山路三段中央商場"
        );
    }

    private void close() {
        Stage stage = (Stage) addressField.getScene().getWindow();
        stage.close();
    }

    public record PresetRow(String number, String address) {
    }

    public record AddressResult(String zipCode, String address) {
    }
}
