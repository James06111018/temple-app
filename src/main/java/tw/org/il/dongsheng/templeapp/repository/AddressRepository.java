package tw.org.il.dongsheng.templeapp.repository;

import tw.org.il.dongsheng.templeapp.model.AddressPreset;
import tw.org.il.dongsheng.templeapp.model.AddressRoad;
import tw.org.il.dongsheng.templeapp.model.AddressVillage;

import java.sql.SQLException;
import java.util.List;

public interface AddressRepository {
    void createTable() throws SQLException;

    int replaceRoads(List<AddressRoad> roads) throws SQLException;

    AddressPreset savePreset(AddressPreset preset) throws SQLException;

    List<AddressRoad> findRoads(String city, String district) throws SQLException;

    List<AddressRoad> findRoadsByPrefix(String city, String district, String prefix) throws SQLException;

    List<AddressVillage> findVillages(String city, String district) throws SQLException;

    List<AddressPreset> findPresets(String city, String district) throws SQLException;
}
