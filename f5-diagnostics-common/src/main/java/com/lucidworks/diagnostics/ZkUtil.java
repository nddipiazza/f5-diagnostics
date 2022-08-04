package com.lucidworks.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetrySleeper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class ZkUtil {
  private static final Logger LOG = LoggerFactory.getLogger(ZkUtil.class);
  public static final String SERVICES_ZOOKEEPER_CONNECT_STRING = "--services.zookeeper.connect-string=";
  private static final String ZK_CONNECTION_STRING_FROM_KUBE = AppYamlReader.getZookeeperConnectionString();
  public static final String DEFAULT_CURATOR_NAMESPACE = "lwfusion/5.0";
  public static final String ADMIN = "admin";
  public static final String INDEXING = "indexing";
  public static final String CONNECTORS = "connectors";
  public static final String SERVICES_INDEXING = "/services/" + INDEXING;
  public static final String SERVICES_ADMIN = "/services/" + ADMIN;
  public static final String SERVICES_CONNECTORS = "/services/" + CONNECTORS;
  public static final int ADMIN_PORT = Integer.parseInt(System.getProperty("services.admin.port", "8765"));
  public static final int INDEXING_PORT = Integer.parseInt(System.getProperty("services.indexing.port", "8765"));

  public static boolean isKubernetes() {
    return ZK_CONNECTION_STRING_FROM_KUBE != null;
  }

  public static Optional<String> getZkChroot() {
    String zkConnectionString = getZkConnectionString();
    if (zkConnectionString != null && zkConnectionString.contains("/")) {
      return Optional.of(zkConnectionString.substring(zkConnectionString.indexOf('/')));
    }
    return Optional.empty();
  }
  public static List<String> getZkHosts() {
    String zkConnectionString = getZkConnectionString();
    if (zkConnectionString == null) {
      return null;
    }
    if (zkConnectionString.contains("/")) {
      zkConnectionString = zkConnectionString.substring(0, zkConnectionString.indexOf('/'));
    }
    return Arrays.asList(zkConnectionString.split(","));
  }

  public static String getZkChrootString() {
    String zkConnectionString = getZkConnectionString();
    if (zkConnectionString != null && zkConnectionString.contains("/")) {
      return zkConnectionString.substring(zkConnectionString.indexOf('/'));
    }
    return null;
  }
  public static String getZkHostsString() {
    String zkConnectionString = getZkConnectionString();
    if (zkConnectionString == null) {
      return null;
    }
    if (zkConnectionString.contains("/")) {
      zkConnectionString = zkConnectionString.substring(0, zkConnectionString.indexOf('/'));
    }
    return zkConnectionString;
  }

  private static String getZkConnectionString() {
    String zkConnectionString = System.getProperty("spring.cloud.zookeeper.connect-string", System.getenv("ZOOKEEPER_CONNECTION_STRING"));
    if (zkConnectionString == null) {
      zkConnectionString = ZK_CONNECTION_STRING_FROM_KUBE;
      if (StringUtils.isNotBlank(zkConnectionString)) {
        LOG.info("Successfully retrieved ZK connection string from configmap application-k8s.yml file and it is: {}", zkConnectionString);
      }
      if (zkConnectionString == null) {
        zkConnectionString = zkConnectionStringWhenRunningInDocker();
        if (StringUtils.isNotBlank(zkConnectionString)) {
          LOG.info("Successfully retrieved the ZK connection string from -Dsun.java.command and it is: {}", zkConnectionString);
        }
      }
    }
    return zkConnectionString;
  }

  private static String zkConnectionStringWhenRunningInDocker() {
    String javaCmd = System.getProperty("sun.java.command");
    if (javaCmd != null && javaCmd.contains(SERVICES_ZOOKEEPER_CONNECT_STRING)) {
      int zkIdx = javaCmd.indexOf(SERVICES_ZOOKEEPER_CONNECT_STRING) + SERVICES_ZOOKEEPER_CONNECT_STRING.length();
      return javaCmd.substring(zkIdx).split(" ")[0];
    }
    return null;
  }

  public static List<String> getFusionServiceEndpoints(String serviceName) {
    ObjectMapper om = new ObjectMapper();
    String connectString = getZkConnectionString();
    List<String> serviceEndpoints = Lists.newArrayList();
    CuratorFrameworkFactory.Builder cfb = CuratorFrameworkFactory.builder()
        .connectString(connectString).sessionTimeoutMs(60000)
        .connectionTimeoutMs(15000).retryPolicy((int retryCount, long elapsedTimeMs, RetrySleeper sleeper) -> retryCount < 5);
    String namespace = StringUtils.defaultIfBlank(System.getenv("CURATOR_NAMESPACE"), DEFAULT_CURATOR_NAMESPACE);
    cfb = cfb.namespace(namespace);
    CuratorFramework curator = cfb.build();
    curator.start();
    try {
      curator.getChildren().forPath(serviceName).forEach(child -> {
        try {
          byte[] data = curator.getData().forPath(serviceName + "/" + child);
          Map indexingService = om.readValue(data, Map.class);
          serviceEndpoints.add(indexingService.get("address") + ":" + indexingService.get("port"));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    } finally {
      curator.close();
    }
    return serviceEndpoints;
  }

  public static void saveDatasourceConfigurationDirectlyToZk(String dsId, Map<String, Object> updates) {
    ObjectMapper om = new ObjectMapper();
    String connectString = getZkConnectionString();
    CuratorFrameworkFactory.Builder cfb = CuratorFrameworkFactory.builder()
        .connectString(connectString).sessionTimeoutMs(60000)
        .connectionTimeoutMs(15000).retryPolicy((int retryCount, long elapsedTimeMs, RetrySleeper sleeper) -> retryCount < 5);
    String namespace = StringUtils.defaultIfBlank(System.getenv("CURATOR_NAMESPACE"), DEFAULT_CURATOR_NAMESPACE);
    cfb = cfb.namespace(namespace);
    CuratorFramework curator = cfb.build();
    curator.start();

    try {
      byte[] data = curator.getData().forPath(SERVICES_CONNECTORS + "/configs/" + dsId);
      Map ds = om.readValue(data, Map.class);
      Map prop = (Map) ds.get("properties");
      for (String path : updates.keySet()) {
        String [] pathSpl = path.split("\\.");
        for (int i = 0; i < pathSpl.length - 1; ++i) {
          prop = (Map) prop.get(pathSpl[i]);
        }
        prop.put(pathSpl[pathSpl.length - 1], updates.get(path));
      }

      curator.setData().forPath(SERVICES_CONNECTORS + "/configs/" + dsId, om.writeValueAsBytes(ds));

    } catch (Exception exception) {
      throw new RuntimeException(exception);
    } finally {
      curator.close();
    }
  }

  public static List<String> getIndexingEndpoints() {
    if (!isKubernetes()) {
      try {
        return getFusionServiceEndpoints(SERVICES_INDEXING);
      } catch (Exception e) {
        LOG.error("Could not get host info from zk in {}. Will fallback to default", SERVICES_INDEXING, e);
      }
    }
    return Collections.singletonList(INDEXING + ":" + INDEXING_PORT);
  }

  public static List<String> getAdminEndpoints() {
    if (!isKubernetes()) {
      try {
        return getFusionServiceEndpoints(SERVICES_ADMIN);
      } catch (Exception e) {
        LOG.error("Could not get host info from zk in {}. Will fallback to default", SERVICES_ADMIN, e);
      }
    }
    return Collections.singletonList(ADMIN + ":" + ADMIN_PORT);
  }

  public static String getAdminEndpoint() {
    List<String> fusionServiceEndpoints = getAdminEndpoints();
    return fusionServiceEndpoints.get(new Random().nextInt(fusionServiceEndpoints.size()));
  }

  public static String getIndexingEndpoint() {
    List<String> fusionServiceEndpoints = getIndexingEndpoints();
    return fusionServiceEndpoints.get(new Random().nextInt(fusionServiceEndpoints.size()));
  }
}
