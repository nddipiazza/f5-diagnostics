import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jose4j.base64url.Base64;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class F5JobRunLogDumpOnFail {
  public static String fusion_host = "fusionadmin.na.xom.com";
  public static String app_name = "Enterprise_Search";
  public static String fusion_username = "admin";
  public static String fusion_password = "Cl9jVfOrfONQEuKfIWn5";

  public static ObjectMapper OM = new ObjectMapper();

  public static void main(String [] args) throws Exception {
    List<String> dsList = Arrays.asList(args);
    for (String ds : dsList) {
      System.out.println("Ds ID: " + ds);

      String encoding = Base64.encode((fusion_username + ":" + fusion_password).getBytes());
      String endpoint = "https://" + fusion_host + "/api";
      runJob(ds, encoding, endpoint);

      String status = "running";
      while ("running".equals(status)) {
        status = jobStatus(ds, encoding, endpoint);
        if ("failed".equals(status)) {
          System.out.println(ds + " = FAILED! STOPPING!");
          System.exit(1);
        }
        System.out.println(ds + " = " + status);
        Thread.sleep(30000);
      }
    }
  }

  @NotNull
  private static void runJob(String ds, String encoding, String endpoint) throws Exception {
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      String jobStartEndpoint = String.format("%s/apps/%s/jobs/datasource:%s/actions", endpoint, app_name, ds);
      HttpPost httpPost = new HttpPost(jobStartEndpoint);
      httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
      httpPost.setHeader("Content-Type", "application/json");

      httpPost.setEntity(new StringEntity("{\n" +
          "                                           \"action\": \"start\",\n" +
          "                                           \"comment\": \"started from java program\"\n" +
          "                                       }"));

      try (CloseableHttpResponse resp = client.execute(httpPost)) {
        if (resp.getStatusLine().getStatusCode() != 200) {
          throw new Exception("Failed " + jobStartEndpoint + " " + resp.getStatusLine());
        }
      }
    }
  }

  @NotNull
  private static String jobStatus(String ds, String encoding, String endpoint) throws Exception {
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      String jobStatusEndpoint = String.format("%s/jobs", endpoint);
      HttpGet httpGet = new HttpGet(jobStatusEndpoint);
      httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
      httpGet.setHeader("Content-Type", "application/json");

      try (CloseableHttpResponse resp = client.execute(httpGet)) {
        if (resp.getStatusLine().getStatusCode() != 200) {
          throw new Exception("Failed " + jobStatusEndpoint + " " + resp.getStatusLine());
        }
        if (resp.getStatusLine().getStatusCode() == 504) {
          System.out.println("Job status timeout!");
          return "running";
        }
        List<Map<String, Object>> statuses = OM.readValue(resp.getEntity().getContent(), List.class);
        for (Map<String, Object> job : statuses) {
          if (String.format("datasource:%s", ds).equals(job.get("resource"))) {
            return (String) job.get("status");
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        return "running";
      }
      return "idle";
    }
  }
}
