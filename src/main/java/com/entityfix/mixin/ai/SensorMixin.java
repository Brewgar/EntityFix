package com.entityfix.mixin.ai;

import com.entityfix.profiling.EFStats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Profiling instrumentation for sensor execution.
 *
 * <p>Vanilla bottleneck being measured: sensors re-run on their senseInterval
 * cadence and repeatedly rescan nearby entities even when nothing relevant
 * changed. This mixin records how often and how long that work takes so later
 * caching decisions are driven by data. It never alters behavior.
 *
 * <p>Target note (verified against yarn 1.20.1): {@code sense} is protected
 * abstract; {@code tick(ServerWorld, LivingEntity)} is the public final
 * interval-gated wrapper every brain calls, so that is our hook.
 */
@Mixin(Sensor.class)
public abstract class SensorMixin {

    private static final ThreadLocal<Long> SENSE_START = new ThreadLocal<>();

    @Inject(method = "tick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"))
    private void entityfix$senseBegin(ServerWorld world, LivingEntity entity, CallbackInfo ci) {
        if (!EFStats.isEnabled()) {
            return;
        }
        EFStats.inc(EFStats.Counter.SENSOR_SENSES);
        SENSE_START.set(System.nanoTime());
    }

    @Inject(method = "tick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("TAIL"))
    private void entityfix$senseEnd(ServerWorld world, LivingEntity entity, CallbackInfo ci) {
        Long start = SENSE_START.get();
        if (start != null) {
            EFStats.addTimer(EFStats.Timer.SENSOR_SENSE, System.nanoTime() - start);
            SENSE_START.remove();
        }
    }
}
