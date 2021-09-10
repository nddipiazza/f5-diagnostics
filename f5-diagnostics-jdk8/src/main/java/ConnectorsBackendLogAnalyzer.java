import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

public class ConnectorsBackendLogAnalyzer {
  public static void main(String[] args) throws Exception {
    Set<String> idsOnQueue = new HashSet<>();
    String incPref = "pendingFetchCalls counter incremented - fetchInput.id=";
    String decPref = "pendingFetchCalls counter decremented for fetchInput.id=";
    File logDir = new File("C:\\Users\\ndipiaz\\Downloads\\backend-logs\\backend-logs");
    for (File logFile : logDir.listFiles()) {
      System.out.println("Next file: " + logFile.getAbsolutePath());
      if (logFile.isFile()) {
        LineIterator lineIterator = new LineIterator(new FileReader(logFile));
        while (lineIterator.hasNext()) {
          String nextLine = lineIterator.nextLine();
          int incPrefixIdx = nextLine.indexOf(incPref);
          if (incPrefixIdx != -1) {
            String id = nextLine.substring(incPrefixIdx + incPref.length()).split(",")[0];
            idsOnQueue.add(id);
          } else {
            int decPrefixIdx = nextLine.indexOf(decPref);
            if (decPrefixIdx != -1) {
              String id = nextLine.substring(decPrefixIdx + decPref.length()).split(",")[0];
              idsOnQueue.remove(id);
            }
          }
        }
      }
    }
    System.out.println("Stuck on queue: ");
    for (String idOnQueue : idsOnQueue) {
      System.out.println(idOnQueue);
    }
  }
}
