package tw.org.il.dongsheng.templeapp.service;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.DonationRankingRow;
import tw.org.il.dongsheng.templeapp.repository.DonationRepository;

import java.sql.SQLException;
import java.util.List;

public class DonationService {

    private final DonationRepository repo;

    public DonationService(DonationRepository repo) {
        this.repo = repo;
    }

    public List<Donation> findByMemberIds(List<Integer> memberIds, int limit, int offset) throws SQLException {
        return repo.findByMemberIds(memberIds, limit, offset);
    }

    public List<Donation> findByMemberId(int memberId) throws SQLException {
        return repo.findByMemberId(memberId);
    }

    public int getDonationCount(List<Integer> memberIds) throws SQLException {
        return repo.getDonationCount(memberIds);
    }

    public List<DonationRankingRow> findRanking(int limit) throws SQLException {
        return repo.findRanking(limit);
    }

    public Donation save(Donation donation) throws SQLException {
        return repo.save(donation);
    }

    public void update(Donation donation) throws SQLException {
        repo.update(donation);
    }

    public boolean deleteById(int id, String reason) throws SQLException {
        return repo.deleteById(id, reason);
    }

    public boolean supplementReceipt(int id, String reason, String supplementReceiptNo) throws SQLException {
        return repo.supplementReceipt(id, reason, supplementReceiptNo);
    }
}
