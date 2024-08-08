package qupath.lib.extension.cedar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

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
    // The current image file shown
    private File currentImageFile;
    // Used to display annoations
    private TableView<CedarAnnotation> annotationTable;
    private Button updateAnnoationBtn;
    private QuPathGUI qupath;
    // Track the PathObject change
    private PathObjectHierarchyListener pathListener;
    // Track the selection in the image view
    private PathObjectSelectionListener pathObjectSelectionListener;
    // A flag indicating the internal table selection
    private boolean isHandlingPathObjectSelection;
    // Keep previous hierarchy so that we can remove listeners
    private PathObjectHierarchy pathObjectHierarchy;

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
        this.qupath.getStage().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, e -> saveAnnoations());
        pathListener = new PathObjectHierarchyListener() {
            @Override
            public void hierarchyChanged(PathObjectHierarchyEvent event) {
                handlePathObjectChangeEvent(event);
            }
        };
        pathObjectSelectionListener = new PathObjectSelectionListener() {
            @Override
            public void selectedPathObjectChanged(PathObject pathObjectSelected,
                                                  PathObject previousObject,
                                                  Collection<PathObject> allSelected) {
                handlePathObjectSelectionEvent(pathObjectSelected);
            }
        };
    }

    private void handlePathObjectSelectionEvent(PathObject pathObjectSelected) {
        // This line is copied from AnnoationPane's implementation
        if (!Platform.isFxApplicationThread()) {
            return;
        }
        // Check if pathObjectSelected has been selected already
        // Need to check this to avoid the selected row jumps after clicking
        CedarAnnotation selected = this.annotationTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getPathObject().equals(pathObjectSelected))
            return; // Nothing needs to be done
        // Try to find the selected PathObject
        this.isHandlingPathObjectSelection = true; // This flag is to control the table selection
        this.annotationTable.getSelectionModel().clearSelection(); // Clear it first
        this.annotationTable.getItems().stream()
                .filter(anotation -> anotation.getPathObject().equals(pathObjectSelected))
                .findFirst()
                .ifPresent(annotation -> {
                    this.annotationTable.getSelectionModel().select(annotation);
                    // For some unknown reason, have to call the following method
                    // in order to switch the mouse click selection to this table.
                    // Otherwise, the user has to do double click to the current selected
                    // row to get the selection. This is very weird!!!!
                    annotationTable.getSelectionModel().getSelectedItem();
                    annotationTable.scrollTo(annotation);
                });
        this.isHandlingPathObjectSelection = false;
    }


    private void handlePathObjectChangeEvent(PathObjectHierarchyEvent event) {
        List<PathObject> changedObjects = event.getChangedObjects();
        if (changedObjects == null || changedObjects.size() == 0)
            return;
        if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.REMOVED) {
            List<CedarAnnotation> toBeRemoved = new ArrayList<>();
            for (CedarAnnotation annotation : annotationTable.getItems()) {
                if (changedObjects.contains(annotation.getPathObject())) {
                    toBeRemoved.add(annotation);
                }
            }
            annotationTable.getItems().removeAll(toBeRemoved);
        }
        else if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.ADDED) {
            List<CedarAnnotation> toBeAdded = new ArrayList<>();
            for (PathObject pathObject : changedObjects) {
                // For some reason, the same PathObject is passed as ADDED multiple time
                boolean existed = false;
                for (CedarAnnotation annotation : annotationTable.getItems()) {
                    if (annotation.getPathObject() == pathObject) {
                        existed = true;
                        break;
                    }
                }
                if (existed)
                    continue;
                CedarAnnotation cedarAnnotation = new CedarAnnotation();
                cedarAnnotation.setPathObject(pathObject);
                cedarAnnotation.setAnnotationStyle("manual");
                cedarAnnotation.setClassId(-1); // Use -1 as a flag for not labeled
                cedarAnnotation.setMetaData("Created in QuPath");
                toBeAdded.add(cedarAnnotation);
            }
            if (toBeAdded.size() > 0)
                annotationTable.getItems().addAll(toBeAdded);
        }
        updateAnnoationBtn.setDisable(false);
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

        annotationTable = new TableView<>();
        initAnnotationTable();

        // Add image list and table into a splitpane
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(imageList, annotationTable);
        splitPane.setDividerPositions(0.5d);
        vbox.getChildren().add(splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        updateAnnoationBtn = new Button("Update Annotation");
        updateAnnoationBtn.setOnAction(e -> {
            saveAnnoations();
            updateAnnoationBtn.setDisable(true); // Disable it after saving
        });
        updateAnnoationBtn.setDisable(true);
        buttonBox.getChildren().add(updateAnnoationBtn);
        vbox.getChildren().add(buttonBox);

        ((BorderPane)contentPane).setCenter(vbox);
    }

    private void initAnnotationTable() {
        annotationTable.setEditable(true); // Enable editing
        annotationTable.getSelectionModel().selectedItemProperty().addListener((observable -> {
            handleAnnotationTableSelection();
        }));

        TableColumn<CedarAnnotation, Integer> classIdCol = createTableIdColumn("class id", "classId");
        classIdCol.setOnEditCommit(event -> {
            event.getRowValue().setClassId(event.getNewValue());
            updateAnnoationBtn.setDisable(false);
        });

        TableColumn<CedarAnnotation, String> classCol = createTableColumn("class name", "className", false);
        classCol.setOnEditCommit(event -> {
            event.getRowValue().setClassName(event.getNewValue());
            updateAnnoationBtn.setDisable(false);
        });

        TableColumn<CedarAnnotation, AnnotationType> annotationStyleCol = createAnnotationTypeTableColumn("type", // Make the name simple
                "annotationStyle");
        annotationStyleCol.setOnEditCommit(event -> {
            event.getRowValue().setAnnotationStyle(event.getNewValue());
            updateAnnoationBtn.setDisable(false);
        });

        TableColumn<CedarAnnotation, String> metaDataSol = createTableColumn("metadata",
                "metaData", false);
        metaDataSol.setOnEditCommit(event -> {
            event.getRowValue().setMetaData(event.getNewValue());
            updateAnnoationBtn.setDisable(false);
        });

        annotationTable.getColumns().addAll(classIdCol, classCol, annotationStyleCol, metaDataSol);
        annotationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        // Set some default width
        // Bind preferred width of columns to TableView width
        annotationTable.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double tableWidth = newWidth.doubleValue();
            classCol.setPrefWidth(tableWidth * 0.5); // 50% of table width
        });
    }

    private TableColumn<CedarAnnotation, String> createTableColumn(String colName, String propName, boolean enableOneClickEdit) {
        TableColumn<CedarAnnotation, String> classCol = new TableColumn<>(colName);
        classCol.setCellValueFactory(new PropertyValueFactory<>(propName));
        // No need to call. The editable has been set at the table level
//        classCol.setEditable(true);
        if (enableOneClickEdit) {
            classCol.setCellFactory(column -> {
                TextFieldTableCell<CedarAnnotation, String> cell = new TextFieldTableCell<>();
                cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                    if (event.getClickCount() == 1 && !cell.isEmpty()) {
                        this.annotationTable.edit(cell.getIndex(), column);
                    }
                });
                // Ignore this for the time being. Not sure how to gain the focus for one cell.
//                cell.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
//                    if (isNowFocused && !cell.isEmpty()) {
//                        this.annotationTable.edit(cell.getIndex(), column);
//                    }
//                });
                return cell;
            });
        }
        else {
            // Have to call this. Otherwise, the table cannot be edited.
            classCol.setCellFactory(TextFieldTableCell.forTableColumn());
        }
        return classCol;
    }

    private TableColumn<CedarAnnotation, AnnotationType> createAnnotationTypeTableColumn(String colName, String propName) {
        TableColumn<CedarAnnotation, AnnotationType> classCol = new TableColumn<>(colName);
        classCol.setCellValueFactory(new PropertyValueFactory<>(propName));
        // Enable single click to edit
        classCol.setCellFactory(column -> new AnnotationTypeChoiceBox<>(AnnotationType.values()));
        return classCol;
    }

    private TableColumn<CedarAnnotation, Integer> createTableIdColumn(String colName, String propName) {
        TableColumn<CedarAnnotation, Integer> classCol = new TableColumn<>(colName);
        classCol.setCellValueFactory(new PropertyValueFactory<>(propName));
        // Enable a single click to editing
        // TODO: For the time being, only click to editing for this column.
        // Need to discuss what other columns also need to enable this.
        // Cannot enable for all. Otherwise, the selection is difficult.
        classCol.setCellFactory(column -> {
            TextFieldTableCell<CedarAnnotation, Integer> cell = new TextFieldTableCell<>();
            cell.setConverter(new IntegerStringConverter());
            cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getClickCount() == 1 && !cell.isEmpty()) {
                    this.annotationTable.edit(cell.getIndex(), column);
                }
            });
            // Ignore this for the time being. Not sure how to gain the focus for one cell.
//            cell.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
//                if (isNowFocused && !cell.isEmpty()) {
//                    this.annotationTable.edit(cell.getIndex(), column);
//                }
//            });
            return cell;
        });
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
        if (isHandlingPathObjectSelection)
            return;
        CedarAnnotation annotation = annotationTable.getSelectionModel().getSelectedItem();
        if (annotation == null)
            return;
        PathObject pathObject = annotation.getPathObject();
        if (pathObject == null)
            return;
        QP.selectObjects(pathObject);
        this.qupath.getViewer().centerROI(pathObject.getROI());
    }

    private void saveAnnoations() {
        // Should not save if it is disabled
        if (this.updateAnnoationBtn.isDisabled() || this.currentImageFile == null)
            return; // Do nothing
        logger.info("Saving annotations for " + imageList.getSelectionModel().getSelectedItem());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        // Get the image name
        String imageName = this.currentImageFile.getName();
        json.put("image_name", imageName);
        ArrayNode classArray = mapper.createArrayNode();
        ArrayNode annotationArray = mapper.createArrayNode();
        ArrayNode metaArray = mapper.createArrayNode();
        ArrayNode pathArray = mapper.createArrayNode();
        for (CedarAnnotation annotation : annotationTable.getItems()) {
            // Force it to integer
            // TODO: This may need to discuss
            classArray.add(annotation.getClassId());
            annotationArray.add(annotation.getAnnotationStyle().toString());
            metaArray.add(annotation.getMetaData());
            // Create a path
            ArrayNode pointsArray = mapper.createArrayNode();
            PathObject pathObject = annotation.getPathObject();
            ROI roi = pathObject.getROI();
            List<Point2> points = roi.getAllPoints();
            for (Point2 point : points) {
                ArrayNode pointArray = mapper.createArrayNode();
                // Switch the order so that it can be consistent with the Python output
                // TODO: This should be changed!!!
                pointArray.add(point.getY());
                pointArray.add(point.getX());
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

        try {
            File annoationFile = getAnnotationFileForImage(currentImageFile);
            backupAnnonations(annoationFile);
            writer.writeValue(annoationFile, json);
        }
        catch(IOException e) {
            logger.error("Cannot save the annotation: " + e.getMessage(), e);
        }
    }

    private void backupAnnonations(File annoationFile) {
        if (!annoationFile.exists())
            return;
        // Change the file name to .json.bak
        String backupFileName = annoationFile.getAbsolutePath() + ".bak";
        File backupFile = new File(backupFileName);
        if (backupFile.exists())
            backupFile.delete(); // Delete it
        annoationFile.renameTo(backupFile);
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

    private File getAnnotationFileForImage(File imageFile) {
        String imageFileName = imageFile.getName();
        int index = imageFileName.lastIndexOf(".");
        String annotationFileName = imageFileName.substring(0, index) + ".json";
        File annotationFile = new File(getAnnotationsFolder(currentFolder), annotationFileName);
        return annotationFile;
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
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                String fileName   = file.getName();
                return !file.getName().startsWith("."); // Include all no hidden files
            }
        };
        File[] files = imageFolder.listFiles(filter);
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
            // Save the annotation first in case there is something changed there.
            saveAnnoations();
            logger.info("Loading image: " + imageFile.getAbsolutePath());
            this.currentImageFile = imageFile;
            boolean rtn = this.qupath.openImage(this.qupath.getViewer(), imageFile.getAbsolutePath());
            if (rtn) {
                if (this.pathObjectHierarchy != null) {
                    this.pathObjectHierarchy.removeListener(this.pathListener);
                    this.pathObjectHierarchy.getSelectionModel().removePathObjectSelectionListener(this.pathObjectSelectionListener);
                }
                // Just in case it has been added
                PathObjectHierarchy hierarchy = qupath.getViewer().getHierarchy();
                if (hierarchy != null) {
                    // Just in case this has been added
//                    hierarchy.removeListener(this.pathListener);
                    hierarchy.addListener(this.pathListener);
                    hierarchy.getSelectionModel().addPathObjectSelectionListener(this.pathObjectSelectionListener);
                }
                // If it is null, still record it.
                this.pathObjectHierarchy = hierarchy;
            }
            return rtn;
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
        // Load new annotations
        File annotationFile = getAnnotationFileForImage(imageFile);
        // This may be possible. Create a warning
        if (!annotationFile.exists()) {
            Dialogs.showWarningNotification("No Annotation File",
                    "Cannot find an annotation file for the image file, " + imageFile.getName() + ".");
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
                    double x = point.get(1).asDouble();
                    double y = point.get(0).asDouble();
                    pointsList.add(new Point2(x, y));
                }
                ImagePlane plane = ImagePlane.getPlane(0, 0);
                ROI polyROI = ROIs.createPolygonROI(pointsList, plane);
                PathObject annotationObject = PathObjects.createAnnotationObject(polyROI);

                String annotationStyle = annoStyleNode.get(i).textValue();
                String metadata = metadataNode.get(i).textValue();
                // This should use to String since the original value is a class
                String annotationClassId = classNode.get(i).toString();
                Integer classId = Integer.parseInt(annotationClassId);
                CedarAnnotation cedarAnnotation = new CedarAnnotation();
                cedarAnnotation.setAnnotationStyle(annotationStyle);
                cedarAnnotation.setPathObject(annotationObject);
                cedarAnnotation.setClassId(classId);
                cedarAnnotation.setMetaData(metadata);
                cedarAnnotations.add(cedarAnnotation);

                // Assign class to annoationObject so that we can see different colors
                PathClass pathCls = CedarPathClassHandler.getHandler().getPathClass(cedarAnnotation.getClassId());
                annotationObject.setPathClass(pathCls);
                cedarAnnotation.setClassName(pathCls.getName());
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
