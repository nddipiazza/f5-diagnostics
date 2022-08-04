package com.lucidworks.diagnostics;

import com.google.common.annotations.VisibleForTesting;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.Map;

public class AppYamlReader {
  private static final File APP_K8S_FILE = new File("/app/config/application-k8s.yml");

  public static String getZookeeperConnectionString() {
    return getZookeeperConnectionStringImpl(APP_K8S_FILE);
  }

  @VisibleForTesting
  static String getZookeeperConnectionStringImpl(File file) {
    if (!file.exists()) {
      return null;
    }
    try (FileReader fr = new FileReader(file)) {
      Yaml yaml = new Yaml();
      Map<String, Object> obj = yaml.load(fr);
      Map<String, Object> services = (Map<String, Object>) obj.get("services");
      Map<String, Object> zookeeper = (Map<String, Object>) services.get("zookeeper");
      return (String) zookeeper.get("connect-string");
    } catch (Exception e) {
      return null;
    }
  }
}
