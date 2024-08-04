package qupath.lib.extension.cedar;

import javafx.scene.input.KeyCombination;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.extensions.QuPathExtension;

import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MenuTools;

public class CedarExtension implements QuPathExtension {

    @Override
    public void installExtension(QuPathGUI qupath) {
        var chooserFolderAction = ActionTools.createAction(new ChooseFolderAction(qupath), "Choose folder...");
        chooserFolderAction.setAccelerator(KeyCombination.keyCombination("ctrl+i"));
        // This extension is based on Monai Label. Therefore, the following should be reliable.
        // Otherwise, a new menu will be created.
        var menu = qupath.getMenu("MONAI Label>CEDAR", true);
        MenuTools.addMenuItems(menu, chooserFolderAction);
    }

    @Override
    public String getName() {
        return "CEDAR Extension";
    }

    @Override
    public String getDescription() {
        return "An extension of Monai Label extension";
    }

}
