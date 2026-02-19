package com.ap.service;

import com.ap.dto.ExecuteResponse;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

@Service
public class PythonExecutorService {

    private static final long TIMEOUT_SECONDS = 10;
    private static final String[] PYTHON_COMMANDS = {"python", "python3", "py"};

    private String findPython() {
        for (String cmd : PYTHON_COMMANDS) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return cmd;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public ExecuteResponse execute(String fileName, String code) {
        Path tempDir = null;
        try {
            // Create temporary directory
            tempDir = Files.createTempDirectory("python-exec-");
            Path scriptFile = tempDir.resolve(fileName);

            // Write script file
            Files.writeString(scriptFile, code);

            // Try to find available Python interpreter
            String pythonCmd = findPython();
            if (pythonCmd == null) {
                return ExecuteResponse.failure("Python not found. Please install Python and add it to PATH.");
            }

            // Run Python script
            ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Wait for completion with timeout
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ExecuteResponse.failure("Execution timeout (>" + TIMEOUT_SECONDS + "s)");
            }

            // Capture output
            String output = new String(process.getInputStream().readAllBytes());
            String errorOutput = new String(process.getErrorStream().readAllBytes());

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ExecuteResponse.failure(errorOutput.isEmpty() ? "Exit code: " + exitCode : errorOutput);
            }

            return ExecuteResponse.success(output);

        } catch (Exception e) {
            return ExecuteResponse.failure("Error: " + e.getMessage());
        } finally {
            // Cleanup temp directory
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                         .sorted(java.util.Comparator.reverseOrder())
                         .forEach(path -> {
                             try { Files.delete(path); } catch (IOException ignored) {}
                         });
                } catch (IOException ignored) {}
            }
        }
    }
}
