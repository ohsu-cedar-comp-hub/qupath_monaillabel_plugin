package qupath.lib.extension.cedar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActionTrackingManager {
    private static Logger logger = LoggerFactory.getLogger(ActionTrackingManager.class);
    private static final String TRACKING_FILE_NAME = "action_tracking.tsv";
    private static final int MAX_ACTIONS_FOR_SAVING = 500;
    // Need to have a thread safe collection
    private List<CedarExtensionAction> trackedActions = Collections.synchronizedList(new ArrayList<>());
    private static ActionTrackingManager manager = null;
    private int id = 0; // Simple id for annotations

    // private constructor
    private ActionTrackingManager() {
        this.id = findMaxId();
    }

    public static ActionTrackingManager getManager() {
        if (manager == null)
            manager = new ActionTrackingManager();
        return manager;
    }

    public CedarExtensionAction createAction(String actionName) {
        return new CedarExtensionAction(id++, actionName);
    }

    public synchronized void trackAction(CedarExtensionAction cedarExtensionAction) {
        trackedActions.add(cedarExtensionAction);
        if (trackedActions.size() >= MAX_ACTIONS_FOR_SAVING) {
            // If multiple thread is calling this, there may be more than one thread created.
            // However, since we are using a thread-safe list and the method is synchronized,
            // the calling should be managed to avoid multiple writing around the same time.
            Thread t = new Thread(this::writeToFile);
            t.start();
        }
    }

    public synchronized void writeToFile() {
        if (this.trackedActions.size() == 0)
            return;
        try {
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
            for (CedarExtensionAction cedarExtensionAction : trackedActions) {
                printWriter.println(cedarExtensionAction.toCSVString());
            }
            fileWriter.close();
            printWriter.close();
            // clear the cache once the annotations have been tracked
            trackedActions.clear();
        }
        catch(IOException e) {
            logger.error("Error in writeToFile(): " + e.getMessage(), e);
        }
    }

    /**
     * Get the maximum action id from the saved file.
     */
    private int findMaxId() {
        File csvOutputFile = new File(Settings.localStoragePathProperty().getValue(), TRACKING_FILE_NAME);
        if (!csvOutputFile.exists())
            return 0;
        try {
            FileReader fr = new FileReader(csvOutputFile);
            BufferedReader br = new BufferedReader(fr);
            String line = br.readLine();
            int maxId = 0;
            while ((line = br.readLine()) != null) {
                int tmpId = Integer.parseInt(line.split("\t")[0]);
                if (tmpId > maxId)
                    maxId = tmpId;
            }
            return maxId;
        }
        catch(IOException e) {
            logger.error("Error in findMaxIs(): " + e.getMessage(), e);
            return 0;
        }
    }
}
