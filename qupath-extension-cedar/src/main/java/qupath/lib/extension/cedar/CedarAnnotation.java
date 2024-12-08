package qupath.lib.extension.cedar;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * A wrapper of PathObject so that it can be easily displayed in a JavaFX table view.
 * TODO: We may use PathObject in the future directly.
 */
public class CedarAnnotation {
    // Keys in meta map for PathObject
    private final String ANNOTATION_TYPE_KEY = "anno_style";
    private final String CLASS_ID_KEY = "class_id";
    private final String META_KEY = "ANNOTATION_DESCRIPTION";
    private final String NEW_OBJECT_DESC = "manually created";
    // Refer to QuPath PathAnnotation
    private PathObject pathObject;

    public CedarAnnotation() {}

    public CedarAnnotation(PathObject pathObject) {
        this.pathObject = pathObject;
    }

    public String toTrackingString() {
        return getPathObject().getID() + ": " + getClassId() + "," + getClassName() + "," + getAnnotationStyle();
    }

    public Integer getClassId() {
        if (pathObject == null || pathObject.getMetadata() == null)
            return -1;
        String id = this.pathObject.getMetadata().get(CLASS_ID_KEY);
        if (id == null)
            return -1;
        return Integer.parseInt(id);
    }

    public String getClassName() {
        if (pathObject == null)
            return "unknown";
        return this.pathObject.getPathClass().getName();
    }

    private PathObject initPathObject() {
        PathAnnotationObject pathObject = new PathAnnotationObject();
        pathObject.setDescription(NEW_OBJECT_DESC);
        return pathObject;
    }

    public void setClassName(String name) {
        // Don't want to handle these two cases
        if (pathObject == null) {
            pathObject = initPathObject();
        }
        if (pathObject.getPathClass().getName().equals(name))
            return;
        pathObject.setPathClass(PathClass.fromString(name));
        Integer id = CedarPathClassHandler.getHandler().getClassId(name);
        setClassId(id);
    }

    public AnnotationType getAnnotationStyle() {
        if (pathObject == null || pathObject.getMetadata() == null)
            return AnnotationType.auto; // Use this as the default
        String style = pathObject.getMetadata().get(ANNOTATION_TYPE_KEY);
        if (style == null)
            return AnnotationType.auto;
        return AnnotationType.valueOf(style);
    }

    public void setAnnotationStyle(String annotationStyle) {
        if (pathObject == null) {
            pathObject = new PathAnnotationObject();
            // Call this to create a meta map so that we can add
            ((PathAnnotationObject)pathObject).setDescription(NEW_OBJECT_DESC);
        }
        pathObject.getMetadata().put(ANNOTATION_TYPE_KEY, annotationStyle);
    }

    public void setAnnotationStyle(AnnotationType annotationStyle) {
        this.setAnnotationStyle(annotationStyle.toString());
    }

    public String getMetaData() {
        if (pathObject != null && pathObject.getMetadata() != null)
            return pathObject.getMetadata().get(META_KEY);
        return null;
    }

    public void setMetaData(String metaData) {
        if (pathObject == null) {
            pathObject = initPathObject();
        }
        pathObject.getMetadata().put(META_KEY, metaData);
    }

    public void setClassId(Integer id) {
        if (pathObject == null) {
            pathObject = initPathObject();
        }
        String oldId = pathObject.getMetadata().get(CLASS_ID_KEY);
        if (oldId != null && oldId.equals(id.toString()))
            return;
        pathObject.getMetadata().put(CLASS_ID_KEY, id + "");
        pathObject.setPathClass(CedarPathClassHandler.getHandler().getPathClass(id));
    }

    public PathObject getPathObject() {
        return pathObject;
    }

    public void setPathObject(PathObject pathObject) {
        this.pathObject = pathObject;
    }
}
