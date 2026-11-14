// src/main/java/hdledit/util/GitInfo.java
package hdledit.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public final class GitInfo {

    private GitInfo() {}

    /**
     * Liefert den kurzen Git-SHA (7 Zeichen) + optional "-dirty".
     * Beispiel: "a1b2c3d" oder "a1b2c3d-dirty"
     * Falls Git nicht verfügbar ist oder ein Fehler auftritt → "unknown"
     */
    public static String getVersionSuffix() {
        try {
            String sha = runGitCommand("rev-parse", "--short=7", "HEAD");
            if (sha == null || sha.isBlank()) {
                return "unknown";
            }

            boolean dirty = isDirty();
            return dirty ? sha + "-dirty" : sha;

        } catch (Exception e) {
            return "unknown";
        }
    }

    private static boolean isDirty() {
        try {
            String output = runGitCommand("status", "--porcelain");
            return output != null && !output.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private static String runGitCommand(String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add("git");
        for (String arg : args) {
            pb.command().add(arg);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean finished = process.waitFor(3, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            return reader.readLine();
        }
    }
}