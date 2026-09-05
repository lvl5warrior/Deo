package com.warriorssmp.teleportagent;

import net.bytebuddy.asm.Advice;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Woven directly into org.bukkit.Location#getWorld() by ByteBuddy - this
 * code runs AS PART OF that method, at the instant it exits (whether
 * normally or via exception). We only care about the exception case.
 *
 * Deliberately references Location only as "Object" (@Advice.This Object,
 * not the real Location type) since this agent project has no Bukkit/Paper
 * dependency at all - it's meant to attach to a server JVM that already has
 * those classes loaded, not to be compiled against them.
 */
public class GetWorldAdvice {

    private static final AtomicInteger COUNT = new AtomicInteger(0);
    private static final int MAX_LOGGED = 25; // cap output - this can fire often once triggered

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.This Object thiz, @Advice.Thrown Throwable throwable) {
        if (throwable == null) return;

        int n = COUNT.incrementAndGet();
        if (n > MAX_LOGGED) return;

        System.err.println("=== [WSMP-TeleportAgent] Location.getWorld() threw (#" + n
                + " of max " + MAX_LOGGED + " logged) ===");
        System.err.println("[WSMP-TeleportAgent] Exception: " + throwable.getClass().getName() + ": " + throwable.getMessage());
        System.err.println("[WSMP-TeleportAgent] Location instance identity: " + System.identityHashCode(thiz)
                + " (" + thiz + ")");
        System.err.println("[WSMP-TeleportAgent] Thread: " + Thread.currentThread().getName());
        // A fresh Throwable's stack trace captures the CURRENT call stack at
        // this exact point - i.e. everyone who called down into
        // Location.getWorld(), all the way up. This is the piece no plugin
        // could ever see, since Paper's own teleport() implementation
        // catches the real exception one level below any plugin's code.
        new Throwable("[WSMP-TeleportAgent] full call stack at the moment of failure").printStackTrace();
    }
}
