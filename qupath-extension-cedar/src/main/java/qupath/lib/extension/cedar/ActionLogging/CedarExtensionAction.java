package qupath.lib.extension.cedar.ActionLogging;

public class CedarExtensionAction {

    private String action; //TODO: controlled vocabulary
    // Could be controlled using reflection and getting all of the Method Names??
    private String propertyName;
    private String propertyValue;
    private String startTime;
    private String endTime;

    public CedarExtensionAction(String action){
        this.action = action;
        this.startTime = Long.toString(System.currentTimeMillis());
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

    public String getPropertyValue() {
        return propertyValue;
    }

    public void setPropertyValue(String propertyValue) {
        this.propertyValue = propertyValue;
    }
}
