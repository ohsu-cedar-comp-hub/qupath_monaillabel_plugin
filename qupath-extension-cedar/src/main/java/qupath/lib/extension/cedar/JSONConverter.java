package qupath.lib.extension.cedar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static qupath.lib.io.PathIO.GeoJsonExportOptions.PRETTY_JSON;

public class JSONConverter {
    private static final String INPUT_DIRECTORY_PATH = "/Users/beaversd/CEDAR/JSON";
    private static final String OUTPUT_DIRECTORY_PATH = "/Users/beaversd/CEDAR/GEOJSON";
    private final static Logger logger = LoggerFactory.getLogger(JSONConverter.class);


    public static void main(String... args) throws IOException {
        readFile();
    }

    private static void readFile() throws IOException {

        File dir = new File(INPUT_DIRECTORY_PATH);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                try {
                    String fileName = child.getName();
                    int index = fileName.lastIndexOf(".");
                    String imageFileRoot = fileName.substring(0, index);
                    String geojsonFileName = imageFileRoot + ".geojson";
                    File geoFile = new File(OUTPUT_DIRECTORY_PATH, geojsonFileName);
                    ObservableList<CedarAnnotation> annotations = loadFromJSON(child);
                    PathIO.exportObjectsAsGeoJSON(geoFile, annotations.stream().map(CedarAnnotation::getPathObject).toList());
                } catch (IOException e) {
                    logger.error("Cannot save the annotation: " + e.getMessage(), e);
                }
            }
        } else {
            System.out.println("Directory not found!");
        }
    }

    private static ObservableList<CedarAnnotation> loadFromJSON(File annotationFile) throws IOException {
        // The following code is based on RunInference.java in Monai Label QuPath extension
        //ImageData<BufferedImage> imageData = this.qupath.getImageData();

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
}
