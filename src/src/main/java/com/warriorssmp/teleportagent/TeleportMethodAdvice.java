package com.warriorssmp.teleportagent;

import net.bytebuddy.asm.Advice;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Woven into org.bukkit.craftbukkit.entity.CraftEntity#teleport() itself
 * (all overloads - they call into each other, so one real teleport attempt
 * produces up to 3 log blocks here, one per nesting level, which is
 * expected). Location.getWorld() turned out NOT to be throwing anything
 * during the actual /mvtp and /wtp failures - so this looks one level
 * higher, directly at the method that's actually returning false, to see
 * its real return value or exception with no assumptions this time.
 */
public class TeleportMethodAdvice {

    private static final AtomicInteger COUNT = new AtomicInteger(0);
    private static final int MAX_LOGGED = 60; // higher cap - each real attempt produces up to 3 entries

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.This Object thiz, @Advice.Return(readOnly = true) boolean returned,
                               @Advice.Thrown Throwable throwable) {
        // Never let our own logging disrupt the real method's behavior - if
        // anything in here goes wrong, swallow it silently rather than let it
        // propagate into the instrumented method's control flow.
        try {
            int n = COUNT.incrementAndGet();
            if (n > MAX_LOGGED) return;

            System.err.println("=== [WSMP-TeleportAgent] CraftEntity.teleport() exit (#" + n + ") ===");
            System.err.println("[WSMP-TeleportAgent] Entity: " + safeToString(thiz));
            System.err.println("[WSMP-TeleportAgent] Thread: " + Thread.currentThread().getName());
            if (throwable != null) {
                System.err.println("[WSMP-TeleportAgent] Threw: " + throwable.getClass().getName() + ": " + throwable.getMessage());
            } else {
                System.err.println("[WSMP-TeleportAgent] Returned (no exception): " + returned);
            }
            new Throwable("[WSMP-TeleportAgent] call stack at this exit").printStackTrace();
        } catch (Throwable ignored) {
            // Logging failure - never let it affect the real teleport call.
        }
    }

    private static String safeToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Throwable t) {
            return "<toString() failed: " + t + ">";
        }
    }
}
