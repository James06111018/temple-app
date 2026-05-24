package tw.org.il.dongsheng.templeapp.model;

public class AddressPreset {
    private Integer id;
    private String zipCode;
    private String city;
    private String district;
    private String village;
    private String road;
    private String address;
    private Integer sortOrder;

    public AddressPreset() {
    }

    public AddressPreset(
            Integer id,
            String zipCode,
            String city,
            String district,
            String village,
            String road,
            String address,
            Integer sortOrder
    ) {
        this.id = id;
        this.zipCode = zipCode;
        this.city = city;
        this.district = district;
        this.village = village;
        this.road = road;
        this.address = address;
        this.sortOrder = sortOrder;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
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

    public String getRoad() {
        return road;
    }

    public void setRoad(String road) {
        this.road = road;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
