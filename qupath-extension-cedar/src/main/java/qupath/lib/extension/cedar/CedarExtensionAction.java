package qupath.lib.extension.cedar;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This class is created to model the user actions so that we can track them to measure the efficiency of human annoation
 * of the segemnetation results.
 */
public class CedarExtensionAction {

    private String action; //TODO: controlled vocabulary
    private String propertyName;
    private String newPropertyValue;
    private String oldPropertyValue;
    private long startTime;
    private long endTime;
    private Integer id;
    private String timeStamp;

    public static String createCSVHeader() {
        return String.join("\t", "id",
                "Action",
                "Start Time (ms)",
                "End Time (ms)",
                "Duration (ms)",
                "Property Name",
                "New Property Value",
                "Old Property Value",
                "Time Stamp");
    }

    public String toCSVString() {
        return String.join("\t",
                getId(),
                getAction(),
                getStartTime() + "",
                getEndTime() + "",
                getDuration() + "",
                getPropertyName(),
                getNewPropertyValue() == null ? "" : getNewPropertyValue(),
                getOldPropertyValue() == null ? "" : getOldPropertyValue(),
                timeStamp);
    }

    public String createTimeStamp() {
        LocalDateTime now = LocalDateTime.now(); // Get current date and time
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    public CedarExtensionAction(int id, String action) {
        this.action = action;
        this.id = id;
        this.startTime = System.currentTimeMillis();
        this.timeStamp = this.createTimeStamp();
    }

    public String getAction() {
        return action;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getNewPropertyValue() {
        return newPropertyValue;
    }

    public void setNewPropertyValue(String newPropertyValue) {
        this.newPropertyValue = newPropertyValue;
    }

    public String getOldPropertyValue() {
        return oldPropertyValue;
    }

    public void setOldPropertyValue(String oldPropertyValue) {
        this.oldPropertyValue = oldPropertyValue;
    }

    public String getId() {
        return id.toString();
    }

    public long getDuration() {
        return endTime - startTime;
    }
}
