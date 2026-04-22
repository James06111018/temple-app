package tw.org.il.dongsheng.templeapp.service;

import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.repository.LightMemberRepository;

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

    public Optional<LightMember> findByName(String name) throws SQLException {
        return repo.findByName(name);
    }

    public List<LightMember> findAllHouse(String phone) throws SQLException {
        return repo.findByPhone(phone);
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
}
