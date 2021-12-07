# f5-diagnostics
Diagnostics for f5

## Build

`./gradlew :f5-diagnostics-jdk8:clean :f5-diagnostics-jdk8:shadowJar :f5-diagnostics-jdk11:clean :f5-diagnostics-jdk11:shadowJar`

## Run

JDK 8

`java -cp ./f5-diagnostics-jdk8/build/libs/f5-diagnostics-jdk8-all.jar Diagnostics [options]`

JDK 11

`java -cp ./f5-diagnostics-jdk11/build/libs/f5-diagnostics-jdk11-all.jar Diagnostics [options]`

## Arguments

-collection VAL   : Solr collection
-command VAL      : Command to run.
-datasourceId VAL : ID of a connectors data source
-filter VAL       : Filter by
-host VAL         : The host to test when doing testConnect
-inFile VAL       : Input files
-label VAL        : Kube label
-namespace VAL    : Kube namespace
-port N           : The Port to test when doing testConnect
-q VAL            : Solr query
-rows N           : Rows (default: 0)
-timeout N        : Timeout to wait in millis before timeout (default: 60000)
-url VAL          : The url to test when doing curl
-zkChroot VAL     : zkChroot
-zkHosts VAL      : zkHosts string (comma sep)

## Commands

```
ps
solrQuery
cat
base64
ls
df
rm
rmdir
testConnect
curl
pulsarStats
indexingTopicStats
replicaCount
deleteOrphanedConnectorsBackendSubscriptions
```

# Turning Fusion 5 CSV log dumps into file system

`java -cp f5-diagnostics-jdk11/build/libs/f5-diagnostics-jdk11-all.jar LogCSVExportUtility [csv-file.csv] [outDirectory]`