package com.lucidworks.diagnostics;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Copy from one config's JSON to another set of other configs.
 * For example, copy the same datasource password to 8 others:
 *
 * <pre>
 * -zkConnect zk:2181/ -copyFrom sp_1 -copyTo sp2 -copyTo sp3 -jsonPath $.properties.ntlmProperties.password
 * </pre>
 * will copy sp_1 password to sp2 and sp3.
 */
public class ZkDataCopyUtil {
    @Option(name = "-zkConnect", usage = "Zookeeper connect string such as localhost:2181.", required = true)
    String zkConnect;

    @Option(name = "-fusionVersion", usage = "Fusion Version, for example: 5.0")
    String fusionVersion = "5.0";

    @Option(name = "-zkPath", usage = "ZK path to resources to update. By default, /connectors/configs will update connectors.")
    String zkPath = "/connectors/configs";

    @Option(name = "-copyFrom", usage = "ID of the resource to obtain the property that will be copied.")
    String copyFrom;

    @Option(name = "-copyTo", usage = "ID of the resources that are going to receive the copied property.")
    List<String> copyTo;

    @Option(name = "-jsonPath", usage = "JSON path of the property we are copying.")
    String jsonPath;

    public static String replaceOldValueVithNewValueforGivenPath(String jsonBody, String path, String newValue) {
        try {
            return JsonPath.parse(jsonBody).set(path, newValue).jsonString();
        } catch (PathNotFoundException var3) {
            throw new RuntimeException("No results for path: " + path);
        }
    }

    public void run() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory
                .builder()
                .connectString(zkConnect)
                .sessionTimeoutMs(15000)
                .connectionTimeoutMs(15000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build()) {
            client.start();

            String fullPath = "/lwfusion/" + fusionVersion + zkPath;

            String configFrom = new String(client.getData().forPath(fullPath + "/" + copyFrom));

            String pwdEncrypted = JsonPath.read(configFrom, jsonPath);

            for (String nextDsId : copyTo) {
                String configTo = new String(client.getData().forPath(fullPath + "/" + nextDsId));
                client.setData().forPath(fullPath + "/" + copyTo, replaceOldValueVithNewValueforGivenPath(configTo,
                    jsonPath, pwdEncrypted).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ZkDataCopyUtil o = new ZkDataCopyUtil();
        CmdLineParser parser = new CmdLineParser(o);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.out);
            System.out.println(e.getLocalizedMessage());
            throw e;
        }
        o.run();
    }
}