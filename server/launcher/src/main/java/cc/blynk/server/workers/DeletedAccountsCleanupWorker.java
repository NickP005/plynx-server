package cc.blynk.server.workers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Daily job that permanently deletes user accounts from the "deleted" folder
 * that are older than the retention period (5 days by default).
 * 
 * This allows admins to recover accidentally deleted accounts within the grace period,
 * while ensuring GDPR compliance by eventually permanently removing the data.
 *
 * The Plynk Project.
 * Created for App Store compliance (Guideline 5.1.1(v)).
 */
public class DeletedAccountsCleanupWorker implements Runnable {

    private static final Logger log = LogManager.getLogger(DeletedAccountsCleanupWorker.class);

    private final Path deletedDataDir;
    private final int retentionDays;

    /**
     * Creates a cleanup worker for deleted accounts.
     * 
     * @param dataDir The main data directory (deleted folder will be a subdirectory)
     * @param retentionDays Number of days to keep deleted accounts (default: 5)
     */
    public DeletedAccountsCleanupWorker(String dataDir, int retentionDays) {
        this.deletedDataDir = Path.of(dataDir, "deleted");
        this.retentionDays = retentionDays;
    }

    /**
     * Creates a cleanup worker with default 5-day retention.
     */
    public DeletedAccountsCleanupWorker(String dataDir) {
        this(dataDir, 5);
    }

    @Override
    public void run() {
        if (!Files.exists(deletedDataDir)) {
            log.debug("Deleted accounts directory does not exist: {}", deletedDataDir);
            return;
        }

        try {
            log.info("Starting deleted accounts cleanup (retention: {} days)...", retentionDays);
            long now = System.currentTimeMillis();
            
            int deletedCount = cleanupOldAccounts();
            
            log.info("Deleted accounts cleanup completed. Removed {} files. Time: {} ms.",
                    deletedCount, System.currentTimeMillis() - now);
        } catch (Throwable t) {
            log.error("Error during deleted accounts cleanup.", t);
        }
    }

    private int cleanupOldAccounts() {
        int deletedCount = 0;
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        try (Stream<Path> stream = Files.list(deletedDataDir)) {
            List<Path> files = stream.collect(Collectors.toList());
            for (Path file : files) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    Instant fileTime = attrs.lastModifiedTime().toInstant();
                    
                    if (fileTime.isBefore(cutoffTime)) {
                        String fileName = file.getFileName().toString();
                        Files.delete(file);
                        deletedCount++;
                        log.info("Permanently deleted expired account file: {}", fileName);
                    }
                } catch (IOException e) {
                    log.error("Error processing file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Error listing deleted accounts directory: {}", e.getMessage());
        }

        return deletedCount;
    }
}
