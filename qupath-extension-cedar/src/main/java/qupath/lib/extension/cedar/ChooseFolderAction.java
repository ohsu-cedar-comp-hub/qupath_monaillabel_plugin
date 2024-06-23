package qupath.lib.extension.cedar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final Logger logger = LoggerFactory.getLogger(ChooseFolderAction.class);

    private final QuPathGUI qupath;

    public ChooseFolderAction(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        // Test code to add a new tab
        TabPane tabPane = this.qupath.getAnalysisTabPane();

        File imageAndAnnotFolder = chooseFolder();
        if (imageAndAnnotFolder == null)
            return; // Nothing needs to be done. The user cancel the action
        logger.info("Selected folder: " + imageAndAnnotFolder);
        // Make sure the following two folders exist in this imageFolder
        File imageFolder = new File(imageAndAnnotFolder, "images");
        if (!imageFolder.exists()) {
            Dialogs.showErrorMessage("Error in Folder Choosing",
                    "Cannot find the images folder in \n" + imageAndAnnotFolder);
            return;
        }
        File annotationFolder = new File(imageAndAnnotFolder, "annotations");
        if (!annotationFolder.exists()) {
            Dialogs.showErrorMessage("Error in Folder Choosing",
                    "Cannot find the annotations folder in \n" + imageAndAnnotFolder);
            return;
        }
        //TODO: Test code below
        File firstImage = imageFolder.listFiles()[0];
        loadImage(firstImage);
        loadAnnotation(firstImage, annotationFolder);
    }

    /**
     * Load an image file.
     * @param imageFile
     * @return
     */
    private boolean loadImage(File imageFile) {
        try {
            logger.info("Loading image: " + imageFile.getAbsolutePath());
            return this.qupath.openImage(this.qupath.getViewer(), imageFile.getAbsolutePath());
        }
        catch (IOException e) {
            Dialogs.showErrorMessage("Error in Opening Image",
                    "Cannot open image: " + imageFile.getName());
            logger.error("Cannot open image: " + imageFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Load the annotations for the passed image file. It is assumed the annotations should be in a
     * json file having the same file name, but in the annotations folder.
     * @param imageFile
     * @return
     */
    private  boolean loadAnnotation(File imageFile, File annotationFolder) {
        String imageFileName = imageFile.getName();
        int index = imageFileName.lastIndexOf(".");
        String annotationFileName = imageFileName.substring(0, index) + ".json";
        File annotationFile = new File(annotationFolder, annotationFileName);
        if (!annotationFile.exists()) {
            Dialogs.showErrorMessage("No Annotation File",
                    "Cannot find an annotation file for the image file, " + imageFileName + ".");
            return false;
        }
        try {
            logger.info("Loading annotation: " + annotationFile.getAbsolutePath());

            // The following code is based on RunInference.java in Monai Label QuPath extension
            ImageData<BufferedImage> imageData = this.qupath.getImageData();

            // Create ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();

            // Read JSON file and parse it to JsonNode
            JsonNode rootNode = objectMapper.readTree(annotationFile);

            // Get image_name
            String imageName = rootNode.path("image_name").asText();
            System.out.println("Image Name: " + imageName);

            // Get features
            JsonNode featuresNode = rootNode.path("features");
            JsonNode classNode = featuresNode.path("class");
            JsonNode annoStyleNode = featuresNode.path("anno_style");
            JsonNode metadataNode = featuresNode.path("metadata");

            System.out.println("Classes: " + classNode);
            System.out.println("Annotation Styles: " + annoStyleNode);
            System.out.println("Metadata: " + metadataNode);

            // Get annotations
            JsonNode annotationsNode = rootNode.path("annotation");
            for (int i = 0; i < annotationsNode.size(); i++) {
                JsonNode annotation = annotationsNode.get(i);
                System.out.println("Annotation " + (i + 1) + ":");
                List<Point2> pointsList = new ArrayList<>();
                for (int j = 0; j < annotation.size(); j++) {
                    JsonNode point = annotation.get(j);
                    double x = point.get(0).asDouble();
                    double y = point.get(1).asDouble();
                    pointsList.add(new Point2(x, y));
                }
                ImagePlane plane = ImagePlane.getPlane(0, 0);
                ROI polyROI = ROIs.createPolygonROI(pointsList, plane);
                PathObject annotationObject = PathObjects.createAnnotationObject(polyROI);

                // This is for debugging purpose
                if (annotationObject instanceof PathAnnotationObject) {
                    String annotationStyle = annoStyleNode.get(i).toString();
                    String metadata = metadataNode.get(i).toString();
                    String desc = "AnnotationStyle: " + annotationStyle + "\n" +
                            "Metadata: " + metadata;
                    ((PathAnnotationObject)annotationObject).setDescription(desc);
                }

                String annotationClass = classNode.get(i).toString();
                PathClass pclass = PathClass.fromString(annotationClass, null);
                annotationObject.setPathClass(pclass);

                imageData.getHierarchy().addObject(annotationObject, false);
            }
            QP.fireHierarchyUpdate(imageData.getHierarchy());
            return true;
        }
        catch (IOException e) {
            Dialogs.showErrorMessage("Error in Opening Annotation",
                    "Cannot open annotation for: " + imageFile.getName());
            logger.error("Cannot open annotation for: " + imageFile.getAbsolutePath(), e);
            return false;
        }
    }


    private File chooseFolder() {
        File imageFolder = FileChoosers.promptForDirectory("Choose a folder with images and annotations", null);
        return imageFolder;
    }

}
