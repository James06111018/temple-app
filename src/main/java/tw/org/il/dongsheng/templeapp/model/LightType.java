package tw.org.il.dongsheng.templeapp.model;

public class LightType {
    private Integer id;
    private String code;
    private String name;
    private boolean enabled;
    private Integer sortOrder;

    public LightType() {
    }

    public LightType(Integer id, String code, String name, boolean enabled, Integer sortOrder) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
