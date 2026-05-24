package tw.org.il.dongsheng.templeapp.service;

import tw.org.il.dongsheng.templeapp.model.AddressRoad;
import tw.org.il.dongsheng.templeapp.repository.AddressRepository;
import tw.org.il.dongsheng.templeapp.util.CsvUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddressRoadImportService {
    private final AddressRepository repository;

    public AddressRoadImportService(AddressRepository repository) {
        this.repository = repository;
    }

    public int importCsv(Path csvPath) throws IOException, SQLException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return importCsv(reader);
        }
    }

    public int importCsv(URI csvUri) throws IOException, SQLException {
        URL url = csvUri.toURL();
        try (InputStream inputStream = url.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return importCsv(reader);
        }
    }

    private int importCsv(BufferedReader reader) throws IOException, SQLException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return 0;
        }

        List<String> headers = CsvUtil.parseLine(removeBom(headerLine));
        Map<String, Integer> indexMap = buildIndexMap(headers);
        int cityIndex = findRequiredIndex(indexMap, "city", "縣市名稱", "縣市");
        int districtIndex = findRequiredIndex(indexMap, "site_id", "行政區域名稱", "鄉鎮市區", "行政區");
        int roadIndex = findRequiredIndex(indexMap, "road", "路名");

        List<AddressRoad> roads = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            List<String> values = CsvUtil.parseLine(line);
            String city = getValue(values, cityIndex);
            String district = normalizeDistrict(city, getValue(values, districtIndex));
            String road = getValue(values, roadIndex);
            if (city.isBlank() || district.isBlank() || road.isBlank()) {
                continue;
            }
            roads.add(new AddressRoad(null, city, district, road, road.substring(0, 1)));
        }

        return repository.replaceRoads(roads);
    }

    private Map<String, Integer> buildIndexMap(List<String> headers) {
        Map<String, Integer> indexMap = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            indexMap.put(normalizeHeader(headers.get(i)), i);
        }
        return indexMap;
    }

    private int findRequiredIndex(Map<String, Integer> indexMap, String... names) {
        for (String name : names) {
            Integer index = indexMap.get(normalizeHeader(name));
            if (index != null) {
                return index;
            }
        }
        throw new IllegalArgumentException("CSV 缺少必要欄位：" + String.join("/", names));
    }

    private String getValue(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            return "";
        }
        return values.get(index).trim();
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : removeBom(value).trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDistrict(String city, String district) {
        if (city == null || district == null) {
            return district == null ? "" : district;
        }
        if (district.startsWith(city)) {
            return district.substring(city.length()).trim();
        }
        return district;
    }

    private String removeBom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value;
    }
}
