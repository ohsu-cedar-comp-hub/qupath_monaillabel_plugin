package qupath.lib.extension.cedar;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.gui.QuPathGUI;

import java.io.File;

public class ChooseFolderAction implements  Runnable {
    private final QuPathGUI qupath;

    public ChooseFolderAction(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        // Close any opened image
        if(!this.qupath.closeViewer(this.qupath.getViewer()))
            return; // Cancel the close. Then do nothing!

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

        // Set available path classes
        CedarPathClassHandler.getHandler().setPathClasses(this.qupath);
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
