package com.entityfix.mixin;

import com.entityfix.EFMod;
import com.entityfix.profiling.EFStats;
import com.entityfix.topology.TopologyRelevance;
import com.entityfix.topology.TopologyTracker;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bumps chunk-section topology versions on pathfinding-relevant block changes.
 *
 * <p>Vanilla bottleneck: nothing tracks "terrain relevant to pathing changed".
 * Consumers therefore cannot know whether a previously computed answer (e.g. a
 * path) is still valid and must poll/recompute unconditionally.
 *
 * <p>Injection point note (verified against yarn 1.20.1): neither
 * {@code ServerWorld} nor {@code ClientWorld} declares {@code setBlockState};
 * the concrete 3-arg implementation lives on {@link World}. We therefore
 * target World and filter to the server side in the handler - one cheap
 * instanceof on the client side, which keeps client behavior untouched.
 *
 * <p>The check runs at HEAD because only there is the previous state readable.
 * Bumping slightly before the write is indistinguishable for consumers (same
 * thread); if the update subsequently fails we performed one harmless extra
 * bump, which is always the safe failure direction.
 */
@Mixin(World.class)
public abstract class WorldMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",
            at = @At("HEAD"))
    private void entityfix$onSetBlockState(BlockPos pos, BlockState state, int flags,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!EFMod.config().topology_tracking) {
            return;
        }
        Object self = this;
        if (!(self instanceof ServerWorld world)) {
            return;
        }
        BlockState previous = world.getBlockState(pos);
        if (TopologyRelevance.isPathRelevant(previous, state)) {
            EFStats.inc(EFStats.Counter.TOPOLOGY_CHANGES_SEEN);
            TopologyTracker.bump(world, pos);
            EFStats.inc(EFStats.Counter.TOPOLOGY_BUMPS);
        }
    }
}
