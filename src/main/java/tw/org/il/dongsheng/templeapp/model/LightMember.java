package tw.org.il.dongsheng.templeapp.model;

import java.util.Objects;

public class LightMember {
    private Integer id;
    private String name;
    private String phone;
    private String city;
    private String dist;
    private String zipCode;
    private String address;
    private String birthDate;
    private String lunarBirthDate;
    private Integer age;
    private String zodiac;
    private String zodiacYear;
    private String birthTime;
    private String note;
    private String contactPerson;
    private String idNumber;
    private Integer sortOrder;
    private Integer ding;
    private Integer kou;
    private String isMail;
    private String gender;

    public LightMember() {}
    // 建構子 (Constructor)
    public LightMember(Integer id,
                       String name,
                       String phone,
                       String city,
                       String dist,
                       String address,
                       String zipCode,
                       String birthDate,
                       String lunarBirthDate,
                       Integer age,
                       String zodiac,
                       String zodiacYear,
                       String birthTime,
                       String note,
                       String contactPerson,
                       String idNumber,
                       Integer sortOrder,
                       Integer ding,
                       Integer kou,
                       String isMail,
                       String gender) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.city = city;
        this.dist = dist;
        this.address = address;
        this.zipCode = zipCode;
        this.birthDate = birthDate;
        this.lunarBirthDate = lunarBirthDate;
        this.age = age;
        this.zodiac = zodiac;
        this.zodiacYear = zodiacYear;
        this.birthTime = birthTime;
        this.note = note;
        this.contactPerson = contactPerson;
        this.idNumber = idNumber;
        this.sortOrder = sortOrder;
        this.ding = ding;
        this.kou = kou;
        this.isMail = isMail;
        this.gender = gender;
    }

    // Getter 必須要有，TableView 才能抓到資料
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDist() {
        return dist;
    }

    public void setDist(String dist) {
        this.dist = dist;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getLunarBirthDate() {
        return lunarBirthDate;
    }

    public void setLunarBirthDate(String lunarBirthDate) {
        this.lunarBirthDate = lunarBirthDate;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getZodiac() {
        return zodiac;
    }

    public void setZodiac(String zodiac) {
        this.zodiac = zodiac;
    }

    public String getZodiacYear() {
        return zodiacYear;
    }

    public void setZodiacYear(String zodiacYear) {
        this.zodiacYear = zodiacYear;
    }

    public String getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(String birthTime) {
        this.birthTime = birthTime;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getDing() {
        return ding;
    }

    public void setDing(Integer ding) {
        this.ding = ding;
    }

    public Integer getKou() {
        return kou;
    }

    public void setKou(Integer kou) {
        this.kou = kou;
    }

    public String getIsMail() {
        return isMail;
    }

    public void setIsMail(String isMail) {
        this.isMail = isMail;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LightMember)) {
            return false;
        }
        LightMember member = (LightMember) o;
        return Objects.equals(id, member.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Member{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", phone='" + phone + '\'' +
                ", city='" + city + '\'' +
                ", dist='" + dist + '\'' +
                ", address='" + address + '\'' +
                ", zipCode='" + zipCode + '\'' +
                ", birthDate='" + birthDate + '\'' +
                ", lunarBirthDate='" + lunarBirthDate + '\'' +
                ", age=" + age +
                ", zodiac='" + zodiac + '\'' +
                ", zodiacYear='" + zodiacYear + '\'' +
                ", birthTime='" + birthTime + '\'' +
                ", note='" + note + '\'' +
                ", contactPerson='" + contactPerson + '\'' +
                ", idNumber='" + idNumber + '\'' +
                ", sortOrder=" + sortOrder +
                ", ding=" + ding +
                ", kou=" + kou +
                ", isMail='" + isMail + '\'' +
                ", gender='" + gender + '\'' +
                '}';
    }
}
