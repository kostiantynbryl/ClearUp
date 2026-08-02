package com.norvexa.clearup.data.privilege;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public final class ShizukuCommandPolicyTest {
    @Test
    public void acceptsNormalPackageName() {
        assertTrue(ShizukuCommandPolicy.isValidPackageName("com.norvexa.example"));
    }

    @Test
    public void rejectsShellAndPathInjection() {
        assertFalse(ShizukuCommandPolicy.isValidPackageName("com.example.app;rm"));
        assertFalse(ShizukuCommandPolicy.isValidPackageName("../data/local/tmp"));
        assertFalse(ShizukuCommandPolicy.isValidPackageName("singleword"));
        assertFalse(ShizukuCommandPolicy.isValidPackageName("com.example.$(id)"));
    }

    @Test
    public void buildsCacheOnlyCommandWithoutShellInterpreter() {
        assertEquals(
                Arrays.asList(
                        "/system/bin/pm",
                        "clear",
                        "--user",
                        "0",
                        "--cache-only",
                        "com.example.app"
                ),
                ShizukuCommandPolicy.commandFor(
                        ShizukuCommandPolicy.CLEAR_CACHE,
                        "com.example.app",
                        0
                )
        );
    }

    @Test
    public void buildsForceStopForSelectedUser() {
        assertEquals(
                Arrays.asList(
                        "/system/bin/am",
                        "force-stop",
                        "--user",
                        "10",
                        "com.example.app"
                ),
                ShizukuCommandPolicy.commandFor(
                        ShizukuCommandPolicy.FORCE_STOP,
                        "com.example.app",
                        10
                )
        );
    }

    @Test
    public void rejectsUnknownOperationAndInvalidUser() {
        assertNull(ShizukuCommandPolicy.commandFor("SHELL", "com.example.app", 0));
        assertNull(
                ShizukuCommandPolicy.commandFor(
                        ShizukuCommandPolicy.FREEZE,
                        "com.example.app",
                        -1
                )
        );
        assertNull(
                ShizukuCommandPolicy.commandFor(
                        ShizukuCommandPolicy.UNFREEZE,
                        "com.example.app",
                        1000
                )
        );
    }
}
