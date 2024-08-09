package qupath.lib.extension.cedar;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

public class CedarAnnotation {
    // Make sure classid and className are consistent
    private SimpleIntegerProperty classId;
    private ObjectProperty<AnnotationType> annotationStyle;
    private SimpleStringProperty metaData;
    // Refer to QuPath PathAnnoation
    private PathObject pathObject;

    public CedarAnnotation() {}

    public Integer getClassId() {
        return classId.get();
    }

    public String getClassName() {
        if (pathObject == null)
            return "unknown";
        return this.pathObject.getPathClass().getName();
    }

    public void setClassName(String name) {
        // Don't want to handle these two cases
        if (pathObject == null || classId == null)
            return;
        pathObject.setPathClass(PathClass.fromString(name));
        Integer id = CedarPathClassHandler.getHandler().getClassId(name);
        if (this.classId.get() == id)
            return;
        this.classId.set(id); // Don't call set method to avoid a circular call.
    }

    public AnnotationType getAnnotationStyle() {
        return annotationStyle.get();
    }

    public void setAnnotationStyle(String annotationStyle) {
        if (this.annotationStyle == null)
            this.annotationStyle = new SimpleObjectProperty<>(AnnotationType.valueOf(annotationStyle));
        else
            this.annotationStyle.set(AnnotationType.valueOf(annotationStyle));
    }

    public void setAnnotationStyle(AnnotationType annotationStyle) {
        if (this.annotationStyle == null)
            this.annotationStyle = new SimpleObjectProperty<>(annotationStyle);
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
        if (this.classId != null && this.classId.get() == id)
            return;
        if (this.classId == null)
            this.classId = new SimpleIntegerProperty(id);
        else
            this.classId.set(id);
        if (pathObject != null)
            pathObject.setPathClass(CedarPathClassHandler.getHandler().getPathClass(id));
    }

    public PathObject getPathObject() {
        return pathObject;
    }

    public void setPathObject(PathObject pathObject) {
        this.pathObject = pathObject;
    }
}
