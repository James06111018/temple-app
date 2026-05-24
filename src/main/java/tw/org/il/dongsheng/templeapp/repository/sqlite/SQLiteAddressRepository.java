package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.AddressPreset;
import tw.org.il.dongsheng.templeapp.model.AddressRoad;
import tw.org.il.dongsheng.templeapp.model.AddressVillage;
import tw.org.il.dongsheng.templeapp.repository.AddressRepository;
import tw.org.il.dongsheng.templeapp.util.AreaUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteAddressRepository implements AddressRepository {
    private final SQLiteDatabaseManager databaseManager;

    public SQLiteAddressRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createTable() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS address_villages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        city TEXT NOT NULL,
                        district TEXT NOT NULL,
                        village TEXT NOT NULL,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(city, district, village)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS address_roads (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        city TEXT NOT NULL,
                        district TEXT NOT NULL,
                        road TEXT NOT NULL,
                        prefix TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(city, district, road)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS address_presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        zip_code TEXT,
                        city TEXT,
                        district TEXT,
                        village TEXT,
                        road TEXT,
                        address TEXT NOT NULL,
                        sort_order INTEGER,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_address_roads_area ON address_roads(city, district)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_address_roads_prefix ON address_roads(city, district, prefix)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_address_villages_area ON address_villages(city, district)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_address_presets_area ON address_presets(city, district)");
        }
    }

    @Override
    public int replaceRoads(List<AddressRoad> roads) throws SQLException {
        createTable();
        String sql = """
                INSERT OR IGNORE INTO address_roads (city, district, road, prefix)
                VALUES (?, ?, ?, ?)
                """;
        int imported = 0;
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.execute("DELETE FROM address_roads");
            for (AddressRoad road : roads) {
                preparedStatement.setString(1, AreaUtil.normalizeCityName(road.getCity()));
                preparedStatement.setString(2, AreaUtil.normalizeDistrictName(road.getDistrict()));
                preparedStatement.setString(3, road.getRoad());
                preparedStatement.setString(4, road.getPrefix());
                preparedStatement.addBatch();
            }
            int[] rows = preparedStatement.executeBatch();
            connection.commit();
            for (int row : rows) {
                if (row > 0 || row == Statement.SUCCESS_NO_INFO) {
                    imported++;
                }
            }
        }
        return imported;
    }

    @Override
    public int replaceVillages(List<AddressVillage> villages) throws SQLException {
        createTable();
        String sql = """
                INSERT OR IGNORE INTO address_villages (city, district, village)
                VALUES (?, ?, ?)
                """;
        int imported = 0;
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.execute("DELETE FROM address_villages");
            for (AddressVillage village : villages) {
                preparedStatement.setString(1, AreaUtil.normalizeCityName(village.getCity()));
                preparedStatement.setString(2, AreaUtil.normalizeDistrictName(village.getDistrict()));
                preparedStatement.setString(3, village.getVillage());
                preparedStatement.addBatch();
            }
            int[] rows = preparedStatement.executeBatch();
            connection.commit();
            for (int row : rows) {
                if (row > 0 || row == Statement.SUCCESS_NO_INFO) {
                    imported++;
                }
            }
        }
        return imported;
    }

    @Override
    public AddressPreset savePreset(AddressPreset preset) throws SQLException {
        createTable();
        String sql = """
                INSERT INTO address_presets (zip_code, city, district, village, road, address, sort_order, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, preset.getZipCode());
            statement.setString(2, AreaUtil.normalizeCityName(preset.getCity()));
            statement.setString(3, AreaUtil.normalizeDistrictName(preset.getDistrict()));
            statement.setString(4, preset.getVillage());
            statement.setString(5, preset.getRoad());
            statement.setString(6, preset.getAddress());
            statement.setObject(7, preset.getSortOrder());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    preset.setId(generatedKeys.getInt(1));
                }
            }
        }
        return preset;
    }

    @Override
    public List<AddressRoad> findRoads(String city, String district) throws SQLException {
        return findRoads(city, district, null);
    }

    @Override
    public List<AddressRoad> findRoadsByPrefix(String city, String district, String prefix) throws SQLException {
        return findRoads(city, district, prefix);
    }

    @Override
    public List<AddressVillage> findVillages(String city, String district) throws SQLException {
        List<AddressVillage> villages = findVillagesByArea(city, district);
        if (villages.isEmpty() && district != null && !district.isBlank()) {
            return findVillagesByArea(city, null);
        }
        return villages;
    }

    private List<AddressVillage> findVillagesByArea(String city, String district) throws SQLException {
        createTable();
        String sql = """
                SELECT id, city, district, village
                FROM address_villages
                WHERE (? IS NULL OR REPLACE(city, '臺', '台') = ?)
                  AND (? IS NULL OR REPLACE(REPLACE(district, '　', ''), ' ', '') = ?)
                ORDER BY village
                """;
        List<AddressVillage> villages = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableFilter(statement, 1, city);
            setNullableFilter(statement, 3, district);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    villages.add(new AddressVillage(
                            resultSet.getInt("id"),
                            resultSet.getString("city"),
                            resultSet.getString("district"),
                            resultSet.getString("village")
                    ));
                }
            }
        }
        return villages;
    }

    @Override
    public List<AddressPreset> findPresets(String city, String district) throws SQLException {
        createTable();
        String sql = """
                SELECT id, zip_code, city, district, village, road, address, sort_order
                FROM address_presets
                WHERE (? IS NULL OR REPLACE(city, '臺', '台') = ?)
                  AND (? IS NULL OR REPLACE(REPLACE(district, '　', ''), ' ', '') = ?)
                ORDER BY COALESCE(sort_order, id), id
                """;
        List<AddressPreset> presets = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableFilter(statement, 1, city);
            setNullableFilter(statement, 3, district);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    presets.add(mapPreset(resultSet));
                }
            }
        }
        return presets;
    }

    private List<AddressRoad> findRoads(String city, String district, String prefix) throws SQLException {
        createTable();
        String sql = """
                SELECT id, city, district, road, prefix
                FROM address_roads
                WHERE (? IS NULL OR REPLACE(city, '臺', '台') = ?)
                  AND (? IS NULL OR REPLACE(REPLACE(district, '　', ''), ' ', '') = ?)
                  AND (? IS NULL OR prefix = ?)
                ORDER BY road
                """;
        List<AddressRoad> roads = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableFilter(statement, 1, city);
            setNullableFilter(statement, 3, district);
            setNullableFilter(statement, 5, prefix);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    roads.add(new AddressRoad(
                            resultSet.getInt("id"),
                            resultSet.getString("city"),
                            resultSet.getString("district"),
                            resultSet.getString("road"),
                            resultSet.getString("prefix")
                    ));
                }
            }
        }
        return roads;
    }

    private void setNullableFilter(PreparedStatement statement, int index, String value) throws SQLException {
        String normalized = normalizeFilter(value);
        statement.setString(index, normalized);
        statement.setString(index + 1, normalized);
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank()
                ? null
                : AreaUtil.normalizeDistrictName(AreaUtil.normalizeCityName(value));
    }

    private AddressPreset mapPreset(ResultSet resultSet) throws SQLException {
        return new AddressPreset(
                resultSet.getInt("id"),
                resultSet.getString("zip_code"),
                resultSet.getString("city"),
                resultSet.getString("district"),
                resultSet.getString("village"),
                resultSet.getString("road"),
                resultSet.getString("address"),
                (Integer) resultSet.getObject("sort_order")
        );
    }
}
