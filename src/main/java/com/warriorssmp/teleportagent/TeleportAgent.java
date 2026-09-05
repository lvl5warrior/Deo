package com.warriorssmp.teleportagent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;

/**
 * Attaches at JVM startup (via -javaagent, NOT dropped in /plugins - this is
 * not a Bukkit plugin) and instruments org.bukkit.Location#getWorld() so we
 * can see the exact moment it throws IllegalArgumentException("World
 * unloaded") - including the full calling stack trace - BEFORE Paper's own
 * teleport() implementation gets a chance to catch and silently swallow it.
 *
 * This is the only way left to see what's actually happening internally:
 * every attempt to catch this from OUR OWN plugin code failed, because by
 * the time control returns to any caller, the exception has already been
 * caught and turned into a plain "false" return value one level deeper than
 * any plugin can observe from outside.
 */
public class TeleportAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        // This server runs Java 25, which ByteBuddy 1.15.10 doesn't officially
        // recognize yet (it refuses to instrument ANY class on an unrecognized
        // class file version, not just Location specifically, unless told it's
        // OK to proceed anyway). Setting this before installOn() avoids needing
        // an extra -D JVM flag from whoever deploys this. The property name
        // itself is relative to ByteBuddy's own (shaded/relocated) package, so
        // it must match wherever pom.xml relocates net.bytebuddy to.
        System.setProperty("com.warriorssmp.teleportagent.shaded.bytebuddy.experimental", "true");

        System.out.println("[WSMP-TeleportAgent] Attaching - watching for Location.getWorld() 'World unloaded' exceptions...");

        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                        System.err.println("[WSMP-TeleportAgent] Failed to instrument " + typeName + ": " + throwable);
                    }
                })
                .type(ElementMatchers.named("org.bukkit.Location"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(GetWorldAdvice.class).on(ElementMatchers.named("getWorld"))))
                .type(ElementMatchers.named("org.bukkit.craftbukkit.entity.CraftEntity"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(TeleportMethodAdvice.class).on(ElementMatchers.named("teleport"))))
                .type(ElementMatchers.named("org.bukkit.craftbukkit.entity.CraftPlayer"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(TeleportMethodAdvice.class).on(ElementMatchers.named("teleport"))))
                .installOn(inst);

        System.out.println("[WSMP-TeleportAgent] Instrumentation installed on org.bukkit.Location#getWorld(), "
                + "org.bukkit.craftbukkit.entity.CraftEntity#teleport(), and org.bukkit.craftbukkit.entity.CraftPlayer#teleport() - "
                + "CraftPlayer almost certainly overrides teleport() separately, which is why the CraftEntity hook alone saw nothing "
                + "for real player teleports.");
    }
}
