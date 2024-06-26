package qupath.lib.extension.cedar;

import javafx.beans.property.SimpleStringProperty;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;

public class CedarAnnotation {
    private SimpleStringProperty className;
    private SimpleStringProperty annotationStyle;
    private SimpleStringProperty metaData;
    // Refer to QuPath PathAnnoation
    private PathObject pathObject;

    public CedarAnnotation() {}

    public String getClassName() {
        return className.get();
    }

    public String getAnnotationStyle() {
        return annotationStyle.get();
    }

    public void setAnnotationStyle(String annotationStyle) {
        if (this.annotationStyle == null)
            this.annotationStyle = new SimpleStringProperty(annotationStyle);
        else
            this.annotationStyle.set(annotationStyle);
    }

    public String getMetaData() {
        return metaData.get();
    }

    public void setMetaData(String metaData) {
        if (this.metaData == null)
            this.metaData = new SimpleStringProperty(metaData);
        else
            this.metaData.set(metaData);
    }

    public void setClassName(String className) {
        if (this.className == null)
            this.className = new SimpleStringProperty(className);
        else
            this.className.set(className);
    }

    public PathObject getPathObject() {
        return pathObject;
    }

    public void setPathObject(PathObject pathObject) {
        this.pathObject = pathObject;
    }
}
