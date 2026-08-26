package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.util.List;

public class RemoteSnapshot {
    private final String nextToken;
    private final List<LightMember> members;
    private final List<Donation> donations;

    public RemoteSnapshot(String nextToken, List<LightMember> members, List<Donation> donations) {
        this.nextToken = nextToken;
        this.members = members;
        this.donations = donations;
    }

    public String getNextToken() {
        return nextToken;
    }

    public List<LightMember> getMembers() {
        return members;
    }

    public List<Donation> getDonations() {
        return donations;
    }
}
