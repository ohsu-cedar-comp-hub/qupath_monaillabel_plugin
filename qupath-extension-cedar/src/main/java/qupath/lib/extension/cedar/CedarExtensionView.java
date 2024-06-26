package qupath.lib.extension.cedar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import org.controlsfx.tools.Borders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.geom.Point;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The view of the cedar extension. A new tab will be added to the analysis tab panel of the main QuPathGUI.
 */
public class CedarExtensionView {
    private final static Logger logger = LoggerFactory.getLogger(CedarExtensionView.class);

    private final String TAB_NAME = "CEDAR";
    private Label folderLabel;
    private Pane contentPane;
    // The current folder
    private File currentFolder;
    // List of image files
    private ListView<File> imageList;
    // Used to display annoations
    private TableView<CedarAnnotation> annotationTable;
    private Button updateAnnoationBtn;
    private QuPathGUI qupath;

    private static CedarExtensionView view;

    // Make sure there is only one view created
    private CedarExtensionView() {
        init();
    }

    public static final CedarExtensionView getView() {
        if (view == null)
            view = new CedarExtensionView();
        return view;
    }

    public void setQupath(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private void init() {
        this.contentPane = new BorderPane();

        VBox vbox = new VBox();
        vbox.setPadding(new Insets(1));
        folderLabel = new Label("Selected Folder");
        vbox.getChildren().add(folderLabel);
        // List images
        Label label = new Label("Images in folder");
        vbox.getChildren().add(label);

        imageList = new ListView<>();
        imageList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null)
                    setText(null);
                else
                    setText(file.getName());
            }
        });
        imageList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        imageList.getSelectionModel().selectedItemProperty().addListener((observable) -> {
            handleImageSelection();
        });

        VBox.setVgrow(imageList, Priority.ALWAYS);
        vbox.getChildren().add(imageList);

        annotationTable = new TableView<>();
        annotationTable.setEditable(true); // Enable editing
        annotationTable.getSelectionModel().selectedItemProperty().addListener((observable -> {
            handleAnnotationTableSelection();
        }));

        TableColumn<CedarAnnotation, String> classCol = createTableColumn("class", "className");
        classCol.setOnEditCommit(event -> {
            event.getRowValue().setClassName(event.getNewValue());
            updateAnnoationBtn.setDisable(false);
        });

        TableColumn<CedarAnnotation, String> annotationStyleCol = createTableColumn("annotation style",
                "annotationStyle");
        annotationStyleCol.setOnEditCommit(event -> {
            event.getRowValue().setAnnotationStyle(event.getNewValue());
            updateAnnoationBtn.setDisable(false);
        });

        TableColumn<CedarAnnotation, String> metaDataSol = createTableColumn("metadata",
                "metaData");
        metaDataSol.setOnEditCommit(event -> {
            event.getRowValue().setAnnotationStyle(event.getNewValue());
            updateAnnoationBtn.setDisable(false);
        });

        annotationTable.getColumns().addAll(classCol, annotationStyleCol, metaDataSol);
        annotationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        vbox.getChildren().add(annotationTable);

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        updateAnnoationBtn = new Button("Update Annotation");
        updateAnnoationBtn.setOnAction(e -> saveAnnoations());
        updateAnnoationBtn.setDisable(true);
        buttonBox.getChildren().add(updateAnnoationBtn);
        vbox.getChildren().add(buttonBox);

        ((BorderPane)contentPane).setCenter(vbox);
    }

    private TableColumn<CedarAnnotation, String> createTableColumn(String colName, String propName) {
        TableColumn<CedarAnnotation, String> classCol = new TableColumn<>(colName);
        classCol.setCellValueFactory(new PropertyValueFactory<>(propName));
        // Have to call this. Otherwise, the table cannot be edited.
        classCol.setCellFactory(TextFieldTableCell.forTableColumn());
        classCol.setEditable(true);
        return classCol;
    }

    private void handleImageSelection() {
        File imageFile = imageList.getSelectionModel().getSelectedItem();
        if (imageFile == null)
            return; // Do nothing
        loadImage(imageFile);
        loadAnnotation(imageFile);
    }

    private void handleAnnotationTableSelection() {
        CedarAnnotation annotation = annotationTable.getSelectionModel().getSelectedItem();
        if (annotation == null)
            return;
        PathObject pathObject = annotation.getPathObject();
        if (pathObject == null)
            return;
        QP.selectObjects(pathObject);
    }

    private void saveAnnoations() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        // Get the image name
        File imageFile = imageList.getSelectionModel().getSelectedItem();
        json.put("image_name", imageFile.getName());
        ArrayNode classArray = mapper.createArrayNode();
        ArrayNode annotationArray = mapper.createArrayNode();
        ArrayNode metaArray = mapper.createArrayNode();
        ArrayNode pathArray = mapper.createArrayNode();
        for (CedarAnnotation annotation : annotationTable.getItems()) {
            // Force it to integer
            // TODO: This may need to discuss
            classArray.add(Integer.parseInt(annotation.getClassName()));
            annotationArray.add(annotation.getAnnotationStyle());
            metaArray.add(annotation.getMetaData());
            // Create a path
            ArrayNode pointsArray = mapper.createArrayNode();
            PathObject pathObject = annotation.getPathObject();
            ROI roi = pathObject.getROI();
            List<Point2> points = roi.getAllPoints();
            for (Point2 point : points) {
                ArrayNode pointArray = mapper.createArrayNode();
                pointArray.add(point.getX());
                pointArray.add(point.getY());
                pointsArray.add(pointArray);
            }
            pathArray.add(pointsArray);
        }
        ObjectNode featureNode = mapper.createObjectNode();
        featureNode.set("class", classArray);
        featureNode.set("anno_style", annotationArray);
        featureNode.set("metadata", metaArray);
        json.set("features", featureNode);
        json.set("annotation", pathArray);

        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

        File outputFile = new File(currentFolder, "output.json");
        try {
            writer.writeValue(outputFile, json);
        }
        catch(IOException e) {
            logger.error("Cannot save the annotation: " + e.getMessage(), e);
        }
    }

    private File getImagesFolder(File folder) {
        if (folder == null)
            folder = this.currentFolder;
        return new File(folder, "images");
    }

    private File getAnnotationsFolder(File folder) {
        if (folder == null)
            folder = this.currentFolder;
        return new File(folder, "annotations");
    }

    public void setFolder(File folder) {
        // Make sure the following two folders exist in this imageFolder
        File imageFolder = getImagesFolder(folder);
        if (!imageFolder.exists()) {
            Dialogs.showErrorMessage("Error in Folder Choosing",
                    "Cannot find the images folder in \n" + folder);
            return;
        }
        File annotationFolder = getAnnotationsFolder(folder);
        if (!annotationFolder.exists()) {
            Dialogs.showErrorMessage("Error in Folder Choosing",
                    "Cannot find the annotations folder in \n" + folder);
            return;
        }
        this.currentFolder = folder;
        this.folderLabel.setText(folder.getAbsolutePath());
        listImages();
    }

    private void listImages() {
        if (this.currentFolder == null)
            return;
        imageList.getItems().clear();
        File imageFolder = new File(this.currentFolder, "images");
        // Should be checked
        if (!imageFolder.exists())
            return;
        File[] files = imageFolder.listFiles();
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            imageList.getItems().add(file);
        }
        if (imageList.getItems().size() > 0) {
            // Select the first image
            imageList.getSelectionModel().selectFirst();
        }
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
     * @param imageFile Note: the passed parameter is an image file, not its annotation file.
     * @return
     */
    private  boolean loadAnnotation(File imageFile) {
        File annotationFolder = getAnnotationsFolder(this.currentFolder);
        if (!annotationFolder.exists())
            return false; // Should not occur
        String imageFileName = imageFile.getName();
        int index = imageFileName.lastIndexOf(".");
        String annotationFileName = imageFileName.substring(0, index) + ".json";
        File annotationFile = new File(annotationFolder, annotationFileName);
        // This may be possible. Create a warning
        if (!annotationFile.exists()) {
            Dialogs.showWarningNotification("No Annotation File",
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

            // Get features
            JsonNode featuresNode = rootNode.path("features");
            JsonNode classNode = featuresNode.path("class");
            JsonNode annoStyleNode = featuresNode.path("anno_style");
            JsonNode metadataNode = featuresNode.path("metadata");

            // Get annotations
            JsonNode annotationsNode = rootNode.path("annotation");
            ObservableList<CedarAnnotation> cedarAnnotations = FXCollections.observableArrayList();
            for (int i = 0; i < annotationsNode.size(); i++) {
                JsonNode annotation = annotationsNode.get(i);
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

                String annotationStyle = annoStyleNode.get(i).textValue();
                String metadata = metadataNode.get(i).textValue();
                // This should use to String since the original value is a class
                String annotationClass = classNode.get(i).toString();
                CedarAnnotation cedarAnnotation = new CedarAnnotation();
                cedarAnnotation.setAnnotationStyle(annotationStyle);
                cedarAnnotation.setPathObject(annotationObject);
                cedarAnnotation.setClassName(annotationClass);
                cedarAnnotation.setMetaData(metadata);
                cedarAnnotations.add(cedarAnnotation);

                // Assign class to annoationObject so that we can see different colors
                annotationObject.setPathClass(PathClass.fromString(annotationClass));
                imageData.getHierarchy().addObject(annotationObject, false);

            }
            annotationTable.setItems(cedarAnnotations);
            updateAnnoationBtn.setDisable(true);
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

    /**
     * Provide a tabbed view so that this view can be added into a TabPane.
     * @return
     */
    public Tab getTabView() {
        Tab tab = new Tab(getTabName());
        tab.setContent(this.contentPane);
        return tab;
    }

    public String getTabName() {
        return this.TAB_NAME;
    }

}
