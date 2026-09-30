package tw.org.il.dongsheng.templeapp.service;

import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.MemberBatchUpdateRequest;
import tw.org.il.dongsheng.templeapp.repository.LightMemberRepository;
import tw.org.il.dongsheng.templeapp.util.MemberCalendar;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class LightMemberService {

    private final LightMemberRepository repo;

    public LightMemberService(LightMemberRepository repo) {
        this.repo = repo;
    }

    public int getNextId() throws SQLException {
        return repo.getNextId();
    }

    public LightMember reserveBlankMember() throws SQLException {
        return repo.reserveBlankMember();
    }

    public Optional<LightMember> findByName(String name) throws SQLException {
        return repo.findByName(name);
    }

    public List<LightMember> search(String id, String name, String phone) throws SQLException {
        return withDerivedBirthData(repo.search(id, name, phone));
    }

    public List<LightMember> findAllHouse(String keyword, int limit, int offset) throws SQLException {
        return withDerivedBirthData(repo.findByAddress(keyword, limit, offset));
    }

    public int getMemberCount(String keyword) throws SQLException {
        return repo.getMemberCount(keyword);
    }

    public boolean exists(int id) throws SQLException {
        Optional<LightMember> finds = repo.findById(id);
        return finds != null && !finds.isEmpty();
    }


    public void save(LightMember member) throws SQLException {
        repo.save(member);
    }

    public void update(LightMember member) throws SQLException {
        repo.update(member);
    }

    /** 同地址者視為同一戶，丁口由當日年齡即時計算，不回寫資料庫。 */
    public HouseholdCount calculateHouseholdCount(String address) throws SQLException {
        if (address == null || address.isBlank()) {
            return HouseholdCount.EMPTY;
        }
        List<LightMember> members = withDerivedBirthData(repo.findByAddress(address, Integer.MAX_VALUE, 0));
        int ding = 0;
        int kou = 0;
        for (LightMember member : members) {
            Integer age = member.getAge();
            if (age == null) {
                continue;
            }
            if ("男".equals(member.getGender()) && age >= 16 && age <= 60) {
                ding++;
            } else {
                kou++;
            }
        }
        return new HouseholdCount(ding, kou);
    }

    public boolean deleteById(int id) throws SQLException {
        return repo.deleteById(id);
    }

    public int batchUpdateContact(MemberBatchUpdateRequest request) throws SQLException {
        return repo.batchUpdateContact(request);
    }

    public List<Integer> findDeletedIds() throws SQLException {
        return repo.findDeletedIds();
    }

    public List<Integer> findBlankNameIds() throws SQLException {
        return repo.findBlankNameIds();
    }

    private List<LightMember> withDerivedBirthData(List<LightMember> members) {
        for (LightMember member : members) {
            MemberCalendar.populateMissingLunarBirthDate(member);
            MemberCalendar.populateDerivedFields(member);
        }
        return members;
    }

    public record HouseholdCount(int ding, int kou) {
        public static final HouseholdCount EMPTY = new HouseholdCount(0, 0);
    }
}
