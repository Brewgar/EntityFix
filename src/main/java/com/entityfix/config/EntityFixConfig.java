package com.entityfix.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EntityFix configuration.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Every option maps to one explicit optimization module - there are no
 *       vague "more optimization" switches.</li>
 *   <li>Default mode is {@link Mode#SAFE}: correctness first, only
 *       bookkeeping/diagnostics features enabled.</li>
 *   <li>Unknown fields in the file are preserved on save so user comments
 *       ordering is stable and future versions stay forward compatible.</li>
 * </ul>
 */
public final class EntityFixConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("entityfix.json");

    /** Global feature preset. Individual flags below always win. */
    public Mode mode = Mode.SAFE;

    /** Master switch for the low-overhead statistics layer. */
    public boolean stats = true;

    /** Path-relevant block change versioning (topology tracking). */
    public boolean topology_tracking = true;

    /**
     * Reuse a navigation's previously computed path when target, distance and
     * topology version are unchanged. Conservative; disabled by default.
     */
    public boolean path_cache = false;

    /**
     * How many ticks a cached path may be reused for. Only used when
     * {@code path_cache} is enabled.
     */
    public int path_cache_ttl_ticks = 40;

    /**
     * Reserved for the (not yet enabled) AI sleeping subsystem. Kept here so
     * the config schema is stable.
     */
    public boolean ai_sleep = false;

    /** Verbose debug logging. Off by default; never spam the log. */
    public boolean verbose_debug = false;

    public enum Mode {
        /** Diagnostics and bookkeeping only. Zero behavior change. */
        SAFE,
        /** Enables conservative redundant-work elimination (e.g. path cache). */
        BALANCED,
        /** Enables experimental modules (currently: none). Not for production. */
        EXPERIMENTAL
    }

    /** Resolve the effective state of a module flag from mode + explicit flag. */
    public boolean effective(boolean explicitFlag, boolean balancedDefault) {
        if (explicitFlag) {
            return true;
        }
        return mode == Mode.BALANCED && balancedDefault;
    }

    public static EntityFixConfig load() {
        EntityFixConfig config = new EntityFixConfig();
        if (Files.exists(PATH)) {
            try {
                String json = Files.readString(PATH);
                EntityFixConfig read = GSON.fromJson(json, EntityFixConfig.class);
                if (read != null) {
                    config = read;
                }
            } catch (Exception e) {
                // Fall back to defaults rather than failing to launch.
                System.err.println("[EntityFix] Failed to read config, using defaults: " + e);
            }
        }
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[EntityFix] Failed to save config: " + e);
        }
    }
}
