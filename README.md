# f5-diagnostics
diagnostics for f5

`java -jar ./build/libs/diag.jar`


-command VAL : Command to run
-filter VAL  : Filter by
-inFile VAL  : Input files


Example

```bash
kubectl -n ns exec -it your_pod_name -- java -jar /tmp/diag.jar -command ls -inFile /apps
/apps:
  --- /apps/file1.txt
  --- /apps/file2.txt
```
