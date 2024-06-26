package qupath.lib.extension.cedar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChooseFolderAction implements  Runnable {
    private final QuPathGUI qupath;

    public ChooseFolderAction(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        // Test code to add a new tab
        TabPane tabPane = this.qupath.getAnalysisTabPane();
        CedarExtensionView view = CedarExtensionView.getView();
        view.setQupath(this.qupath);
        Tab tab = findTabView(tabPane, view);
        if (tab == null) {
            tab = view.getTabView();
            tabPane.getTabs().add(tab);
        }
        tabPane.getSelectionModel().select(tab);

        File imageAndAnnotFolder = chooseFolder();
        if (imageAndAnnotFolder == null)
            return; // Nothing needs to be done. The user cancel the action
        view.setFolder(imageAndAnnotFolder);
    }

    private Tab findTabView(TabPane tabPane, CedarExtensionView view) {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().equals(view.getTabName())) {
                return tab;
            }
        }
        return null;
    }

    private File chooseFolder() {
        File imageFolder = FileChoosers.promptForDirectory("Choose a folder with images and annotations", null);
        return imageFolder;
    }

}
