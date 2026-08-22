package com.entityfix.command;

import com.entityfix.EFMod;
import com.entityfix.compat.LithiumCompat;
import com.entityfix.profiling.EFStats;
import com.entityfix.topology.TopologyTracker;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /entityfix status|debug|profile ... - operator-facing diagnostics.
 *
 * <p>Commands are the only place EntityFix produces output; normal operation
 * never logs (except a single init line). Counting work here may be expensive
 * with thousands of entities, which is acceptable for an operator command.
 */
public final class EntityFixCommand {
    private EntityFixCommand() {}

    public static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("entityfix")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("status").executes(ctx -> status(ctx.getSource())))
                .then(literal("debug").executes(ctx -> debug(ctx.getSource())))
                .then(literal("profile")
                        .then(literal("start").executes(ctx -> profiling(ctx.getSource(), true)))
                        .then(literal("stop").executes(ctx -> profiling(ctx.getSource(), false)))
                        .then(literal("clear").executes(ctx -> {
                            EFStats.clear();
                            feedback(ctx.getSource(), "Profiling counters cleared.");
                            return 1;
                        }))
                        .then(literal("dump").executes(ctx -> dump(ctx.getSource())))));
    }

    private static int status(ServerCommandSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append("EntityFix\n");
        sb.append("Version: 0.1.0\n");
        sb.append("Mode: ").append(EFMod.config().mode).append('\n');
        sb.append('\n');
        sb.append("Stats layer: ").append(EFMod.config().stats ? "ENABLED" : "disabled").append('\n');
        sb.append("Topology tracking: ")
                .append(EFMod.config().topology_tracking ? "ENABLED" : "disabled").append('\n');
        sb.append("Path cache: ").append(
                EFMod.config().effective(EFMod.config().path_cache, true) ? "ENABLED" : "disabled").append('\n');
        sb.append("AI sleeping: ").append(EFMod.config().ai_sleep ? "ENABLED" : "not implemented yet").append('\n');
        sb.append("Lithium detected: ").append(LithiumCompat.isLithiumPresent() ? "yes" : "no").append('\n');
        sb.append('\n');
        appendPathfinding(sb);
        appendSensors(sb);
        appendTopology(source, sb);
        sendLines(source, sb.toString());
        return 1;
    }


    private static void appendPathfinding(StringBuilder sb) {
        long hits = EFStats.get(EFStats.Counter.PATH_CACHE_HITS);
        long misses = EFStats.get(EFStats.Counter.PATH_CACHE_MISSES);
        sb.append("Pathfinding:\n");
        sb.append(String.format("    Calls: %,d%n", EFStats.get(EFStats.Counter.NAV_PATH_CALLS)));
        sb.append(String.format("    Cache hits: %,d%n", hits));
        sb.append(String.format("    Cache misses: %,d%n", misses));
        if (hits + misses > 0) {
            sb.append(String.format("    Hit rate: %.1f%%%n", 100.0 * hits / (hits + misses)));
        }
        sb.append('\n');
    }

    private static void appendSensors(StringBuilder sb) {
        sb.append("Sensors:\n");
        sb.append(String.format("    Sense calls: %,d%n", EFStats.get(EFStats.Counter.SENSOR_SENSES)));
        sb.append(String.format("    Brain ticks: %,d%n", EFStats.get(EFStats.Counter.BRAIN_TICKS)));
        sb.append('\n');
    }

    private static void appendTopology(ServerCommandSource source, StringBuilder sb) {
        sb.append("Topology:\n");
        sb.append(String.format("    Block changes seen: %,d%n",
                EFStats.get(EFStats.Counter.TOPOLOGY_CHANGES_SEEN)));
        sb.append(String.format("    Section invalidations: %,d%n",
                EFStats.get(EFStats.Counter.TOPOLOGY_BUMPS)));
        ServerWorld world = source.getWorld();
        if (world != null) {
            sb.append(String.format("    Tracked sections in %s: %,d%n",
                    world.getRegistryKey().getValue(), TopologyTracker.entryCount(world)));
        }
    }

    private static int debug(ServerCommandSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append("EntityFix debug\n");
        sb.append(String.format("    Sensor sense total: %s ms%n",
                ms(EFStats.timerNanos(EFStats.Timer.SENSOR_SENSE))));
        sb.append(String.format("    Pathfinding total: %s ms (timer pending source verification)%n",
                ms(EFStats.timerNanos(EFStats.Timer.NAV_PATH))));
        sb.append(String.format("    Path cache invalidations: %,d%n",
                EFStats.get(EFStats.Counter.PATH_CACHE_INVALIDATIONS)));
        sb.append(String.format("    Loaded entities in current world: %,d%n",
                countEntities(source.getWorld())));
        sendLines(source, sb.toString());
        return 1;
    }

    private static int dump(ServerCommandSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append("EntityFix counter dump\n");
        for (EFStats.Counter c : EFStats.Counter.values()) {
            sb.append(String.format("    %-28s %,d%n", c.name(), EFStats.get(c)));
        }
        for (EFStats.Timer t : EFStats.Timer.values()) {
            sb.append(String.format("    %-28s %s ms over %,d calls%n", t.name(),
                    ms(EFStats.timerNanos(t)), EFStats.timerCalls(t)));
        }
        sendLines(source, sb.toString());
        return 1;
    }

    private static int profiling(ServerCommandSource source, boolean enable) {
        EFStats.setEnabled(enable);
        feedback(source, enable
                ? "Profiling started."
                : "Profiling stopped (counters kept; use /entityfix profile clear to reset).");
        return 1;
    }

    private static long countEntities(ServerWorld world) {
        if (world == null) {
            return 0;
        }
        long n = 0;
        for (@SuppressWarnings("unused") var ignored : world.iterateEntities()) {
            n++;
        }
        return n;
    }

    private static String ms(long nanos) {
        return String.format("%,.1f", nanos / 1_000_000.0);
    }

    private static void feedback(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal("[EntityFix] " + message), false);
    }

    private static void sendLines(ServerCommandSource source, String block) {
        for (String line : block.split("\n")) {
            final String l = line;
            source.sendFeedback(() -> Text.literal(l).formatted(Formatting.GRAY), false);
        }
    }
}
