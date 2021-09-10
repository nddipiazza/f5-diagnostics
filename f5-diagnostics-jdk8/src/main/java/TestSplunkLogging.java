import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSplunkLogging {
  private static final Logger logger = LoggerFactory.getLogger(TestSplunkLogging.class);

  public static void main(String [] args) {
    logger.info("tikaasync: download_file_queue_size_i=3, host_address_s=hello, latest_emit_s=2021-08-23T18:55:27.641, latest_fetch_s=2021-08-23T18:55:28.340, downloads_per_minute_l=101, latest_start_s=2021-08-23T18:55:00.594, latest_find_s=2021-08-23T18:55:02.432, latest_complete_s=(no activity recorded), id=tika-async-0, fetch_queue_size_i=907, emits_per_minute_l=16, hostname_s=tika-async-0, current_async_job_id_s=all-sharepoint");
  }
}
