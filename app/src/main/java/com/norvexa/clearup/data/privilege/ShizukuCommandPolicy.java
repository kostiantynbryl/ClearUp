package com.norvexa.clearup.data.privilege;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ShizukuCommandPolicy {
    static final String CLEAR_CACHE = "CLEAR_CACHE";
    static final String FORCE_STOP = "FORCE_STOP";
    static final String FREEZE = "FREEZE";
    static final String UNFREEZE = "UNFREEZE";
    static final int MIN_SAFE_CACHE_CLEAR_SDK = 33;

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$"
    );

    private ShizukuCommandPolicy() {
    }

    static boolean isValidPackageName(String packageName) {
        return packageName != null && PACKAGE_PATTERN.matcher(packageName).matches();
    }

    static boolean isValidUserId(int userId) {
        return userId >= 0 && userId <= 999;
    }

    static boolean isCacheOnlySupported(int sdkInt) {
        return sdkInt >= MIN_SAFE_CACHE_CLEAR_SDK;
    }

    static List<String> commandFor(String operation, String packageName, int userId) {
        if (!isValidPackageName(packageName) || !isValidUserId(userId)) {
            return null;
        }

        final String user = Integer.toString(userId);
        final List<String> command = new ArrayList<>();
        if (CLEAR_CACHE.equals(operation)) {
            command.add("/system/bin/pm");
            command.add("clear");
            command.add("--user");
            command.add(user);
            command.add("--cache-only");
            command.add(packageName);
            return command;
        }
        if (FORCE_STOP.equals(operation)) {
            command.add("/system/bin/am");
            command.add("force-stop");
            command.add("--user");
            command.add(user);
            command.add(packageName);
            return command;
        }
        if (FREEZE.equals(operation)) {
            command.add("/system/bin/pm");
            command.add("disable-user");
            command.add("--user");
            command.add(user);
            command.add(packageName);
            return command;
        }
        if (UNFREEZE.equals(operation)) {
            command.add("/system/bin/pm");
            command.add("enable");
            command.add("--user");
            command.add(user);
            command.add(packageName);
            return command;
        }
        return null;
    }
}
