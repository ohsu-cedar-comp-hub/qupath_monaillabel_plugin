package qupath.lib.extension.cedar.ActionLogging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CedarExtensionAction {

    private String action; //TODO: controlled vocabulary
    private String propertyName;
    private String newPropertyValue;
    private String oldPropertyValue = "";
    private String startTime;
    private String endTime;
    private Integer id;
    private String timeStamp;

    public static String createCSVHeader() {
        return String.join(",", "id", "Action", "Start Time", "End Time", "Property Name",
                "New Property Value", "Old Property Value", "Duration", "Time Stamp");
    }

    public String toCSVString() {
        return String.join(",",
                getId(),
                getAction(),
                getStartTime(),
                getEndTime(),
                getPropertyName(),
                getNewPropertyValue(),
                getOldPropertyValue(),
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
        this.startTime = Long.toString(System.currentTimeMillis());
        this.timeStamp = this.createTimeStamp();

    }

    public String getAction() {
        return action;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getStartTime() {
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

    public String calculateDuration(){
       int startTime = Integer.parseInt(getStartTime());
       int endTime = Integer.parseInt(getEndTime());
       return Integer.toString(endTime - startTime);
    }
}
