import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

public class LogCSVExportUtility {
    public static void main(String[] args) throws Exception {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy.MM.dd_HH.mm.ss.SSS");
        File csvFile = new File(args[0]);
        File outDir = new File(args[1]);
        File dumpDir = new File(outDir, df.format(LocalDateTime.now()));
        dumpDir.mkdirs();
        final CSVFormat csvFormat = CSVFormat.DEFAULT.withHeader().withSkipHeaderRecord(false);
        FileReader fr = new FileReader(csvFile);
        CSVParser parser = csvFormat.parse(fr);
        Iterator<CSVRecord> nextRecord = parser.iterator();
        while (nextRecord.hasNext()) {
            CSVRecord record = nextRecord.next();
            String kubernetes_pod_name_s = record.get(parser.getHeaderMap().get("kubernetes_pod_name_s"));
            String level_s = record.get(parser.getHeaderMap().get("level_s"));
            String timestamp_tdt = record.get(parser.getHeaderMap().get("timestamp_tdt"));
            String logger_class_s = record.get(parser.getHeaderMap().get("logger_class_s"));
            String message_txt = record.get(parser.getHeaderMap().get("message_txt"));
            String thread_s = record.get(parser.getHeaderMap().get("thread_s"));
            String caller_line_number_s = record.get(parser.getHeaderMap().get("caller_line_number_s"));
            FileUtils.writeStringToFile(new File(dumpDir, kubernetes_pod_name_s + ".log"), String.format("%s - %s [%s:%s@%s] - %s%n", timestamp_tdt, level_s, thread_s, logger_class_s, caller_line_number_s, message_txt), true);
        }
        System.out.println("Exported logs to " + dumpDir);
    }
}
