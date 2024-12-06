package qupath.lib.extension.cedar;

import javafx.beans.property.StringProperty;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.File;

public class Settings {
    private static StringProperty localStoragePath = PathPrefs.createPersistentPreference("localStoragePath",
            System.getProperty("user.home") + File.separator + "QuPath" + File.separator + "cedar_extension" + File.separator);

    public static StringProperty localStoragePathProperty() {
        File file = new File(localStoragePath.get());
        if (!file.exists())
            file.mkdirs();
        return localStoragePath;
    }

}
