package tw.org.il.dongsheng.templeapp.model;

public class AddressRoad {
    private Integer id;
    private String city;
    private String district;
    private String road;
    private String prefix;

    public AddressRoad() {
    }

    public AddressRoad(Integer id, String city, String district, String road, String prefix) {
        this.id = id;
        this.city = city;
        this.district = district;
        this.road = road;
        this.prefix = prefix;
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

    public String getRoad() {
        return road;
    }

    public void setRoad(String road) {
        this.road = road;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
