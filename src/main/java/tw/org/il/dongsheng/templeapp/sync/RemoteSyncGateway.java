package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;

import java.util.List;

public interface RemoteSyncGateway {
    RemoteSnapshot fetchChanges(String sinceToken);

    void pushMembers(List<LightMember> members);

    void pushDonations(List<Donation> donations);
}
