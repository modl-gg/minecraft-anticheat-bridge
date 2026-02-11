package gg.modl.bridge.detection;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ViolationRecord {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DetectionSource source;
    private final String checkName;
    private final String verbose;
    private final long timestamp;

    public ViolationRecord(DetectionSource source, String checkName, String verbose) {
        this.source = source;
        this.checkName = checkName;
        this.verbose = verbose;
        this.timestamp = System.currentTimeMillis();
    }

    public DetectionSource getSource() {
        return source;
    }

    public String getCheckName() {
        return checkName;
    }

    public String getVerbose() {
        return verbose;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        return FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    @Override
    public String toString() {
        return "[" + getFormattedTimestamp() + "] [" + source + "] " + checkName + " | " + verbose;
    }
}
