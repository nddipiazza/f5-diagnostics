import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Diagnostics {
  @Option(name = "-command", usage = "Command to run.")
  private String command;

  @Option(name = "-filter", usage = "Filter by")
  private String filter;

  @Option(name = "-inFile", usage = "Input files")
  private List<String> inFiles;

  @Option(name = "-host", usage = "The host to test when doing testConnect")
  private String host;

  @Option(name = "-port", usage = "The Port to test when doing testConnect")
  private Integer port;

  @Option(name = "-url", usage = "The url to test when doing curl")
  private String url;

  @Option(name = "-timeout", usage = "Timeout to wait in millis before timeout")
  private int timeout = 60000;

  @Option(name = "-datasourceId", usage = "ID of a connectors data source")
  private String datasourceId;

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
        File file = new File(inFile);
        if (!file.exists()) {
          System.out.println("File does not exist: " + file.getAbsolutePath());
          return;
        }
        if (!file.isDirectory()) {
          System.out.println("File is not directory: " + file.getAbsolutePath());
          return;
        }
        File[] files = file.listFiles();
        if (files == null) {
          System.out.println("Could not list files for: " + file.getAbsolutePath());
          return;
        }
        for (File innerFile : files) {
          System.out.println("  --- " + innerFile.getCanonicalPath());
        }
      }
    } else if ("rm".equals(command)) {
      inFiles.
          stream()
          .map(File::new)
          .forEach(FileUtils::deleteQuietly);
    } else if ("rmdir".equals(command)) {
      inFiles.
          stream()
          .map(File::new)
          .forEach(f -> {
            try {
              FileUtils.deleteDirectory(f);
            } catch (IOException e) {
              System.out.println("Could not delete dir " + f.getAbsolutePath());
              e.printStackTrace();
            }
          });
    } else if ("testConnect".equals(command)) {
      pingHost(host, port, timeout);
    } else if ("curl".equals(command)) {
      httpRequest(url);
    } else if ("pulsarStats".equals(command)) {
      try (PulsarAdmin admin = PulsarAdmin.builder()
          .serviceHttpUrl(url)
          .build()) {
        for (String tenant : admin.tenants().getTenants()) {
          System.out.println("Tenant: " + tenant);
          for (String namespace : admin.namespaces().getNamespaces(tenant)) {
            System.out.println("  Namespace: " + namespace);
            for (String topic : admin.topics().getList(namespace)) {
              System.out.println("    Topic: " + topic);
              System.out.println("           " + admin.topics().getStats(topic, true));
              for (String subscription : admin.topics().getSubscriptions(topic)) {
                System.out.println("      Subscription: " + subscription);
              }
            }
          }
        }
      }
    } else if ("deleteOrphanedConnectorsBackendSubscriptions".equals(command)) {
      try (PulsarAdmin admin = PulsarAdmin.builder()
          .serviceHttpUrl(url)
          .build()) {
         for (String tenant : admin.tenants().getTenants()) {
          System.out.println("Tenant: " + tenant);
          for (String namespace : admin.namespaces().getNamespaces(tenant)) {
            if (namespace.equals(tenant + "/_connectors")) {
              System.out.println("  Namespace: " + namespace);
              for (String topic : admin.topics().getList(namespace)) {
                if (topic.startsWith("persistent://" + tenant + "/_connectors/datasource-" + datasourceId)) {
                  System.out.println("    Topic: " + topic);
                  System.out.println("           " + admin.topics().getStats(topic, true));
                  for (String subscription : admin.topics().getSubscriptions(topic)) {
                    System.out.println("      Subscription: " + subscription);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  public static void pingHost(String host, int port, int timeout) throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeout);
      System.out.println("Successfully connected to " + host + ":" + port);
    }
  }

  public static void httpRequest(String url) throws Exception {
    CloseableHttpResponse response = HttpClients.createDefault().execute(new HttpGet(url));
    System.out.println("url: " + url);
    System.out.println("status: " + response.getStatusLine());
    System.out.println("headers: ");
    if (response.getAllHeaders() != null) {
      Stream.of(response.getAllHeaders()).forEach(h -> System.out.println("  --- " + h.getName() + " = " + h.getValue()));
    }
    System.out.println("Response: " + IOUtils.toString(response.getEntity().getContent()));
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
