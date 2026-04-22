package tw.org.il.dongsheng.templeapp.repository.sqlite;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.repository.DonationRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteDonationRepository implements DonationRepository {
    private static final String TABLE_NAME = "donations";

    private final SQLiteDatabaseManager databaseManager;

    public SQLiteDonationRepository(SQLiteDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "member_id INTEGER NOT NULL," +
                "receipt_no TEXT," +
                "donate_date TEXT," +
                "extra_no TEXT," +
                "amount INTEGER," +
                "summary TEXT," +
                "donate_note TEXT," +
                "other_note TEXT," +
                "donor_no TEXT," +
                "light_no TEXT," +
                "should_pay INTEGER," +
                "donate_type TEXT," +
                "FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE" +
                ")";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute(sql);
        }
    }

    @Override
    public Donation save(Donation donation) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " (" +
                "member_id, receipt_no, donate_date, extra_no, amount, summary, donate_note, other_note, donor_no, light_no, should_pay, donate_type" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            setCommonFields(statement, donation);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    donation.setId(generatedKeys.getInt(1));
                }
            }
        }

        return donation;
    }

    @Override
    public boolean update(Donation donation) throws SQLException {
        if (donation.getId() == null) {
            throw new IllegalArgumentException("Donation id cannot be null when updating.");
        }

        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "member_id = ?, receipt_no = ?, donate_date = ?, extra_no = ?, amount = ?, summary = ?, donate_note = ?, other_note = ?, donor_no = ?, " +
                "light_no = ?, should_pay = ?, donate_type = ? " +
                "WHERE id = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            setCommonFields(statement, donation);
            statement.setObject(13, donation.getId());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deleteById(int id) throws SQLException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<Donation> findById(int id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public List<Donation> findByMemberId(int memberId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE member_id = ? ORDER BY donate_date DESC, id DESC";
        List<Donation> donations = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, memberId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    donations.add(mapRow(resultSet));
                }
            }
        }

        return donations;
    }

    @Override
    public List<Donation> findAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY donate_date DESC, id DESC";
        List<Donation> donations = new ArrayList<>();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                donations.add(mapRow(resultSet));
            }
        }

        return donations;
    }

    private void setCommonFields(PreparedStatement statement, Donation donation) throws SQLException {
        statement.setObject(1, donation.getMemberId());
        statement.setString(2, donation.getReceiptNo());
        statement.setString(3, donation.getDonateDate());
        statement.setString(4, donation.getExtraNo());
        statement.setObject(5, donation.getAmount());
        statement.setString(6, donation.getSummary());
        statement.setString(7, donation.getDonateNote());
        statement.setString(8, donation.getOtherNote());
        statement.setString(9, donation.getDonorNo());
        statement.setString(10, donation.getLightNo());
        statement.setObject(11, donation.getShouldPay());
        statement.setString(12, donation.getDonateType());
    }

    private Donation mapRow(ResultSet resultSet) throws SQLException {
        Donation donation = new Donation();
        donation.setId(resultSet.getInt("id"));
        donation.setMemberId(resultSet.getInt("member_id"));
        donation.setReceiptNo(resultSet.getString("receipt_no"));
        donation.setDonateDate(resultSet.getString("donate_date"));
        donation.setExtraNo(resultSet.getString("extra_no"));
        donation.setAmount((Integer) resultSet.getObject("amount"));
        donation.setSummary(resultSet.getString("summary"));
        donation.setDonateNote(resultSet.getString("donate_note"));
        donation.setOtherNote(resultSet.getString("other_note"));
        donation.setDonorNo(resultSet.getString("donor_no"));
        donation.setLightNo(resultSet.getString("light_no"));
        donation.setShouldPay((Integer) resultSet.getObject("should_pay"));
        donation.setDonateType(resultSet.getString("donate_type"));
        return donation;
    }
}

