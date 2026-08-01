package com.migration.debug;

public final class AgentDebugLog {

    private static final String LOG_PATH = "/Users/ade/Workspaces/cursor/m2m/.cursor/debug-ce46e5.log";

    private AgentDebugLog() {
    }

    public static void log(String hypothesisId, String location, String message, String dataJson) {
        // #region agent log
        try (var w = new java.io.FileWriter(LOG_PATH, java.nio.charset.StandardCharsets.UTF_8, true)) {
            w.write(String.format(
                    "{\"sessionId\":\"ce46e5\",\"hypothesisId\":\"%s\",\"location\":\"%s\",\"message\":\"%s\",\"data\":%s,\"timestamp\":%d}%n",
                    hypothesisId,
                    location,
                    message.replace("\"", "\\\""),
                    dataJson,
                    System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
        // #endregion
    }
}
