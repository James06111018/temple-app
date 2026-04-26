package tw.org.il.dongsheng.templeapp.repository;


import tw.org.il.dongsheng.templeapp.model.DonationCategory;

import java.sql.SQLException;
import java.util.List;

public interface DonationCategoryRepository {

    void createTable() throws SQLException;

    DonationCategory save(DonationCategory dc) throws SQLException;

    boolean update(DonationCategory dc) throws SQLException;

    List<DonationCategory> findAll() throws SQLException;

    boolean disable(String code) throws SQLException;

}
