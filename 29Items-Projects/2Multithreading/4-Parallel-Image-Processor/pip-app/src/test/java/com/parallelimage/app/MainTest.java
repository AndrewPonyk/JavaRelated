package com.parallelimage.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.app.cli.CliRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Main} tests: every branch that ends in {@code System.exit}, exercised in a real child JVM
 * since {@code main} cannot be called in-process without killing the test runner. The JaCoCo agent
 * attached to this JVM (if any) is forwarded to the child so its coverage merges into the same
 * {@code jacoco.exec} the module's report/check goals read.
 */
class MainTest {

    @TempDir
    private Path root;

    private record Launch(int exitCode, String stdout, String stderr) {
    }

    private Launch launch(List<String> args, Map<String, String> extraEnv) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = Path.of(javaHome, "bin", "java").toString();

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        agentArg().ifPresent(command::add);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(extraEnv);
        Process process = builder.start();

        StreamDrain out = new StreamDrain(process.getInputStream());
        StreamDrain err = new StreamDrain(process.getErrorStream());
        out.start();
        err.start();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        out.join(5_000);
        err.join(5_000);

        return new Launch(finished ? process.exitValue() : -1, out.text(), err.text());
    }

    /** Forwards this JVM's own {@code -javaagent} (JaCoCo, if attached) so the child's hits count too. */
    private java.util.Optional<String> agentArg() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-javaagent:") && arg.toLowerCase(java.util.Locale.ROOT).contains("jacoco"))
                .findFirst();
    }

    private static final class StreamDrain extends Thread {
        private final InputStream stream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamDrain(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public void run() {
            try {
                stream.transferTo(buffer);
            } catch (IOException e) {
                // process was destroyed; whatever was captured is still useful
            }
        }

        String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("startup / usage")
    class StartupUsage {

        @Test
        @DisplayName("--ui and --serve together is a usage error")
        void uiAndServeAreMutuallyExclusive() throws Exception {
            Launch result = launch(List.of("--ui", "--serve"), Map.of());

            assertEquals(CliRunner.EXIT_USAGE, result.exitCode());
            assertTrue(result.stderr().contains("--ui and --serve are mutually exclusive"), result.stderr());
        }

        @Test
        @DisplayName("an unknown flag is a CLI syntax error")
        void unknownFlagIsSyntaxError() throws Exception {
            Launch result = launch(List.of("--not-a-real-flag"), Map.of());

            assertEquals(CliRunner.EXIT_USAGE, result.exitCode());
            assertTrue(result.stderr().startsWith("error: "), result.stderr());
        }

        @Test
        @DisplayName("--help prints usage and exits 0")
        void helpPrintsUsage() throws Exception {
            Launch result = launch(List.of("--help"), Map.of());

            assertEquals(CliRunner.EXIT_OK, result.exitCode());
            assertTrue(result.stdout().contains("--in"), result.stdout());
        }

        @Test
        @DisplayName("--version prints the version report and exits 0")
        void versionPrintsReport() throws Exception {
            Launch result = launch(List.of("--version"), Map.of("PIP_DB_ENABLED", "false"));

            assertEquals(CliRunner.EXIT_OK, result.exitCode());
            assertTrue(result.stdout().contains("Parallel Image Processor"), result.stdout());
            assertTrue(result.stdout().contains("history:"), result.stdout());
        }
    }

    @Nested
    @DisplayName("batch mode")
    class BatchMode {

        @Test
        @DisplayName("a missing --in fails validation before the registry ever opens")
        void missingInputIsUsageError() throws Exception {
            Launch result = launch(List.of("--out", root.resolve("out").toString()),
                    Map.of("PIP_DB_ENABLED", "false"));

            assertEquals(CliRunner.EXIT_USAGE, result.exitCode());
            assertTrue(result.stderr().contains("--in is required"), result.stderr());
        }

        @Test
        @DisplayName("an empty --in runs the whole registry-open/close lifecycle and exits 0")
        void emptyInputRunsCleanly() throws Exception {
            Path input = Files.createDirectories(root.resolve("in"));
            Path output = root.resolve("out");

            Launch result = launch(List.of("--in", input.toString(), "--out", output.toString()),
                    Map.of("PIP_DB_ENABLED", "false"));

            assertEquals(CliRunner.EXIT_OK, result.exitCode());
            assertTrue(result.stdout().contains("no images found in"), result.stdout());
        }
    }

    @Nested
    @DisplayName("--serve")
    class Serve {

        @Test
        @DisplayName("a port already in use fails startup with EXIT_STARTUP")
        void portAlreadyInUseFailsStartup() throws Exception {
            try (ServerSocket blocker = new ServerSocket(0)) {
                int busyPort = blocker.getLocalPort();

                Launch result = launch(List.of("--serve"), Map.of(
                        "PIP_DB_ENABLED", "false",
                        "PIP_API_PORT", String.valueOf(busyPort)));

                assertEquals(CliRunner.EXIT_STARTUP, result.exitCode());
                assertTrue(result.stderr().contains("error: cannot listen on port " + busyPort), result.stderr());
            }
        }
    }
}
