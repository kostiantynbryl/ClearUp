package com.norvexa.clearup.data.privilege;

import android.content.Context;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Keep
public final class ShizukuCommandService extends IShizukuCommandService.Stub {
    private static final int MAX_OUTPUT_LENGTH = 4_000;
    private static final long TIMEOUT_SECONDS = 45L;

    public ShizukuCommandService() {
    }

    @Keep
    public ShizukuCommandService(Context context) {
        // The Context supplied by Shizuku is intentionally not retained.
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String[] execute(String operation, String packageName, int userId) {
        final List<String> command = ShizukuCommandPolicy.commandFor(
                operation,
                packageName,
                userId
        );
        if (command == null) {
            return result(-1, "", "Operation, package name or Android user is not allowed");
        }
        return run(command);
    }

    private static String[] run(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            final boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2L, TimeUnit.SECONDS);
                return result(-1, "", "Shizuku operation timed out");
            }

            final StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null && output.length() < MAX_OUTPUT_LENGTH) {
                    if (output.length() > 0) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            }
            final int exitCode = process.exitValue();
            final String text = truncate(output.toString());
            return exitCode == 0
                    ? result(exitCode, text, "")
                    : result(exitCode, "", text.isEmpty() ? "Command failed" : text);
        } catch (Throwable error) {
            return result(
                    -1,
                    "",
                    truncate(error.getMessage() == null ? error.toString() : error.getMessage())
            );
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_OUTPUT_LENGTH
                ? value
                : value.substring(0, MAX_OUTPUT_LENGTH);
    }

    private static String[] result(int exitCode, String output, String error) {
        return new String[]{Integer.toString(exitCode), output, error};
    }
}
