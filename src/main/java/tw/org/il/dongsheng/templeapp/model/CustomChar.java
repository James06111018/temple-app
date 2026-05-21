package tw.org.il.dongsheng.templeapp.model;

public class CustomChar {
    private Integer id;
    private String code;
    private String charValue;
    private String note;
    private boolean enabled;

    public CustomChar() {
    }

    public CustomChar(Integer id, String code, String charValue, String note, boolean enabled) {
        this.id = id;
        this.code = code;
        this.charValue = charValue;
        this.note = note;
        this.enabled = enabled;
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

    public String getCharValue() {
        return charValue;
    }

    public void setCharValue(String charValue) {
        this.charValue = charValue;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
