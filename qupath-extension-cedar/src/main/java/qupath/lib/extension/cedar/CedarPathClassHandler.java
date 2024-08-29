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
        // Load the class definition from a file
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("prostate_cancer_path_classes.txt")) {
            if (input == null) {
                logger.error("Cannot find file: prostate_cancer_path_classes.txt");
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(input));
            // Bypasss the first line, which is head
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().length() == 0)
                    continue;
                String[] tokens = line.split("\t");
                String[] colorText = tokens[2].split(",");
                Integer color = ColorTools.packRGB(Integer.parseInt(colorText[0]),
                        Integer.parseInt(colorText[1]),
                        Integer.parseInt(colorText[2]));
                PathClass cls = PathClass.fromString(tokens[1], color);
                id2cls.put(Integer.parseInt(tokens[0]), cls);
            }
            // reverse the map
            id2cls.forEach((id, cls) -> clsName2id.put(cls.getName(), id));
        }
        catch (IOException ex) {
            logger.error("Error in CedarPathClassHandler(): " + ex.getMessage(), ex);
        }
    }

    public static CedarPathClassHandler getHandler() {
        if (handler == null)
            handler = new CedarPathClassHandler();
        return handler;
    }

    public List<String> getClassNames() {
        List<String> classNames = id2cls.values().stream().map(cls -> cls.getName()).sorted().collect(Collectors.toUnmodifiableList());
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
        List<PathClass> newClasses = new ArrayList<>(id2cls.values());
        Collections.sort(newClasses);

        newClasses.add(PathClass.StandardPathClasses.IGNORE);

        List<PathClass> currentClasses = new ArrayList<>(availablePathClasses);
        currentClasses.remove(null);
        if (currentClasses.equals(newClasses)) {
            return; // Do nothing to avoid any change event fired
        }

        // Put null as the first
        newClasses.add(0, PathClass.NULL_CLASS);
        availablePathClasses.setAll(newClasses);
    }

}
