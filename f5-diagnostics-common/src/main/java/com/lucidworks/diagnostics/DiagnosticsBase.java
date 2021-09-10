package com.lucidworks.diagnostics;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Scale;
import io.kubernetes.client.util.Config;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Stream;

public abstract class DiagnosticsBase {
  @Option(name = "-command", usage = "Command to run.")
  protected String command;

  @Option(name = "-filter", usage = "Filter by")
  protected String filter;

  @Option(name = "-inFile", usage = "Input files")
  protected List<String> inFiles;

  @Option(name = "-host", usage = "The host to test when doing testConnect")
  protected String host;

  @Option(name = "-port", usage = "The Port to test when doing testConnect")
  protected Integer port;

  @Option(name = "-url", usage = "The url to test when doing curl")
  protected String url;

  @Option(name = "-timeout", usage = "Timeout to wait in millis before timeout")
  protected int timeout = 60000;

  @Option(name = "-datasourceId", usage = "ID of a connectors data source")
  protected String datasourceId;

  @Option(name = "-namespace", usage = "Kube namespace")
  protected String namespace;

  @Option(name = "-label", usage = "Kube label")
  protected String label;

  @Option(name = "-q", usage = "Solr query")
  protected String q;

  @Option(name = "-collection", usage = "Solr collection")
  protected String collection;

  @Option(name = "-rows", usage = "Rows")
  protected int rows;

  @Option(name = "-zkHosts", usage = "zkHosts string (comma sep)")
  protected String zkHosts;

  @Option(name = "-zkChroot", usage = "zkChroot")
  protected String zkChroot;


  public void run() throws Exception {
    if ("ps".equals(command)) {
      ps();
    } else if ("solrQuery".equals(command)) {
      solrQuery();
    } else if ("cat".equals(command)) {
      cat();
    } else if ("base64".equals(command)) {
      base64();
    } else if ("ls".equals(command)) {
      ls();
    } else if ("df".equals(command)) {
      df();
    } else if ("rm".equals(command)) {
      rm();
    } else if ("rmdir".equals(command)) {
      rmdir();
    } else if ("testConnect".equals(command)) {
      testConnect();
    } else if ("curl".equals(command)) {
      curl();
    } else if ("pulsarStats".equals(command)) {
      pulsarStats();
    } else if ("indexingTopicStats".equals(command)) {
      indexingTopicStats();
    } else if ("replicaCount".equals(command)) {
      replicaCount();
    } else if ("deleteOrphanedConnectorsBackendSubscriptions".equals(command)) {
      deleteOrphanedConnectorsBackendSubscriptions();
    }
  }

  private void solrQuery() throws Exception {
    try (SolrClient client = new CloudSolrClient.Builder(Arrays.asList(zkHosts.split(",")), Optional.of(zkChroot)).build()) {
      SolrQuery query = new SolrQuery(q);
      query.setRows(rows);
      QueryResponse response = client.query(collection, query);
      System.out.println(response);
    }
  }

  abstract protected void ps();

  protected void deleteOrphanedConnectorsBackendSubscriptions() throws IOException, ApiException, PulsarAdminException {
    ApiClient client = Config.defaultClient();
    Configuration.setDefaultApiClient(client);

    CoreV1Api api = new CoreV1Api();
    V1PodList list = api.listNamespacedPod(namespace, null, null, null, null, label, null, null, null, 50, false);
    Set<String> backendPodNames = new HashSet<>();
    for (V1Pod item : list.getItems()) {
      String podName = item.getMetadata().getName();
      if (StringUtils.startsWith(podName, namespace + "connectors-backend-")) {
        System.out.println("Backend pod " + podName);
        backendPodNames.add(podName);
      }
    }
    try (PulsarAdmin admin = PulsarAdmin.builder()
        .serviceHttpUrl(url)
        .build()) {
      for (String tenant : admin.tenants().getTenants()) {
        System.out.println("Tenant: " + tenant);
        for (String namespace : admin.namespaces().getNamespaces(tenant)) {
          if (namespace.equals(tenant + "/_connectors")) {
            System.out.println("  Namespace: " + namespace);
            for (String topic : admin.topics().getList(namespace)) {
//                System.out.println("    Topic: " + topic);
//                  System.out.println("           " + admin.topics().getStats(topic, true));
              for (String subscription : admin.topics().getSubscriptions(topic)) {
                if (StringUtils.contains(subscription, datasourceId)) {
                  System.out.println("      Subscription: " + subscription);
                }
              }
            }
          }
        }
      }
    }
  }

  protected void replicaCount() throws IOException {
    ApiClient client = Config.defaultClient();
    Configuration.setDefaultApiClient(client);

    AppsV1Api api = new AppsV1Api(client);
    V1Scale scale = null;
    try {
      scale = api.readNamespacedStatefulSetScale("tika-async", namespace, null);
    } catch (ApiException e) {
      e.printStackTrace();
      System.out.println("Error body: " + e.getResponseBody());
      System.out.println("Error code: " + e.getCode());
      System.out.println("Error response headers: " + e.getResponseHeaders());
      return;
    }

    System.out.println("Scale: " + scale);
    System.out.println("no of replicas is :  " + scale.getSpec().getReplicas());
  }

  protected void indexingTopicStats() throws PulsarAdminException, PulsarClientException {
    try (PulsarAdmin admin = PulsarAdmin.builder()
        .serviceHttpUrl(url)
        .build()) {
      for (String tenant : admin.tenants().getTenants()) {
        System.out.println("Tenant: " + tenant);
        for (String namespace : admin.namespaces().getNamespaces(tenant)) {
          System.out.println("  Namespace: " + namespace);
          for (String topic : admin.topics().getList(namespace)) {
            if (StringUtils.contains(topic, datasourceId)) {
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

  protected void pulsarStats() throws PulsarAdminException, PulsarClientException {
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
  }

  protected void curl() throws Exception {
    httpRequest(url);
  }

  protected void testConnect() throws Exception {
    pingHost(host, port, timeout);
  }

  protected void rmdir() {
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
  }

  protected void rm() {
    inFiles.
        stream()
        .map(File::new)
        .forEach(FileUtils::deleteQuietly);
  }

  protected void df() {
    NumberFormat nf = NumberFormat.getNumberInstance();
    for (Path root : FileSystems.getDefault().getRootDirectories()) {

      System.out.print(root + ": ");
      try {
        FileStore store = Files.getFileStore(root);
        System.out.println("available=" + nf.format(store.getUsableSpace())
            + ", total=" + nf.format(store.getTotalSpace()));
      } catch (IOException e) {
        System.out.println("error querying space: " + e.toString());
      }
    }
  }

  protected void ls() throws IOException {
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
        if (innerFile.isFile()) {
          System.out.println(" t=f,sz=" + innerFile.length() + " -- " + innerFile.getCanonicalPath());
        } else {
          System.out.println(" t=d -- " + innerFile.getCanonicalPath());
        }
      }
    }
  }

  protected void base64() throws IOException {
    for (String inFile : inFiles) {
      System.out.println(inFile + ":");
      System.out.println(Base64.getEncoder().encodeToString(FileUtils.readFileToString(new File(inFile)).getBytes()));
    }
  }

  protected void cat() throws IOException {
    for (String inFile : inFiles) {
      System.out.println(inFile + ":");
      System.out.println(FileUtils.readFileToString(new File(inFile)));
    }
  }

  public static void pingHost(String host, int port, int timeout) throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeout);
      System.out.println("Successfully connected to " + host + ":" + port);
    }
  }

  public static void httpRequest(String url) throws Exception {
    SSLContext sslContext = SSLContexts.createSystemDefault();
    // CONN-664: allowAllCertificates logic
    if (true) {
      try {
        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[]{
            new X509ExtendedTrustManager() {
              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return null;
              }

              @Override
              public void checkClientTrusted(X509Certificate[] certs, String authType) {
              }

              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
              }

              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
              }

              @Override
              public void checkServerTrusted(X509Certificate[] certs, String authType) {
              }

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
              }

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
              }
            }}, null);
      } catch (KeyManagementException | NoSuchAlgorithmException e) {
        // XXX: BDW: Need to log something or something.
        //LOG.warn("Failed to create an allow-all TrustManager, using the system default!", e);
        sslContext = SSLContexts.createSystemDefault();
      }
    }

    HttpClientBuilder httpClientBuilder = HttpClients.custom();

    httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom()
        .setConnectionRequestTimeout(10000)
        .setConnectTimeout(30000)
        .setSocketTimeout(-1)
        .build());

    CloseableHttpClient httpClient = httpClientBuilder.setSSLContext(sslContext).build();

    CloseableHttpResponse response = httpClient.execute(new HttpGet(url));

    System.out.println("url: " + url);
    System.out.println("status: " + response.getStatusLine());
    System.out.println("headers: ");
    if (response.getAllHeaders() != null) {
      Stream.of(response.getAllHeaders()).forEach(h -> System.out.println("  --- " + h.getName() + " = " + h.getValue()));
    }
    System.out.println("Response: " + IOUtils.toString(response.getEntity().getContent()));
  }
}
