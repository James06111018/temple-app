package tw.org.il.dongsheng.templeapp;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseholdCountControllerTest {

    @Test
    void countsSameNormalizedFullAddressAsOneHousehold() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createMemberTable(connection);
            insertMember(connection, 1, "臺北市", "中正區", "忠孝東路 1 號", 0);
            insertMember(connection, 2, "臺北市", "中正區", "忠孝東路　1號", 0);

            assertEquals(1, queryHouseholdCount(connection));
        }
    }

    @Test
    void keepsSameStreetAddressInDifferentCitiesAsSeparateHouseholds() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createMemberTable(connection);
            insertMember(connection, 1, "臺北市", "中正區", "中山路1號", 0);
            insertMember(connection, 2, "臺中市", "中區", "中山路1號", 0);

            assertEquals(2, queryHouseholdCount(connection));
        }
    }

    @Test
    void countsBlankAddressesSeparatelyAndExcludesDeletedMembers() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createMemberTable(connection);
            insertMember(connection, 1, "", "", "", 0);
            insertMember(connection, 2, "", "", "", 0);
            insertMember(connection, 3, "宜蘭縣", "冬山鄉", "冬山路2號", 0);
            insertMember(connection, 4, "宜蘭縣", "冬山鄉", "冬山路2號", 1);

            assertEquals(3, queryHouseholdCount(connection));
        }
    }

    private static void createMemberTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE light_members (
                        id INTEGER PRIMARY KEY,
                        city TEXT,
                        dist TEXT,
                        address TEXT,
                        is_deleted INTEGER DEFAULT 0
                    )
                    """);
        }
    }

    private static void insertMember(
            Connection connection,
            int id,
            String city,
            String dist,
            String address,
            int deleted
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO light_members (id, city, dist, address, is_deleted)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setInt(1, id);
            statement.setString(2, city);
            statement.setString(3, dist);
            statement.setString(4, address);
            statement.setInt(5, deleted);
            statement.executeUpdate();
        }
    }

    private static int queryHouseholdCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(HouseholdCountController.HOUSEHOLD_COUNT_SQL)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }
}
