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

    LightMember reserveBlankMember() throws SQLException;

    Optional<LightMember> findById(int id) throws SQLException;

    Optional<LightMember> findByName(String name) throws SQLException;

    List<LightMember> search(String id, String name, String phone) throws SQLException;

    List<LightMember> findByAddress(String keyword, int limit, int offset) throws SQLException;
    int getMemberCount(String keyword) throws SQLException;
    List<LightMember> findAll() throws SQLException;

    List<Integer> findDeletedIds() throws SQLException;

    List<Integer> findBlankNameIds() throws SQLException;

    int getNextId() throws SQLException;
}
