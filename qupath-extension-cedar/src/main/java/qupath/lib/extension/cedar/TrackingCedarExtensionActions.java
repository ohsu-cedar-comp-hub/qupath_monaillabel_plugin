package qupath.lib.extension.cedar;

import qupath.lib.extension.cedar.ActionLogging.CedarExtensionAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;

public class TrackingCedarExtensionActions {
    // TODO: make the filePathName configurable
    String filePathName = "/Users/beaversd/IdeaProjects/qupath_monaillabel_plugin/qupath-extension-cedar/src/main/tracking/CedarExtensionLogging" + LocalDateTime.now() + ".csv";
    int id = 0; // tracking the id to further annotate
    HashMap<Integer, CedarExtensionAction> trackedAnnotations = new HashMap<>();

    public void addCedarExtensionAction(CedarExtensionAction cedarExtensionAction) {
        trackedAnnotations.put(id, cedarExtensionAction);
        try {
            writeToFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        id++; // Simple incrementation
    }


    private void writeToFile() throws IOException {
        for(CedarExtensionAction cedarExtensionAction : trackedAnnotations.values()){
            String data = String.join(",",
                    cedarExtensionAction.getAction(),
                    cedarExtensionAction.getStartTime(),
                    cedarExtensionAction.getEndTime(),
                    cedarExtensionAction.getPropertyName(),
                    cedarExtensionAction.getPropertyValue());
            // TODO: improve with a queue
            File csvOutputFile = new File(filePathName);
            try (FileWriter fileWriter = new FileWriter(csvOutputFile, true)) {

                fileWriter.write(data);
            }
        }
        // clear the cache once the annotations have been tracked
        trackedAnnotations.clear();
    }
}
