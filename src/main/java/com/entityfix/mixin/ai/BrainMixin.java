package com.entityfix.mixin.ai;

import com.entityfix.profiling.EFStats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Profiling instrumentation for {@link Brain} ticks.
 *
 * <p>Vanilla bottleneck being measured: brain-based mobs (villagers, piglins,
 * etc.) re-run their full sensor/task pipeline on fixed cadences. This mixin
 * records invocation counts so later caching decisions are driven by data.
 * It never alters behavior.
 */
@Mixin(Brain.class)
public abstract class BrainMixin {

    @Inject(method = "tick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"))
    private void entityfix$brainTick(ServerWorld world, LivingEntity entity, CallbackInfo ci) {
        EFStats.inc(EFStats.Counter.BRAIN_TICKS);
    }
}
