package tw.org.il.dongsheng.templeapp.service;

import tw.org.il.dongsheng.templeapp.model.Donation;
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

    public int getDonationCount(List<Integer> memberIds) throws SQLException {
        return repo.getDonationCount(memberIds);
    }

    public Donation save(Donation donation) throws SQLException {
        return repo.save(donation);
    }

    public void update(Donation donation) throws SQLException {
        repo.update(donation);
    }
}
