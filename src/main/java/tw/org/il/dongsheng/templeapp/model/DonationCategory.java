package tw.org.il.dongsheng.templeapp.model;

import javafx.beans.property.*;

public class DonationCategory {

    private final StringProperty code = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final IntegerProperty amount = new SimpleIntegerProperty();
    private final BooleanProperty enabled = new SimpleBooleanProperty();
    private final IntegerProperty sort = new SimpleIntegerProperty();
    private final StringProperty remark = new SimpleStringProperty();

    public DonationCategory(String code, String name, String type,
                            int amount, boolean enabled, int sort, String remark) {
        this.code.set(code);
        this.name.set(name);
        this.type.set(type);
        this.amount.set(amount);
        this.enabled.set(enabled);
        this.sort.set(sort);
        this.remark.set(remark);
    }

    public StringProperty codeProperty() { return code; }
    public StringProperty nameProperty() { return name; }
    public BooleanProperty enabledProperty() { return enabled; }

    public String getCode() { return code.get(); }
    public void setCode(String val) { code.set(val); }

    public String getName() { return name.get(); }
    public void setName(String val) { name.set(val); }

    public String getType() { return type.get(); }
    public void setType(String val) { type.set(val); }

    public int getAmount() { return amount.get(); }
    public void setAmount(int val) { amount.set(val); }

    public boolean isEnabled() { return enabled.get(); }
    public void setEnabled(boolean val) { enabled.set(val); }

    public int getSort() { return sort.get(); }
    public void setSort(int val) { sort.set(val); }

    public String getRemark() { return remark.get(); }
    public void setRemark(String val) { remark.set(val); }

}
