package qupath.lib.extension.cedar;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.File;

public class Settings {
    private static StringProperty localStoragePath = PathPrefs.createPersistentPreference("localStoragePath",
            System.getProperty("user.home") + File.separator + "QuPath" + File.separator + "cedar_extension" + File.separator);
    private static StringProperty cancerFTUIDClassFile = PathPrefs.createPersistentPreference("FTUIdClassFile", null);

    public static StringProperty localStoragePathProperty() {
        File file = new File(localStoragePath.get());
        if (!file.exists())
            file.mkdirs();
        return localStoragePath;
    }

    public static StringProperty ftuIDClassFileName() {
        return cancerFTUIDClassFile;
    }

    void addProperties(QuPathGUI qupath) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> addProperties(qupath));
            return;
        }

        qupath.getPreferencePane().addPropertyPreference(Settings.localStoragePathProperty(), String.class,
                "Working directory", "CEDAR", "Local Storage Path for cedar files (e.g. tracking)");
        qupath.getPreferencePane().addPropertyPreference(Settings.ftuIDClassFileName(), String.class,
                "FTU ID Class File (in working directory)", "CEDAR", "File for mapping between id and class");

    }

}
