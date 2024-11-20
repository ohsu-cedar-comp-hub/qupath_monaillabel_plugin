package qupath.lib.extension.cedar.ActionLogging;

public class CedarExtensionAction {

    private String action;
    private String startTime;
    private String endTime;

    public CedarExtensionAction(String action){
        this.action = action;
        this.startTime = Long.toString(System.currentTimeMillis());
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
}
