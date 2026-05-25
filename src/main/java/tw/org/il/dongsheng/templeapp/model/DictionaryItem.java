package tw.org.il.dongsheng.templeapp.model;

public class DictionaryItem {
    private Integer id;
    private String categoryCode;
    private String code;
    private String name;
    private String description;
    private Integer amount;
    private boolean enabled;
    private Integer sortOrder;
    private Integer parentItemId;

    public DictionaryItem() {
    }

    public DictionaryItem(Integer id, String categoryCode, String code, String name, String description, Integer amount, boolean enabled, Integer sortOrder) {
        this(id, categoryCode, code, name, description, amount, enabled, sortOrder, null);
    }

    public DictionaryItem(Integer id, String categoryCode, String code, String name, String description, Integer amount, boolean enabled, Integer sortOrder, Integer parentItemId) {
        this.id = id;
        this.categoryCode = categoryCode;
        this.code = code;
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.parentItemId = parentItemId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
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

    public Integer getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(Integer parentItemId) {
        this.parentItemId = parentItemId;
    }

    @Override
    public String toString() {
        return code == null || code.isBlank() ? name : code + " - " + name;
    }
}
