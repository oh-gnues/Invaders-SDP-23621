package endless;

import engine.*;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Shared personal-best storage for modes whose scores are not comparable to campaign. */
public class ModeRecords {
    private final Path path;
    private long score, seconds, kills;
    private String status = "";

    public ModeRecords(Path path) {
        this.path = path.toAbsolutePath();
        if (!Files.exists(path)) return;
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            data.load(input);
            long loadedScore = read(data, "score");
            long loadedSeconds = read(data, "seconds");
            long loadedKills = read(data, "kills");
            score = loadedScore;
            seconds = loadedSeconds;
            kills = loadedKills;
        } catch (IOException | IllegalArgumentException ex) {
            status = "RECORDS UNAVAILABLE; RUN IS SAFE.";
            Core.getLogger().warning("Cannot read endless records: " + ex.getMessage());
        }
    }

    private long read(Properties data, String key) {
        long value = Long.parseLong(data.getProperty(key, "0"));
        if (value < 0) throw new IllegalArgumentException("Negative record");
        return value;
    }

    public boolean save(long runScore, long runSeconds, long runKills) {
        score = Math.max(score, runScore);
        seconds = Math.max(seconds, runSeconds);
        kills = Math.max(kills, runKills);
        Properties data = new Properties();
        data.setProperty("score", Long.toString(score));
        data.setProperty("seconds", Long.toString(seconds));
        data.setProperty("kills", Long.toString(kills));
        Path temporary = null;
        try {
            temporary = Files.createTempFile(path.getParent(), "endless-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                data.store(output, "Endless mode personal bests");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            status = "PERSONAL BESTS SAVED";
            return true;
        } catch (IOException ex) {
            status = "SAVE FAILED; KEPT THIS SESSION.";
            Core.getLogger().warning("Cannot save endless records: " + ex.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ex) { Core.getLogger().fine(ex.getMessage()); }
            }
        }
    }

    public long getScore() { return score; }
    public long getSeconds() { return seconds; }
    public long getKills() { return kills; }
    public String getStatus() { return status; }
}
