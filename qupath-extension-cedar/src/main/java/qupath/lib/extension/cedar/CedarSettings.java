package qupath.lib.extension.cedar;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.File;

public class CedarSettings {

    private StringProperty localStoragePath = PathPrefs.createPersistentPreference("localStoragePath",
            System.getProperty("user.home") + File.separator + "QuPath" + File.separator + "cedar_extension" + File.separator);
    private StringProperty cancerFTUIDClassFile = PathPrefs.createPersistentPreference("FTUIdClassFile", null);
    private StringProperty modelFile = PathPrefs.createPersistentPreference("ModelFile", null);
    private BooleanProperty useModelForSegmentationOnly = PathPrefs.createPersistentPreference(
            "useModelForSegmentationOnly", Boolean.FALSE);
    private boolean preferenceAdded = false;
    private static CedarSettings settings = null;

    private CedarSettings() {
    }

    public static CedarSettings getSettings() {
        if (settings == null)
            settings = new CedarSettings();
        return settings;
    }


    public StringProperty localStoragePathProperty() {
        File file = new File(localStoragePath.get());
        if (!file.exists())
            file.mkdirs();
        return localStoragePath;
    }

    public StringProperty ftuIDClassFileName() {
        return cancerFTUIDClassFile;
    }

    public StringProperty modelFileName() {
        return modelFile;
    }

    public BooleanProperty useModelForSegmentationOnly() {
        return useModelForSegmentationOnly;
    }

    public void addProperties(QuPathGUI qupath) {
        if (this.preferenceAdded)
            return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> addProperties(qupath));
            return;
        }

        qupath.getPreferencePane().addPropertyPreference(localStoragePathProperty(), String.class,
                "Working directory", "CEDAR", "Local Storage Path for cedar files (e.g. tracking)");
        qupath.getPreferencePane().addPropertyPreference(ftuIDClassFileName(), String.class,
                "FTU ID Class File (in working directory)", "CEDAR", "File for mapping between id and class");
        qupath.getPreferencePane().addPropertyPreference(modelFileName(), String.class,
                "Model File (in working directory)", "CEDAR", "File for trained model weights");
        qupath.getPreferencePane().addPropertyPreference(useModelForSegmentationOnly(), Boolean.class,
                "Use Inference Model for Segmentation Only", "CEDAR", "Use the AI model for segmentation only without classification");
        this.preferenceAdded = true;
    }

}
