package tw.org.il.dongsheng.templeapp.sync;

import tw.org.il.dongsheng.templeapp.model.Donation;
import tw.org.il.dongsheng.templeapp.model.LightMember;
import tw.org.il.dongsheng.templeapp.model.AppFunction;
import tw.org.il.dongsheng.templeapp.model.AppRole;

import java.util.List;

public interface RemoteSyncGateway {
    RemoteSnapshot fetchChanges(String sinceToken);

    void pushMembers(List<LightMember> members);

    void pushDonations(List<Donation> donations);

    void pushLightNumbers(List<LightNumberSyncRow> lightNumbers);

    void pushHouseholdLightRecords(List<HouseholdLightSyncRow> records);

    void pushDonationSupplements(List<DonationSupplementSyncRow> supplements);

    void replaceAppUsers(List<AuthUserSyncRow> users);

    void replaceAppRoles(List<AppRole> roles);

    void replaceAppFunctions(List<AppFunction> functions);

    void replaceRoleFunctions(List<RoleFunctionSyncRow> roleFunctions);

    List<AuthUserSyncRow> fetchAppUsers();

    List<AppRole> fetchAppRoles();

    List<AppFunction> fetchAppFunctions();

    List<RoleFunctionSyncRow> fetchRoleFunctions();
}
