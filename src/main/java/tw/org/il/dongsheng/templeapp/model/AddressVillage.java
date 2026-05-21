package tw.org.il.dongsheng.templeapp.model;

public class AddressVillage {
    private Integer id;
    private String city;
    private String district;
    private String village;

    public AddressVillage() {
    }

    public AddressVillage(Integer id, String city, String district, String village) {
        this.id = id;
        this.city = city;
        this.district = district;
        this.village = village;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getVillage() {
        return village;
    }

    public void setVillage(String village) {
        this.village = village;
    }
}
