package tw.org.il.dongsheng.templeapp.model;

public class HourItem {
    private String name;
    private String timeRange;

    public HourItem(String name, String timeRange) {
        this.name = name;
        this.timeRange = timeRange;
    }

    public String getName() { return name; }
    public String getTimeRange() { return timeRange; }
}
