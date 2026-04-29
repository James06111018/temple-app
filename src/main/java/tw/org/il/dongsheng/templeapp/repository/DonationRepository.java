package tw.org.il.dongsheng.templeapp.repository;

import tw.org.il.dongsheng.templeapp.model.Donation;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface DonationRepository {
    void createTable() throws SQLException;

    Donation save(Donation donation) throws SQLException;

    boolean update(Donation donation) throws SQLException;

    boolean deleteById(int id) throws SQLException;

    Optional<Donation> findById(int id) throws SQLException;

    List<Donation> findByMemberId(int memberId) throws SQLException;

    List<Donation> findByMemberIds(List<Integer> memberIds, int limit, int offset) throws SQLException;
    int getDonationCount(List<Integer> memberIds) throws SQLException;

    List<Donation> findAll() throws SQLException;
}
