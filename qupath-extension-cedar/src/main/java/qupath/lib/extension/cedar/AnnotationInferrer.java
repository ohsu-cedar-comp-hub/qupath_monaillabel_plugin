package qupath.lib.extension.cedar;

import com.google.gson.Gson;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.extension.monailabel.MonaiLabelClient;
import qupath.lib.extension.monailabel.RequestUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to infer annotations (tissue segmentation) using a backend monailabel Python app.
 * The implementation of this class is based on the QuPath monailabel code.
 */
public class AnnotationInferrer {
    // This is hard-coded and will be updated when more models are available
    private final String MODEL = "segmentation_tissue";
    private final Logger logger = LoggerFactory.getLogger((AnnotationInferrer.class));

    public AnnotationInferrer() {
    }

    public void infer(File imageFile, File annotationFolder, CedarExtensionView extension) {
        ProgressIndicator progressIndicator = new ProgressIndicator(-1);
        // Wrap the progress indicator in a layout if needed
        StackPane root = new StackPane(progressIndicator);
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75);"); // Half transparent
        Scene previousScene = extension.getQupath().getStage().getScene();
        Scene scene = new Scene(root, previousScene.getWidth(), previousScene.getHeight());
        scene.setFill(Color.TRANSPARENT);

        // Create the transparent stage to show the progress
        Stage dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.APPLICATION_MODAL); // Block interaction with other windows
        dialogStage.initOwner(extension.getQupath().getStage()); // Set the owner for modality
        dialogStage.setScene(scene);

        // The location of dialogState may be offset a little bit. Don't adjust anything
        // here to avoid even weirder behavior!!!
//        Stage primaryStage = extension.getQupath().getStage();
//        dialogStage.setX(primaryStage.getX());
//        dialogStage.setY(primaryStage.getY());

        dialogStage.show();

        Task<Void> inferTask = createInferTask(imageFile, annotationFolder, extension);
        // Handle task completion: need to call here as the main JavaFX thread
        inferTask.setOnSucceeded(event -> {
            dialogStage.close();
        });

        inferTask.setOnFailed(event -> {
            dialogStage.close();
        });

        new Thread(inferTask).start();
    }

    private Task<Void> createInferTask(File imageFile, File annotationFolder, CedarExtensionView extension) {
        return new Task<>() {
            @Override
            protected Void call() {
                _infer(imageFile, annotationFolder, extension);
                return null;
            }
        };
    }

    private void _infer(File imageFile, File annotationFolder, CedarExtensionView extension)  {
        try {
            logger.info("Infer annotation for " + imageFile);
            // The following code is based on MonaiLabelClient.java in qupath.lib.extension.monailabel
            String model = MODEL;
            String uri = "/infer/wsi_v2/" + URLEncoder.encode(model, "UTF-8") + "?output=asap";
            uri += "&image=" + URLEncoder.encode(imageFile.getName(), "UTF-8");

            // Pass parameters using RequestInfer
            RequestParams params = new RequestParams();
            params.setParam("src_image_dir", imageFile.getParentFile().getAbsolutePath());
            params.setParam("src_image_file", imageFile.getName());
            params.setParam("annotation_dir", annotationFolder.getAbsolutePath());

            String jsonBody = new Gson().toJson(params, RequestParams.class);
            // Somehow required by the server-side
            logger.info("MONAILabel:: Request BODY => " + jsonBody);
            // To use the existing APIs, follow the multi-part for the time being
            // TODO: Use our own implementation to intiaize a simple HTTP call
            Map<String, File> files = new HashMap<>();
            Map<String, String> filelds = new HashMap<>();
            filelds.put("wsi", jsonBody);

            String response = RequestUtils.requestMultiPart("POST", uri, files, filelds);

            // Save the file
            String fileName = imageFile.getName();
            int lastIndex = fileName.lastIndexOf(".");
            fileName = fileName.substring(0, lastIndex);
            File annotationFile = new File(annotationFolder, fileName + ".geojson");
            // Consider using a buffer writer
            FileWriter fileWriter = new FileWriter(annotationFile);
            fileWriter.write(response);
            fileWriter.flush();
            fileWriter.close();

            extension.parseAnnotationFile(annotationFile);
        }
        catch(Exception e) {
            Dialogs.showErrorMessage("Error in Annotation Inference",
                    "Error in inferring annotations for : " + imageFile.getName());
            logger.error("Cannot infer annotations for: " + imageFile.getAbsolutePath(), e);
        }
    }

    // A simple model for converting to json parameters
    private class RequestParams {
        HashMap<String, String> params = new HashMap<>();

        public void setParam(String name, String value) {
            params.put(name, value);
        }
    }

}
