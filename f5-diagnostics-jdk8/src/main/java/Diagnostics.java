import com.lucidworks.diagnostics.DiagnosticsBase;
import org.apache.commons.lang3.NotImplementedException;
import org.kohsuke.args4j.CmdLineParser;

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
    throw new NotImplementedException();
  }
}
