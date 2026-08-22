package com.entityfix;

import com.entityfix.command.EntityFixCommand;
import com.entityfix.compat.LithiumCompat;
import com.entityfix.config.EntityFixConfig;
import com.entityfix.profiling.EFStats;
import com.entityfix.topology.TopologyTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EntityFix - entity performance optimizations for Minecraft 1.20.1.
 *
 * <p>Central philosophy: Minecraft should not repeatedly solve a problem whose
 * inputs are unchanged. Every optimization in this mod follows the pattern
 * STATE CHANGE -> INVALIDATE -> CALCULATE -> CACHE -> REUSE, and every module
 * has a vanilla fallback path (see ARCHITECTURE.md).
 */
public class EFMod implements ModInitializer {
    public static final String MOD_ID = "entityfix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static EntityFixConfig config;

    public static EntityFixConfig config() {
        return config;
    }

    @Override
    public void onInitialize() {
        config = EntityFixConfig.load();
        EFStats.setEnabled(config.stats);

        EntityFixCommand.register();

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TopologyTracker.clearAll());

        if (LithiumCompat.isLithiumPresent()) {
            LOGGER.info("EntityFix initialized (mode: {}). Lithium detected: overlapping hot paths "
                    + "are handled by independent injection points; see COMPATIBILITY.md.",
                    config.mode);
        } else {
            LOGGER.info("EntityFix initialized (mode: {}).", config.mode);
        }
    }
}
