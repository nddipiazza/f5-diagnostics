import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class RunWithRollingLogger {
  static final Logger logger = LogManager.getLogger(RunWithRollingLogger.class);
  private static class StreamGobbler extends Thread {
    private InputStream in;

    private StreamGobbler(InputStream in) {
      this.in = in;
    }

    @Override
    public void run() {
      try {
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = input.readLine()) != null)
          logger.info(line);
      } catch (IOException e) {
        logger.error("Could not tail output", e);
      }
    }
  }
  public static void main(String[] args) throws Exception {
    Process p = new ProcessBuilder(Arrays.asList(args))
        .start();
    StreamGobbler pOut = new StreamGobbler(p.getInputStream());
    StreamGobbler pErr = new StreamGobbler(p.getErrorStream());
    pOut.start();
    pErr.start();
  }
}
