package qupath.lib.extension.cedar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.extension.cedar.ActionLogging.CedarExtensionAction;
import qupath.lib.extension.cedar.ActionLogging.QPathActionTrackingInterface;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;
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
import qupath.lib.extension.cedar.LoggingAspect.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

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
    private Button updateAnnotationBtn;
    private Button inferAnnotationBtn;
    private QuPathGUI qupath;
    // Track the PathObject change
    private PathObjectHierarchyListener pathListener;
    // Track the selection in the image view
    private PathObjectSelectionListener pathObjectSelectionListener;
    // A flag indicating the internal table selection
    private boolean isHandlingPathObjectSelection;
    // Keep previous hierarchy so that we can remove listeners
    private PathObjectHierarchy pathObjectHierarchy;
    // Used for animation
    private Timeline timeline;
    private Button startBtn;
    private Button pauseBtn;
    private Button stopBtn;
    private CheckBox autoAssignCheckedBox;
    private TextField durationTF;
    // Filter TextField to be reset after loading
    private TextField filterTF;
    // A flag to block the changes to self
    private boolean changeFromObject;

    private static CedarExtensionView view;

    private QPathActionTrackingInterface service;
    public CedarExtensionView(QPathActionTrackingInterface svc){
        this.service=svc;
    }

    // Tracking the action to time and log
    private static int cedarExtensionActionId = 0;
    static HashMap<Integer, CedarExtensionAction> trackedAnnotations = new HashMap<>();
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
        this.qupath.getStage().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, e -> saveAnnotations());
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
        // This line is copied from AnnotationPane's implementation
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
                .filter(annotation -> annotation.getPathObject().equals(pathObjectSelected))
                .findFirst()
                .ifPresent(annotation -> {
                    this.annotationTable.getSelectionModel().select(annotation);
                    // For some unknown reason, have to call the following method
                    // in order to switch the mouse click selection to this table.
                    // Otherwise, the user has to do double click to the current selected
                    // row to get the selection. This is very weird!!!!
                    annotationTable.getSelectionModel().getSelectedItem();
                    // Update the button manually
                    this.inferAnnotationBtn.setDisable(annotation.getClassId() != -1);
                    annotationTable.scrollTo(annotation);
                });
        this.isHandlingPathObjectSelection = false;
    }


    private void handlePathObjectChangeEvent(PathObjectHierarchyEvent event) {
        if (changeFromObject)
            return; // Changes from this object
        if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.REMOVED) {
            List<PathObject> changedObjects = event.getChangedObjects();
            if (changedObjects == null || changedObjects.size() == 0)
                return;
            List<CedarAnnotation> toBeRemoved = new ArrayList<>();
            for (CedarAnnotation annotation : annotationTable.getItems()) {
                if (changedObjects.contains(annotation.getPathObject())) {
                    toBeRemoved.add(annotation);
                }
            }
            if (toBeRemoved.size() > 0) {
                ObservableList<CedarAnnotation> source = getTableSource();
                source.removeAll(toBeRemoved);
            }
        }
        else if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.ADDED) {
            List<PathObject> changedObjects = event.getChangedObjects();
            if (changedObjects == null || changedObjects.size() == 0)
                return;
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
                CedarAnnotation cedarAnnotation = this.createNewAnnotationForPathObject(pathObject);
                toBeAdded.add(cedarAnnotation);
            }
            if (toBeAdded.size() > 0) {
                ObservableList<CedarAnnotation> source = getTableSource();
                source.addAll(toBeAdded);
            }
        }
        else if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.OTHER_STRUCTURE_CHANGE) {
            // This event is fired when multiple objects are deleted, merged, or split at the same time
            // For this event, getChangedObjects returns null. Therefore, we need to do some details comparison
            // to remove or add new
            Collection<PathObject> currentObjects = event.getHierarchy().getAllObjects(false);
            ObservableList<CedarAnnotation> source = getTableSource();
            List<CedarAnnotation> toBeRemoved = new ArrayList<>();
            for (CedarAnnotation annotation : source) {
                if (!currentObjects.contains(annotation.getPathObject())) {
                    toBeRemoved.add(annotation);
                }
            }
            List<CedarAnnotation> toBeAdded = new ArrayList<>();
            for (PathObject pathObject : currentObjects) {
                boolean exist = false;
                for (CedarAnnotation annotation : source) {
                    if (annotation.getPathObject() == pathObject) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) {
                    CedarAnnotation cedarAnnotation = this.createNewAnnotationForPathObject(pathObject);
                    toBeAdded.add(cedarAnnotation);
                }
            }
            if (toBeAdded.size() > 0 || toBeRemoved.size() > 0) {
                source.removeAll(toBeRemoved);
                source.addAll(toBeAdded);
            }
        }
        else if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.CHANGE_CLASSIFICATION) {
            // This may not be that efficient. But probably the easiest!
            // Need to make sure class ids are correct
            List<PathObject> changedObjects = event.getChangedObjects();
            if (changedObjects == null || changedObjects.size() == 0)
                return;
            ObservableList<CedarAnnotation> source = getTableSource();
            for (PathObject pathObject : changedObjects) {
                for (CedarAnnotation annotation : source) {
                    if (annotation.getPathObject() == pathObject) {
                        Integer id = CedarPathClassHandler.getHandler().getClassId(pathObject.getPathClass().getName());
                        annotation.setClassId(id);
                    }
                }
            }
            annotationTable.refresh();
        }
        updateAnnotationBtn.setDisable(false);
    }

    private CedarAnnotation createNewAnnotationForPathObject(PathObject pathObject) {
        CedarAnnotation cedarAnnotation = new CedarAnnotation();
        cedarAnnotation.setPathObject(pathObject);
        if (pathObject.getPathClass() == null) {
            cedarAnnotation.setAnnotationStyle("manual");
            cedarAnnotation.setClassId(-1); // Use -1 as a flag for not labeled
            cedarAnnotation.setMetaData("Created in QuPath");
        }
        return cedarAnnotation;
    }

    private ObservableList<CedarAnnotation> getTableSource() {
        SortedList<CedarAnnotation> sortedList = (SortedList<CedarAnnotation>) annotationTable.getItems();
        FilteredList<CedarAnnotation> filteredList = (FilteredList<CedarAnnotation>) sortedList.getSource();
        return (ObservableList<CedarAnnotation>) filteredList.getSource();
    }

    private void init() {
        this.contentPane = new BorderPane();

        VBox cedarBox = new VBox();
        cedarBox.setPadding(new Insets(1));
        folderLabel = new Label("Selected Folder");
        cedarBox.getChildren().add(folderLabel);
        // List images
        Label label = new Label("Images in folder");
        cedarBox.getChildren().add(label);

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

        BorderPane tablePane = new BorderPane();
        tablePane.setBottom(createAnimationPane());
        tablePane.setCenter(annotationTable);
        tablePane.setTop(createFilter());

        splitPane.getItems().addAll(imageList, tablePane);
        splitPane.setDividerPositions(0.5d);
        cedarBox.getChildren().add(splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        HBox buttonBox = new HBox(6);
        buttonBox.setAlignment(Pos.CENTER);
        // Add etched border to buttonBox
        buttonBox.setBorder(new Border(new BorderStroke(
                Color.LIGHTGRAY, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(1))
        ));

        inferAnnotationBtn = new Button("Infer Annotation");
        inferAnnotationBtn.setOnAction(e -> {
            inferAnnotations();
            inferAnnotationBtn.setDisable(true);
        });
        inferAnnotationBtn.setDisable(true);
        buttonBox.getChildren().add(inferAnnotationBtn);

        updateAnnotationBtn = new Button("Update Annotation");
        updateAnnotationBtn.setOnAction(e -> {
            saveAnnotations();
        });
        updateAnnotationBtn.setDisable(true);
        buttonBox.getChildren().add(updateAnnotationBtn);
        cedarBox.getChildren().add(buttonBox);

        ((BorderPane) contentPane).setCenter(cedarBox);
    }

    public QuPathGUI getQupath() {
        return this.qupath;
    }

    private void inferAnnotations() {
        File imageFile = imageList.getSelectionModel().getSelectedItem();
        File annotationsFolder = getAnnotationsFolder(currentFolder);
        // Check if this is for a ROI
        ROI roi = null;
        CedarAnnotation annotation = annotationTable.getSelectionModel().getSelectedItem();
        if (annotation != null && annotation.getPathObject() != null)
            roi = annotation.getPathObject().getROI();
        new AnnotationInferrer().infer(roi, imageFile, annotationsFolder, this);
    }

    private HBox createAnimationPane() {
        startBtn = new Button("play");
        pauseBtn = new Button("pause");
        stopBtn = new Button("stop");
        autoAssignCheckedBox = new CheckBox("checked");
        Label durationLabel = new Label("time(s):");
        durationTF = new TextField(0.5 + "");
        durationTF.setPrefColumnCount(3);
        // Add a listener to ensure only double values are accepted
        // TODO: Is there a better way to do this?
        durationTF.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (!newValue.matches("\\d*\\.?\\d*")) {
                    durationTF.setText(oldValue);
                }
            }
        });

        HBox buttonBox = new HBox(6, startBtn, pauseBtn, stopBtn,
                autoAssignCheckedBox, durationLabel, durationTF);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(2));
        // Add etched border to buttonBox
        buttonBox.setBorder(new Border(new BorderStroke(
                Color.LIGHTGRAY, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(1))
        ));

        timeline = new Timeline();
        timeline.setCycleCount(1);

        // Use space key to control
        //TRACK
        annotationTable.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.SPACE) {
                if (timeline.getStatus() == Animation.Status.PAUSED) {
                    timeline.play();
                    pauseBtn.setDisable(false);
                } else if (timeline.getStatus() == Animation.Status.RUNNING) {
                    timeline.pause();
                    pauseBtn.setDisable(true);
                }
            }
        });

        //TRACK
        startBtn.setOnAction(e -> {
            if (timeline.getStatus() == Animation.Status.PAUSED) {
                timeline.play();
                pauseBtn.setDisable(false);
                return;
            }
            setUpTimeline();
            // In case it has no focus
            annotationTable.requestFocus();
            timeline.play();
            pauseBtn.setDisable(false);
            stopBtn.setDisable(false);
        });
        //TRACK
        pauseBtn.setOnAction(e -> {
            timeline.pause();
            pauseBtn.setDisable(true);
        });
        //TRACK
        stopBtn.setOnAction(e -> {
            timeline.stop();
            timeline.getKeyFrames().clear();
            stopBtn.setDisable(true);
            pauseBtn.setDisable(true);
        });


        return buttonBox;
    }

    private void setUpTimeline() {
        if (timeline != null) { // Should not do it. Just in case...
            timeline.getKeyFrames().clear();
        }

        int startIndex = annotationTable.getSelectionModel().getSelectedIndex();
        if (startIndex < 0 || startIndex >= annotationTable.getItems().size()) {
            startIndex = 0; // Restart from 0
        }
        double duration = Double.parseDouble(durationTF.getText().trim()); // per row
        for (int i = startIndex; i < annotationTable.getItems().size(); i++) {
            int rowIndex = i;
            KeyFrame keyFrame = new KeyFrame(Duration.seconds((i - startIndex) * duration + duration), event -> {
                // Clear the previous selection
                annotationTable.getSelectionModel().clearSelection();

                // Select the current row
                annotationTable.getSelectionModel().select(rowIndex);

                // Scroll to the current row to bring it into view
                annotationTable.scrollTo(rowIndex);
                if (autoAssignCheckedBox.isSelected()) {
                    CedarAnnotation annotation = annotationTable.getItems().get(rowIndex);
                    if (annotation.getAnnotationStyle() == AnnotationType.auto) {
                        annotation.setAnnotationStyle(AnnotationType.auto_checked);
                        annotationTable.refresh();
                    }
                }
            });
            timeline.getKeyFrames().add(keyFrame);
        }
    }

    private HBox createFilter() {
        // Create a ChoiceBox
        ChoiceBox<String> choiceBox = new ChoiceBox<>();
        // Add column names to the ChoiceBox
        choiceBox.getItems().addAll("class id", "class name", "type", "meta data");
        // Set a default value
        choiceBox.setValue("class name");

        Label label = new Label("Filter for: ");

        // Create new text field for filter
        TextField filterField = new TextField();
        filterField.setTooltip(new Tooltip("Return or enter to filter"));
        filterField.setOnAction(event -> {
            String searchText = filterField.getText().trim();
            String selectedColumnName = choiceBox.getSelectionModel().getSelectedItem();
            filterAnnotationTable(searchText, selectedColumnName);
        });
        this.filterTF = filterField; // Keep at the object level so that we can reset it after loading

        // Logic to filter based on selected column
        Button reset = new Button("Reset");
        reset.setOnAction(e -> {
            filterField.setText("");
            filterAnnotationTable(null, null);
        });

        // Set host HBox
        HBox filters = new HBox(6, label, choiceBox, filterField, reset);
        filters.setPadding(new Insets(2));
        filters.setAlignment(Pos.CENTER);
        return filters;
    }

    private void filterAnnotationTable(String searchText, String selectedColumnName) {
        Predicate<CedarAnnotation> filter = null;
        if (selectedColumnName == null) {
            filter = cedarAnnotation -> true;
        } else {
            switch (selectedColumnName) {
                case "class name":
                    filter = cedarAnnotation -> cedarAnnotation.getClassName().toLowerCase().contains(searchText.toLowerCase());
                    break;
                case "class id":
                    try {
                        Integer searchId = Integer.valueOf(searchText);
                        filter = cedarAnnotation -> cedarAnnotation.getClassId().equals(searchId);
                    } catch (NumberFormatException numberFormatException) {
                        filter = cedarAnnotation -> cedarAnnotation.getClassName().toLowerCase().contains(searchText.toLowerCase());
                    }
                    break;
                case "meta data":
                    filter = cedarAnnotation -> cedarAnnotation.getMetaData().toLowerCase().contains(searchText.toLowerCase());
                    break;
                case "type":
                    filter = cedarAnnotation -> cedarAnnotation.getAnnotationStyle().name().toLowerCase().contains(searchText.toLowerCase());
                    break;
            }
        }
        if (filter != null) {
            SortedList<CedarAnnotation> sortedList = (SortedList<CedarAnnotation>) annotationTable.getItems();
            FilteredList<CedarAnnotation> tableData = (FilteredList<CedarAnnotation>) sortedList.getSource();
            tableData.setPredicate(filter);
            changeFromObject = true;
            updatePathObjectHierarchy();
            changeFromObject = false;
        }
    }

    private void updatePathObjectHierarchy() {
        if (this.pathObjectHierarchy == null)
            return;
        // Update the pathObjectHierarchy
        List<PathObject> shownObjects = annotationTable.getItems().stream().map(a -> a.getPathObject()).toList();
        // Just a lazy way to update the hierarchy so that we will get the same view.
        this.pathObjectHierarchy.clearAll();
        this.pathObjectHierarchy.addObjects(shownObjects);
    }


    private void initAnnotationTable() {
        annotationTable.setEditable(true); // Enable editing
        annotationTable.getSelectionModel().selectedItemProperty().addListener((observable -> {
            handleAnnotationTableSelection();
        }));
        // With the current table setting, one clicking to editing, multiple selection is buggy and should not
        // be enabled without much more tests!!!
        // However, multiple selection in the image view works.
//        annotationTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<CedarAnnotation, Integer> classIdCol = createClassIdColumn("class id", "classId");

        TableColumn<CedarAnnotation, String> classCol = createClassNameColumn("class name", "className");

        TableColumn<CedarAnnotation, AnnotationType> annotationStyleCol = createAnnotationTypeColumn("type", // Make the name simple
                "annotationStyle");
        annotationStyleCol.setOnEditCommit(event -> {
            event.getRowValue().setAnnotationStyle(event.getNewValue());
            updateAnnotationBtn.setDisable(false);
        });

        TableColumn<CedarAnnotation, String> metaDataSol = createTableColumn("meta data",
                "metaData", false);
        metaDataSol.setOnEditCommit(event -> {
            event.getRowValue().setMetaData(event.getNewValue());
            updateAnnotationBtn.setDisable(false);
        });

        annotationTable.getColumns().addAll(classIdCol, classCol, annotationStyleCol, metaDataSol);
        annotationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        // Set some default width
        // Bind preferred width of columns to TableView width
        annotationTable.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double tableWidth = newWidth.doubleValue();
            classCol.setPrefWidth(tableWidth * 0.5); // 50% of table width
        });

        // To avoid any null exception, an empty filtered list is added
        FilteredList<CedarAnnotation> filteredList = new FilteredList(FXCollections.observableArrayList());
        // Use sorted list so that we can still sort the columns
        SortedList<CedarAnnotation> sortedList = new SortedList(filteredList);
        sortedList.comparatorProperty().bind(annotationTable.comparatorProperty());

        annotationTable.setItems(sortedList);
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
                        //TRACK
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
        } else {
            // Have to call this. Otherwise, the table cannot be edited.
            classCol.setCellFactory(TextFieldTableCell.forTableColumn());
        }
        return classCol;
    }

    private TableColumn<CedarAnnotation, AnnotationType> createAnnotationTypeColumn(String colName, String propName) {
        TableColumn<CedarAnnotation, AnnotationType> col = new TableColumn<>(colName);
        col.setCellValueFactory(new PropertyValueFactory<>(propName));
        // Enable single click to edit
        // Don't use the customized choicebox. Unless to copy the whole ChoiceBoxTableCell and then modify to improve.
//        col.setCellFactory(column -> new AnnotationTypeChoiceBox<>(AnnotationType.values()));
        col.setCellFactory(column -> {
            ChoiceBoxTableCell<CedarAnnotation, AnnotationType> cell = new ChoiceBoxTableCell<>(AnnotationType.values());
            cell.setOnMouseClicked(event -> {
                //TRACK
                if (!cell.isEditing())
                    cell.startEdit();
            });
            return cell;
        });
        return col;
    }

    private TableColumn<CedarAnnotation, String> createClassNameColumn(String colName, String propName) {
        TableColumn<CedarAnnotation, String> classCol = new TableColumn<>(colName);
        classCol.setCellValueFactory(new PropertyValueFactory<>(propName));
        classCol.setCellFactory(column -> {
            ObservableList<String> classNames = FXCollections.observableArrayList(CedarPathClassHandler.getHandler().getClassNames());
            ChoiceBoxTableCell<CedarAnnotation, String> cell = new ChoiceBoxTableCell<>(classNames);
            // Enable single click to edit
            cell.setOnMouseClicked(event -> {
                //TRACK
                if (!cell.isEditing())
                    cell.startEdit();
            });
            return cell;
        });
        classCol.setOnEditCommit(event -> {
            event.getRowValue().setClassName(event.getNewValue());
            this.annotationTable.refresh(); // TODO: Look for a method to refresh a cell only
            updateAnnotationBtn.setDisable(false);
        });
        return classCol;
    }

    private TableColumn<CedarAnnotation, Integer> createClassIdColumn(String colName, String propName) {
        TableColumn<CedarAnnotation, Integer> col = new TableColumn<>(colName);
        col.setCellValueFactory(new PropertyValueFactory<>(propName));
        // Enable a single click to editing
        // Cannot enable for all. Otherwise, the selection is difficult.
        // Here we have to use a customized TextField for cell editing to enable commitEdit
        // when the mouse exits.
//        col.setCellFactory(column -> new AnnotationClassIdTableCell<>());
        col.setCellFactory(column -> {
            //TRACK
            TextFieldTableCell<CedarAnnotation, Integer> cell = new TextFieldTableCell<>(new IntegerStringConverter());
            cell.setOnMouseClicked(event -> {
                if (!cell.isEditing())
                    cell.startEdit();
            });
            cell.setOnMouseExited(event -> {
                if (cell.isEditing()) {
                    // A way to hack into the children to get the private TextField.
                    // By using this, we don't need a customized Cell.
                    List<Node> children = cell.getChildrenUnmodifiable();
                    if (children != null) {
                        for (Node node : children) {
                            if (node instanceof TextField) {
                                cell.commitEdit(Integer.parseInt(((TextField) node).getText()));
                            }
                        }
                    }
                }
            });
            return cell;
        });
        col.setOnEditCommit(event -> {
            CedarAnnotation annotation = event.getRowValue();
            annotation.setClassId(event.getNewValue());
            // This is not efficient. Use it for the time being to synchronize the whole row
            annotationTable.refresh();
            updateAnnotationBtn.setDisable(false);
        });
        return col;
    }

    private void handleImageSelection() {
        File imageFile = imageList.getSelectionModel().getSelectedItem();
        if (imageFile == null)
            return; // Do nothing
        loadImage(imageFile);
        // Wrap into here to avoid displaying issue when there is dialog shown (e.g. confirm the
        // image type)
        // Inside runLater doesn't work when no dialog is shown!
//        Platform.runLater(() -> loadAnnotation(imageFile));
        // Use thread works for both cases.
        // However, an exception is thrown sometimes indicating this thread is not
        // in the JavaFX thread.
        Thread t = new Thread(() -> loadAnnotation(imageFile));
        t.start();
//        loadAnnotation(imageFile);
    }

    @Tracking(action = "Table Annotation Selected")
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
        // Enable the "Infer Annotation" button when class id = -1 (a flag for a new path object)
        this.inferAnnotationBtn.setDisable(annotation.getClassId() != -1);
    }

    //@Tracking(action = "Saving an Annotation")
    private void saveAnnotations() {
        // Should not save if it is disabled
        if (this.updateAnnotationBtn.isDisabled() || this.currentImageFile == null)
            return; // Do nothing
        logger.info("Saving annotations for " + imageList.getSelectionModel().getSelectedItem());
        // Save the data into geojson
        File annotationFile = getAnnotationFileForImage(currentImageFile, true);
        // Since we have used filer, we need to use the source of the original filtered list
        List<PathObject> pathObjects = getTableSource().stream().map(a -> a.getPathObject()).toList();
        try {
            backupAnnotations(annotationFile);
            PathIO.exportObjectsAsGeoJSON(annotationFile, pathObjects);
            // Disable after saving
            this.updateAnnotationBtn.setDisable(true);
        } catch (IOException e) {
            logger.error("Cannot save the annotation: " + e.getMessage(), e);
        }
    }

    // TODO: use for prototyping
    @Tracking(action = "")
    private void backupAnnotations(File annoationFile) {
        if (!annoationFile.exists())
            return;
        // Change the file name to .json.bak
        String backupFileName = annoationFile.getAbsolutePath() + ".bak";
        File backupFile = new File(backupFileName);
        if (backupFile.exists())
            backupFile.delete(); // Delete it
        annoationFile.renameTo(backupFile);
        this.addPropertyToTracking(cedarExtensionActionId, backupFileName);
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

    private File getAnnotationFileForImage(File imageFile, boolean isForSave) {
        String imageFileName = imageFile.getName();
        int index = imageFileName.lastIndexOf(".");
        String imageFileRoot = imageFileName.substring(0, index);
        // Check if there is a geojson file. If yes we will use it. Otherwise, use json.
        String geojsonFileName = imageFileRoot + ".geojson";
        File geoFile = new File(getAnnotationsFolder(currentFolder), geojsonFileName);
        if (isForSave)
            return geoFile;
        if (geoFile.exists())
            return geoFile;
        // Now try json
        String annotationFileName = imageFileRoot + ".json";
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
//            Dialogs.showErrorMessage("Error in Folder Choosing",
//                    "Cannot find the annotations folder in \n" + folder);
            // We will automatically create an annotation folder if it doesn't exist to
            // support inference
            annotationFolder.mkdir();
//            return;
        }
        // Before switch to the new folder, save whatever annotation we have if needed.
        saveAnnotations();
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
                String fileName = file.getName();
                return !file.getName().startsWith("."); // Include all no hidden files
            }
        };
        File[] files = imageFolder.listFiles(filter);
        List<File> fileList = checkOMETiff(files);
        Collections.sort(fileList, Comparator.comparing(File::getName));
        for (File file : fileList) {
            imageList.getItems().add(file);
        }
//        if (imageList.getItems().size() > 0) {
//            // Select the first image
//            imageList.getSelectionModel().selectFirst();
//        }
    }

    /**
     * When there are both tiff and ome.tif images in the same folder, choose ome.tif
     * images over tif.
     * @param files
     * @return
     */
    private List<File> checkOMETiff(File[] files) {
        Map<String, List<File>> name2files = new HashMap<>();
        List<File> fileList = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".tif") || name.endsWith(".tiff")) {
                name = name.substring(0, name.lastIndexOf(".tif"));
                if (name.endsWith(".ome")) {
                    name = name.substring(0, name.lastIndexOf(".ome"));
                }
                List<File> list = name2files.get(name);
                if (list == null) {
                    list = new ArrayList<>();
                    name2files.put(name, list);
                }
                list.add(file);
            }
            else
                fileList.add(file);
        }
        if (name2files.size() == 0)
            return fileList;
        for (String name : name2files.keySet()) {
            List<File> list = name2files.get(name);
            if (list.size() == 1)
                fileList.add(list.get(0));
            else {
                // Find the ome.tif format
                for (File file : list) {
                    if (file.getName().endsWith(".ome.tif") || file.getName().endsWith(".ome.tiff")) {
                        fileList.add(file);
                        break;
                    }
                }
            }
        }
        return fileList;
    }

    /**
     * Load an image file.
     *
     * @param imageFile
     * @return
     */
    @Tracking(action = "Loading an image")
    public boolean loadImage(File imageFile) {
        try {
            // Save the annotation first in case there is something changed there.
            saveAnnotations();
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
                this.addPropertyToTracking(cedarExtensionActionId, imageFile.getName());
            }
            return rtn;
        } catch (IOException e) {
            Dialogs.showErrorMessage("Error in Opening Image",
                    "Cannot open image: " + imageFile.getName());
            logger.error("Cannot open image: " + imageFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Load the annotations for the passed image file. It is assumed the annotations should be in a
     * json file having the same file name, but in the annotations folder.
     *
     * @param imageFile Note: the passed parameter is an image file, not its annotation file.
     * @return
     */
    @Tracking(action = "Loading an annotation")
    private boolean loadAnnotation(File imageFile) {
        File annotationFolder = getAnnotationsFolder(this.currentFolder);
        if (!annotationFolder.exists())
            return false; // Should not occur
        // Load new annotations
        File annotationFile = getAnnotationFileForImage(imageFile, false);
        // This may be possible. Create a warning
        if (!annotationFile.exists()) {
            Dialogs.showWarningNotification("No Annotation File",
                    "Cannot find an annotation file for the image file, " + imageFile.getName() + ".");
            // Reset the table first
            ObservableList<CedarAnnotation> source = getTableSource();
            source.clear();
            updateAnnotationBtn.setDisable(true);
            inferAnnotationBtn.setDisable(false);
            return false;
        }
        try {
            // If there is annotation, the infer button should be disabled
            inferAnnotationBtn.setDisable(true);
            if (filterTF != null)
                filterTF.clear();
            return parseAnnotationFile(annotationFile);
        } catch (IOException e) {
            Dialogs.showErrorMessage("Error in Opening Annotation",
                    "Cannot open annotation for: " + imageFile.getName());
            logger.error("Cannot open annotation for: " + imageFile.getAbsolutePath(), e);
            return false;
        }
    }

   @Tracking(action = "Adding a Path Object")
    void addPathObjects(List<PathObject> pathObjects) {
        ImageData<BufferedImage> imageData = qupath.getImageData();
        changeFromObject = true;
        imageData.getHierarchy().addObjects(pathObjects);
        ObservableList<CedarAnnotation> newAnnotations = FXCollections.observableArrayList(pathObjects.stream().map(p -> new CedarAnnotation(p)).toList());
        sortAnnotations(newAnnotations);
        ObservableList<CedarAnnotation> source = getTableSource();
        // Insert just below the original path object that is selected
        int selectedIndex = annotationTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex > -1)
            source.addAll(selectedIndex, newAnnotations);
        else
            source.addAll(newAnnotations); // insert at the end
        // Enable save button so that we can save
        this.updateAnnotationBtn.setDisable(false);
        // Select all together with the original one
        List<PathObject> toBeSelected = new ArrayList<>(pathObjects);
        CedarAnnotation selectedAnnotation = annotationTable.getSelectionModel().getSelectedItem();
        if (selectedAnnotation != null)
            toBeSelected.add(selectedAnnotation.getPathObject());
        QP.selectObjects(toBeSelected);
        changeFromObject = false;
    }

    boolean parseAnnotationFile(File annotationFile) throws IOException {
        logger.info("Loading annotation: " + annotationFile.getAbsolutePath());
        ObservableList<CedarAnnotation> cedarAnnotations = null;
        if (annotationFile.getName().endsWith(".geojson")) {
            cedarAnnotations = loadFromGeoJSON(annotationFile);
        } else if (annotationFile.getName().endsWith(".json")) {
            cedarAnnotations = loadFromJSON((annotationFile));
        }
        if (cedarAnnotations == null) {
            logger.info("Cannot load annnotation file. The file must have extension name .geojson or .json.");
            return false;
        }
        changeFromObject = true;
        ImageData<BufferedImage> imageData = this.qupath.getImageData();
        cedarAnnotations.forEach(a -> imageData.getHierarchy().addObject(a.getPathObject(), false));
        sortAnnotations(cedarAnnotations);
        // Wrap the annotation list into a FilteredList so that we can do filtering
        // Replace the data for the table. We only need to replace the original source
        // the table view will be refreshed automatically.
        ObservableList<CedarAnnotation> dataSource = this.getTableSource();
        dataSource.clear();
        dataSource.setAll(cedarAnnotations);
        // Have to wrap it into this. Otherwise, it cannot be disabled.
        Platform.runLater(() -> updateAnnotationBtn.setDisable(true));
        QP.fireHierarchyUpdate(this.qupath.getImageData().getHierarchy());
        changeFromObject = false;
        return true;
    }

    /**
     * Sort the list of CedarAnnoations based on ROI's centroid starting from top left corner to the bottom right corner.
     *
     * @param annotations
     */
    private void sortAnnotations(List<CedarAnnotation> annotations) {
        annotations.sort((a1, a2) -> {
            // Using bounds, instead of centroid, gives a better intuition
            double a1_x = a1.getPathObject().getROI().getBoundsX();
            double a1_y = a1.getPathObject().getROI().getBoundsY();
            double a2_x = a2.getPathObject().getROI().getBoundsX();
            double a2_y = a2.getPathObject().getROI().getBoundsY();
            // Compare y first: We want to make sure a2, which has higher value to be listed later, therefore
            // the delta should be like this.
            double delta_y = a1_y - a2_y;
            if (delta_y > 0) return 1;
            if (delta_y < 0) return -1;
            double delta_x = a1_x - a2_x;
            if (delta_x > 0) return 1;
            return -1;
        });
    }

    private ObservableList<CedarAnnotation> loadFromJSON(File annotationFile) throws IOException {
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

            // Assign class to annotationObject so that we can see different colors
            PathClass pathCls = CedarPathClassHandler.getHandler().getPathClass(cedarAnnotation.getClassId());
            annotationObject.setPathClass(pathCls);
            cedarAnnotation.setClassName(pathCls.getName());
        }

        return cedarAnnotations;
    }

    private ObservableList<CedarAnnotation> loadFromGeoJSON(File annotationFile) throws IOException {
        List<PathObject> geoObjects = PathIO.readObjects(annotationFile);
        // Convert it into a list of CedarAnnotation
        ObservableList<CedarAnnotation> cedarAnnotations = FXCollections.observableArrayList(geoObjects.stream().map(p -> new CedarAnnotation(p)).toList());
        return cedarAnnotations;
    }

    /**
     * Provide a tabbed view so that this view can be added into a TabPane.
     *
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

   public void addPropertyToTracking(int id, String property) {
//        CedarExtensionAction cedarExtensionAction = trackedAnnotations.get(id);
//        String action = cedarExtensionAction.getAction();
//        cedarExtensionAction.setAction((action += property));
   }

   public static void addCedarAnnotationTracking(CedarExtensionAction cedarExtensionAction) {
       trackedAnnotations.put(cedarExtensionActionId, cedarExtensionAction);
       cedarExtensionActionId++;
   }
}
