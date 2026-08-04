package com.migration.contract.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ensures docker-compose Postgres and Mongo are running before contract tests.
 */
public final class DockerComposeSupport {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private DockerComposeSupport() {
    }

    public static void ensureRunning() throws IOException, InterruptedException {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        Path repoRoot = findRepoRoot();
        runCommand(repoRoot, 120, "docker", "compose", "up", "-d");
        waitForServices(repoRoot);
    }

    private static Path findRepoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("docker-compose.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate docker-compose.yml from user.dir="
                + System.getProperty("user.dir"));
    }

    private static void waitForServices(Path repoRoot) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                if (postgresReady(repoRoot) && mongoReady(repoRoot)) {
                    return;
                }
            } catch (IOException ex) {
                lastFailure = ex;
            }
            Thread.sleep(2000);
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("Timed out waiting for docker-compose services to become ready");
    }

    private static boolean postgresReady(Path repoRoot) throws IOException, InterruptedException {
        String output = runCommand(repoRoot, 30, "docker", "compose", "ps", "postgres");
        if (!output.matches("(?s).*(healthy|running).*")) {
            return false;
        }
        runCommand(
                repoRoot,
                30,
                "docker",
                "compose",
                "exec",
                "-T",
                "postgres",
                "pg_isready",
                "-U",
                "postgres",
                "-d",
                "migration");
        return true;
    }

    private static boolean mongoReady(Path repoRoot) throws IOException, InterruptedException {
        String output = runCommand(repoRoot, 30, "docker", "compose", "ps", "mongo");
        if (!output.matches("(?s).*(healthy|running).*")) {
            return false;
        }
        String rsStatus = runCommand(
                repoRoot,
                30,
                "docker",
                "compose",
                "exec",
                "-T",
                "mongo",
                "mongosh",
                "--quiet",
                "--eval",
                "try { rs.status().ok } catch (e) { 0 }");
        return rsStatus.trim().equals("1");
    }

    private static String runCommand(Path workingDirectory, long timeoutSeconds, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed (" + process.exitValue() + "): "
                    + String.join(" ", command) + System.lineSeparator() + output);
        }
        return output.toString();
    }
}
