package qupath.lib.extension.cedar;

import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.classes.PathClass;

import java.io.*;
import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is used to handle the list of path classes used in the prostate cancer project.
 */
public class CedarPathClassHandler {
    private static final Logger logger = LoggerFactory.getLogger(CedarPathClassHandler.class);
    private static CedarPathClassHandler handler;
    private Map<Integer, PathClass> id2cls = new HashMap<>();
    // For reverse search
    private Map<String, Integer> clsName2id = new HashMap<>();

    private CedarPathClassHandler() {
        this.loadClassIdConfig();
    }

    public void loadClassIdConfig() {
        // Check if the file is setting
        String ftuFile = Settings.ftuIDClassFileName() == null ? null : Settings.ftuIDClassFileName().getValue();
        try {
            InputStream input = null;
            if (ftuFile != null && ftuFile.trim().length() > 0) {
                File file = new File(Settings.localStoragePathProperty().getValue(), ftuFile.trim());
                input = new FileInputStream(file);
            }
            else {
                input = getClass().getClassLoader().getResourceAsStream("prostate_cancer_path_classes.txt");
            }
            loadClassIdFile(input);
        }
        catch (IOException ex) {
            logger.error("Error in CedarPathClassHandler(): " + ex.getMessage(), ex);
        }
    }

    private void loadClassIdFile(InputStream input) throws IOException {
        if (input == null) {
            logger.error("Cannot find file: prostate_cancer_path_classes.txt");
            return;
        }
        id2cls.clear();
        clsName2id.clear();
        BufferedReader br = new BufferedReader(new InputStreamReader(input));
        // Bypasss the first line, which is head
        String line = br.readLine();
        while ((line = br.readLine()) != null) {
            if (line.trim().length() == 0)
                continue;
            String[] tokens = line.split("\t");
            String[] colorText = tokens[2].trim().split(",");
            Integer color = ColorTools.packRGB(Integer.parseInt(colorText[0]),
                    Integer.parseInt(colorText[1]),
                    Integer.parseInt(colorText[2]));
            PathClass cls = PathClass.fromString(tokens[1].trim(), color);
            id2cls.put(Integer.parseInt(tokens[0].trim()), cls);
        }
        br.close();
        // reverse the map
        id2cls.forEach((id, cls) -> clsName2id.put(cls.getName(), id));
    }

    public static CedarPathClassHandler getHandler() {
        if (handler == null)
            handler = new CedarPathClassHandler();
        return handler;
    }

    public List<String> getClassNames() {
        // Make the order as the original one
        // Since we are using HashMap and cannot guarantee the order of the keys
        // therefore, we order the names explicitly using their ids.
        List<String> classNames = id2cls.values().stream().map(cls -> cls.getName())
                .sorted((c1, c2) -> clsName2id.get(c1).compareTo(clsName2id.get(c2)))
                .collect(Collectors.toUnmodifiableList());
        return classNames;
    }

    public Integer getClassId(String className) {
        Integer id = clsName2id.get(className);
        if (id != null)
            return id;
        return -1; // As a flag to indicate nothing is there.
    }


    public PathClass getPathClass(Integer id) {
        PathClass cls = id2cls.get(id);
        if (cls != null)
            return cls;
        return PathClass.fromString(id.toString()); // Use this as the default.
    }

    public void setPathClasses(QuPathGUI qupath) {
        ObservableList<PathClass> availablePathClasses = qupath.getAvailablePathClasses();

        // The following code is based on promptToPopulateFromImage() in PathClassPane
        List<PathClass> newClasses = new ArrayList<>(); // We'd like to use a modifiable list
        id2cls.keySet().stream().sorted().map(id ->id2cls.get(id)).forEach(c -> newClasses.add(c));

//        newClasses.add(PathClass.StandardPathClasses.IGNORE);

        List<PathClass> currentClasses = new ArrayList<>(availablePathClasses);
        currentClasses.remove(null);
        if (currentClasses.equals(newClasses)) {
            return; // Do nothing to avoid any change event fired
        }

        // Put null as the first
        // Need to add null to avoid Invalid PathClass list modification
        newClasses.add(0, PathClass.NULL_CLASS);
        availablePathClasses.setAll(newClasses);
    }

}
