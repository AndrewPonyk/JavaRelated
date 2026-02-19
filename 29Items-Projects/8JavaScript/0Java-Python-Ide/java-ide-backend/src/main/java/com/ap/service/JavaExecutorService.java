package com.ap.service;

import com.ap.dto.ExecuteResponse;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

@Service
public class JavaExecutorService {

    private static final String TEMP_DIR_PREFIX = "java-exec-";

    public ExecuteResponse execute(String className, String code) {
        Path tempDir = null;
        try {
            // Create temporary directory
            tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            Path sourceFile = tempDir.resolve(className + ".java");
            Path classFile = tempDir.resolve(className + ".class");

            // Write source file
            Files.writeString(sourceFile, code);

            // Compile the code
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return ExecuteResponse.failure("Java compiler not available. Please run with JDK, not JRE.");
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile.toString());
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, null, null, compilationUnits
            );

            boolean compiled = task.call();
            fileManager.close();

            if (!compiled) {
                StringBuilder errors = new StringBuilder("Compilation failed:\n");
                for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                    errors.append(diagnostic.getKind()).append(": ")
                          .append(diagnostic.getMessage(null)).append("\n");
                }
                return ExecuteResponse.failure(errors.toString());
            }

            // Execute the compiled class
            ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;

            try {
                URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()});
                Class<?> mainClass = classLoader.loadClass(className);

                System.setOut(new PrintStream(outputCapture));
                System.setErr(new PrintStream(outputCapture));

                mainClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);

                classLoader.close();
                return ExecuteResponse.success(outputCapture.toString());

            } catch (Exception e) {
                String errorMsg = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
                return ExecuteResponse.failure("Runtime error: " + errorMsg);
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }

        } catch (Exception e) {
            return ExecuteResponse.failure("Error: " + e.getMessage());
        } finally {
            // Cleanup temp directory
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                         .sorted(Comparator.reverseOrder())
                         .forEach(path -> {
                             try { Files.delete(path); } catch (IOException ignored) {}
                         });
                } catch (IOException ignored) {}
            }
        }
    }
}
