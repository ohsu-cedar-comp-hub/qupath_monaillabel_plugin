//package qupath.lib.extension.cedar.ActionLogging;
//
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.PrintWriter;
//
//public class QPathActionTracking implements QPathActionTrackingInterface {
//   // @Override
//    public static void startRecordAction(CedarExtensionAction data) {
//        System.out.println("testing record Action");
//        data.setStartTime(System.nanoTime());
//    }
//
//    public void stopRecordAction(CedarExtensionAction data) throws FileNotFoundException {
//        data.setEndTime(System.nanoTime());
//        wrtieCSV(data);
//    }
//
//    private String convertToCSV(CedarExtensionAction data) {
//        long timeElapsed = data.getEndTime() - data.getStartTime();
//        return String.join(",", data.getAction(), Long.toString(timeElapsed));
//    }
//
//    private void wrtieCSV(CedarExtensionAction data) throws FileNotFoundException {
//        String filePathName = "CedarExtensionLogging.csv";
//        File csvOutputFile = new File(filePathName);
//        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
//          this.convertToCSV(data);
//        }
//    }
//}
