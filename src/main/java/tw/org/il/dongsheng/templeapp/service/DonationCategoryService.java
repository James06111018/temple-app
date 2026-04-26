package tw.org.il.dongsheng.templeapp.service;

import tw.org.il.dongsheng.templeapp.model.DonationCategory;
import tw.org.il.dongsheng.templeapp.repository.DonationCategoryRepository;

import java.sql.SQLException;
import java.util.List;

public class DonationCategoryService {

    private final DonationCategoryRepository repo;

    public DonationCategoryService(DonationCategoryRepository repo) {
        this.repo = repo;
    }

    public List<DonationCategory> findAll() throws SQLException {
        return repo.findAll();
    }

    public DonationCategory save(DonationCategory dc) throws SQLException {
        return repo.save(dc);
    }

    public boolean update(DonationCategory dc) throws SQLException {
        return repo.update(dc);
    }

    public void disable(String code) throws SQLException {
        repo.disable(code);
    }
}
