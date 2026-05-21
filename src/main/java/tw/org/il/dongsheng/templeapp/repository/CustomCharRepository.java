package tw.org.il.dongsheng.templeapp.repository;

import tw.org.il.dongsheng.templeapp.model.CustomChar;

import java.sql.SQLException;
import java.util.List;

public interface CustomCharRepository {
    void createTable() throws SQLException;

    void seedDefaults() throws SQLException;

    List<CustomChar> findEnabled() throws SQLException;
}
