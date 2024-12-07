package qupath.lib.extension.cedar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.extension.cedar.ActionLogging.CedarExtensionAction;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackingCedarExtensionActions {
    private static Logger logger = LoggerFactory.getLogger(TrackingCedarExtensionActions.class);
    private static final String TRACKING_FILE_NAME = "tracking.csv";
    private static final int MAX_ACTIONS_FOR_SAVING = 500;
    private List<CedarExtensionAction> trackedAnnotations = new ArrayList<>();
    private static TrackingCedarExtensionActions trackingCedarExtensionActions = null;
    private int id = 0; // Simple id for annotations

    // private constructor
    private TrackingCedarExtensionActions() {
        findMaxId();
    }

    public static TrackingCedarExtensionActions getTrackingCedarExtensionActions() {
        if (trackingCedarExtensionActions == null)
            trackingCedarExtensionActions = new TrackingCedarExtensionActions();
        return trackingCedarExtensionActions;
    }

    public CedarExtensionAction createAction(String actionName) {
        return new CedarExtensionAction(id++, actionName);
    }

    public void addCedarExtensionAction(String actionName) {
        this.addCedarExtensionAction(new CedarExtensionAction(id++, actionName));
    }

    public void addCedarExtensionAction(CedarExtensionAction cedarExtensionAction) {
        trackedAnnotations.add(cedarExtensionAction);
        if (trackedAnnotations.size() >= MAX_ACTIONS_FOR_SAVING) {
            try {
                writeToFile();
            } catch (IOException e) {
                logger.error("addCedarExtensionAction: " + e.getMessage(), e);
            }
        }
    }

    public synchronized void writeToFile() throws IOException {
        if (this.trackedAnnotations.size() == 0)
            return;
        File csvOutputFile = new File(Settings.localStoragePathProperty().getValue(), TRACKING_FILE_NAME);
        boolean needHeader = false;
        if (!csvOutputFile.exists()) {
            csvOutputFile.createNewFile();
            needHeader = true;
        }
        FileWriter fileWriter = new FileWriter(csvOutputFile, true);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        if (needHeader) {
            printWriter.println(CedarExtensionAction.createCSVHeader());
        }
        for (CedarExtensionAction cedarExtensionAction : trackedAnnotations) {
            printWriter.println(cedarExtensionAction.toCSVString());
        }
        fileWriter.close();
        printWriter.close();
        // clear the cache once the annotations have been tracked
        trackedAnnotations.clear();
    }

    private void findMaxId() {
        File csvOutputFile = new File(Settings.localStoragePathProperty().getValue(), TRACKING_FILE_NAME);
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(csvOutputFile))) {
            br.readLine(); // Skip the first line, the headers
            while ((line = br.readLine()) != null) {
                // The id is the first value in the row
                int tempId = Integer.parseInt(line.split(",")[0]);
                if(tempId > id)
                    id = tempId;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
