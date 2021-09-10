import com.lucidworks.diagnostics.DiagnosticsBase;
import org.kohsuke.args4j.CmdLineParser;

import java.util.Optional;

public class Diagnostics extends DiagnosticsBase {

  public static void main(String[] args) throws Exception {
    Diagnostics diagnostics = new Diagnostics();
    CmdLineParser parser = new CmdLineParser(diagnostics);
    parser.parseArgument(args);
    if (diagnostics.command == null) {
      parser.printUsage(System.out);
    }
    diagnostics.run();
  }

  @Override
  protected void ps() {
    ProcessHandle.allProcesses()
        .map(Diagnostics::processDetails)
        .filter(processStr -> filter == null || filter.trim().equals("") || processStr.contains(filter))
        .forEach(System.out::println);
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
