package com.warriorssmp.teleportagent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Attaches at JVM startup (via -javaagent, NOT dropped in /plugins - this is
 * not a Bukkit plugin).
 *
 * Two independent things happen here now, after CraftEntity AND CraftPlayer
 * both produced zero output despite player.getClass().getName() confirming
 * the object really is a CraftPlayer:
 *
 * 1. A raw, unconditional ClassFileTransformer that just LOGS every class
 *    whose name contains "player" (case-insensitive) as it loads - no
 *    guessing about class names anymore, actual ground truth of what's
 *    really in this JVM.
 * 2. The ByteBuddy instrumentation of "teleport" is now matched against
 *    ElementMatchers.any() - EVERY loaded class, not just an assumed
 *    package - so it cannot miss the real method regardless of which class
 *    actually defines it.
 */
public class TeleportAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.setProperty("com.warriorssmp.teleportagent.shaded.bytebuddy.experimental", "true");

        System.out.println("[WSMP-TeleportAgent] Attaching (max-effort build) - raw class-load logging "
                + "for anything containing 'player', PLUS universal 'teleport' method instrumentation "
                + "across every loaded class, no package assumptions this time.");

        // --- 1. Raw ground-truth class-load logger, no bytecode modification ---
        final AtomicInteger classLogCount = new AtomicInteger(0);
        final int MAX_CLASS_LOGS = 200;
        inst.addTransformer((loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (className != null && className.toLowerCase().contains("player")) {
                int n = classLogCount.incrementAndGet();
                if (n <= MAX_CLASS_LOGS) {
                    System.out.println("[WSMP-TeleportAgent][classload #" + n + "] " + className.replace('/', '.')
                            + (loader == null ? " (bootstrap loader)" : " (loader: " + loader.getClass().getName() + ")"));
                }
            }
            return null; // never modify bytecode here - this transformer only observes
        }, false);

        // --- 2. Universal teleport-method instrumentation, no package assumption ---
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                        // Expected to be noisy now that we match ElementMatchers.any() -
                        // only print errors for types that actually look relevant, to
                        // keep this from drowning out the real signal.
                        if (typeName != null && (typeName.toLowerCase().contains("player")
                                || typeName.toLowerCase().contains("entity")
                                || typeName.toLowerCase().contains("location"))) {
                            System.err.println("[WSMP-TeleportAgent] Failed to instrument " + typeName + ": " + throwable);
                        }
                    }
                })
                .type(ElementMatchers.named("org.bukkit.Location"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(GetWorldAdvice.class).on(ElementMatchers.named("getWorld"))))
                .type(ElementMatchers.any())
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(TeleportMethodAdvice.class).on(ElementMatchers.named("teleport"))))
                .installOn(inst);

        System.out.println("[WSMP-TeleportAgent] Both instrumentations installed.");
    }
}
