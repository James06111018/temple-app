package tw.org.il.dongsheng.templeapp.repository;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationRankingRow;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DonationRepository {
    void createTable() throws SQLException;

    Donation save(Donation donation) throws SQLException;

    boolean update(Donation donation) throws SQLException;

    boolean deleteById(int id, String reason) throws SQLException;

    boolean supplementReceipt(int id, String reason, String supplementReceiptNo) throws SQLException;

    Optional<Donation> findById(int id) throws SQLException;

    List<Donation> findByMemberId(int memberId) throws SQLException;

    List<Donation> findByMemberIds(List<Integer> memberIds, int limit, int offset) throws SQLException;
    int getDonationCount(List<Integer> memberIds) throws SQLException;

    List<Donation> findAll(LocalDate startDate, LocalDate endDate, String receiptNo, String creator) throws SQLException;

    List<DonationRankingRow> findRanking(int limit) throws SQLException;
}
