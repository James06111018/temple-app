package tw.org.il.dongsheng.templeapp.model;

public class MeritCategory {
    private Long id;
    private String code;
    private String name;
    private boolean deleted;

    public MeritCategory() {
    }

    public MeritCategory(Long id, String code, String name, boolean deleted) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.deleted = deleted;
    }

    public static MeritCategory allOption() {
        return new MeritCategory(null, "", "全部", false);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
