import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class Diagnostics {
  @Option(name = "-command", usage = "Command to run.")
  private String command;

  @Option(name = "-filter", usage = "Filter by")
  private String filter;

  @Option(name = "-inFile", usage = "Input files")
  private List<String> inFiles;

  public static void main(String[] args) throws Exception {
    Diagnostics diagnostics = new Diagnostics();
    CmdLineParser parser = new CmdLineParser(diagnostics);
    parser.parseArgument(args);
    if (diagnostics.command == null) {
      parser.printUsage(System.out);
    }
    diagnostics.run();
  }

  public void run() throws Exception {
    if ("ps".equals(command)) {
      ProcessHandle.allProcesses()
          .map(Diagnostics::processDetails)
          .filter(processStr -> filter == null || filter.trim().equals("") || processStr.contains(filter))
          .forEach(System.out::println);
    } else if ("cat".equals(command)) {
      for (String inFile : inFiles) {
        System.out.println(inFile + ":");
        System.out.println(FileUtils.readFileToString(new File(inFile)));
      }
    } else if ("ls".equals(command)) {
      for (String inFile : inFiles) {
        System.out.println(inFile + ":");
        for (File innerFile : new File(inFile).listFiles()) {
          System.out.println("  --- " + innerFile.getCanonicalPath());
        }
      }
    }

  }

  private static String processDetails(ProcessHandle process) {
    return String.format("%8d %8s %10s %26s %-40s",
        process.pid(),
        text(process.parent().map(ProcessHandle::pid)),
        text(process.info().user()),
        text(process.info().startInstant()),
        text(process.info().commandLine()));
  }

  private static String text(Optional<?> optional) {
    return optional.map(Object::toString).orElse("-");
  }
}
