package tw.org.il.dongsheng.templeapp.repository;

import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface LightMemberRepository {

    void createTable() throws SQLException;

    LightMember save(LightMember member) throws SQLException;

    boolean update(LightMember member) throws SQLException;

    boolean deleteById(int id) throws SQLException;

    Optional<LightMember> findById(int id) throws SQLException;

    Optional<LightMember> findByName(String name) throws SQLException;

    List<LightMember> findByPhone(String phone) throws SQLException;
    List<LightMember> findAll() throws SQLException;

    int getNextId() throws SQLException;
}