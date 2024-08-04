package qupath.lib.extension.cedar;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

public class CedarAnnotation {
    private SimpleIntegerProperty classId;
    private SimpleStringProperty className;
    private SimpleStringProperty annotationStyle;
    private SimpleStringProperty metaData;
    // Refer to QuPath PathAnnoation
    private PathObject pathObject;

    public CedarAnnotation() {}

    public Integer getClassId() {
        return classId.get();
    }

    public String getClassName() {
        return this.className.get();
    }

    public void setClassName(String name) {
        if (this.className == null)
            this.className = new SimpleStringProperty(name);
        else
            this.className.set(name);
        if (pathObject != null)
            pathObject.setPathClass(PathClass.fromString(name));
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

    public void setClassId(Integer id) {
        if (this.classId == null)
            this.classId = new SimpleIntegerProperty(id);
        else
            this.classId.set(id);
    }

    public PathObject getPathObject() {
        return pathObject;
    }

    public void setPathObject(PathObject pathObject) {
        this.pathObject = pathObject;
    }
}
