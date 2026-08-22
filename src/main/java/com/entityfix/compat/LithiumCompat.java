package com.entityfix.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Detection of neighboring optimization mods.
 *
 * <p>EntityFix deliberately shares no code paths with Lithium: all mixins use
 * independent injection points into vanilla methods, so coexistence is by
 * construction. This class only feeds diagnostics (/entityfix status) and is
 * the place to grow explicit integration logic if profiling ever shows a real
 * conflict.
 */
public final class LithiumCompat {
    private LithiumCompat() {}

    private static final boolean LITHIUM = FabricLoader.getInstance().isModLoaded("lithium");

    public static boolean isLithiumPresent() {
        return LITHIUM;
    }
}
