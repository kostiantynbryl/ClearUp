package com.norvexa.clearup.data.privilege;

import android.content.Context;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Keep
public final class ShizukuCommandService extends IShizukuCommandService.Stub {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$"
    );
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
        if (operation == null || !PACKAGE_PATTERN.matcher(packageName == null ? "" : packageName).matches()) {
            return result(-1, "", "Invalid operation target");
        }
        if (userId < 0 || userId > 999) {
            return result(-1, "", "Invalid Android user id");
        }

        final List<String> command = commandFor(operation, packageName, userId);
        if (command == null) {
            return result(-1, "", "Unsupported Shizuku operation");
        }
        return run(command);
    }

    private static List<String> commandFor(String operation, String packageName, int userId) {
        final String user = Integer.toString(userId);
        final List<String> command = new ArrayList<>();
        switch (operation) {
            case "CLEAR_CACHE":
                command.add("/system/bin/pm");
                command.add("clear");
                command.add("--user");
                command.add(user);
                command.add("--cache-only");
                command.add(packageName);
                return command;
            case "FORCE_STOP":
                command.add("/system/bin/am");
                command.add("force-stop");
                command.add("--user");
                command.add(user);
                command.add(packageName);
                return command;
            case "FREEZE":
                command.add("/system/bin/pm");
                command.add("disable-user");
                command.add("--user");
                command.add(user);
                command.add(packageName);
                return command;
            case "UNFREEZE":
                command.add("/system/bin/pm");
                command.add("enable");
                command.add("--user");
                command.add(user);
                command.add(packageName);
                return command;
            default:
                return null;
        }
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
            return result(-1, "", truncate(error.getMessage() == null ? error.toString() : error.getMessage()));
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
