package qupath.lib.extension.cedar;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import qupath.lib.extension.cedar.ActionLogging.CedarExtensionAction;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;

@Aspect
public class LoggingAspect {
    // TODO: make the filePathName configurable
    String filePathName = "/Users/beaversd/IdeaProjects/qupath_monaillabel_plugin/qupath-extension-cedar/src/main/tracking/CedarExtensionLogging" + LocalDateTime.now() + ".csv";
    //static int id = 0; // tracking the id to further annotate
    static HashMap<Integer, CedarExtensionAction> trackedAnnotations = new HashMap<>();

    @Pointcut("@annotation(qupath.lib.extension.cedar.Tracking)")
    public void annotated() {}

    // Keep a stack of the annotations as objects. Can get an object using key or id and further
    // add the information that cannot passed at method execution.

    @Around("annotated()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String start = Long.toString(System.currentTimeMillis());
        String methodName = joinPoint.getSignature().getName();
        String action = "";
        Object proceed = joinPoint.proceed(); // execute method

        try {
            Class<?> clazz = CedarExtensionView.class;
            // Get the method
            Method method = clazz.getDeclaredMethod(methodName, File.class);

            // Make the method accessible if private
            method.setAccessible(true);

            if (method.isAnnotationPresent(Tracking.class)) {
                Tracking annotation = method.getAnnotation(Tracking.class);
                action = annotation.action();
                String endTime = Long.toString(System.currentTimeMillis());
                CedarExtensionAction cedarExtensionAction = new CedarExtensionAction(action);
                CedarExtensionView.addCedarAnnotationTracking(cedarExtensionAction);
                System.out.println("Annotation value: " + annotation.action());
            } else {
                System.out.println("Annotation not found.");
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        //trackedAnnotations.put(cedarExtensionAction);
        //if(trackedAnnotations.size() > 100){
            writeToFile(trackedAnnotations.values());
        //}
        //id++;
        return proceed;
    }

    private void writeToFile(Collection<CedarExtensionAction> cedarExtensionActions) throws Throwable {
        for(CedarExtensionAction cedarExtensionAction : cedarExtensionActions){
            String data = String.join(",",
                    cedarExtensionAction.getAction(),
                    cedarExtensionAction.getStartTime(),
                    cedarExtensionAction.getEndTime());
            // TODO: improve with a queue
            File csvOutputFile = new File(filePathName);
            try (FileWriter fileWriter = new FileWriter(csvOutputFile, true)) {
                fileWriter.write(data);
            }
        }
        // clear the cache once the annotations have been tracked
        trackedAnnotations.clear();
    }

//    public static CedarExtensionAction getTrackedAnnotation(int id){
//        return trackedAnnotations.get(id);
//    }
//
//    public static int getId(){
//        return id;
//    }
}