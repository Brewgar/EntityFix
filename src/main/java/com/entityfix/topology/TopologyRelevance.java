package com.entityfix.topology;

import net.minecraft.block.BlockState;

/**
 * Cheap filter deciding whether a block state change is potentially relevant
 * to pathfinding and therefore must bump the section's topology version.
 *
 * <p>Conservative by design: when in doubt, report relevant. A missed bump
 * could validate a stale cached path (unsafe); a spurious bump only costs a
 * recomputation (acceptable).
 *
 * <p>The current rule bumps whenever either the old or new state blocks
 * movement or carries a fluid. This covers:
 * <ul>
 *   <li>solid &lt;-&gt; air transitions</li>
 *   <li>doors, trapdoors, fence gates (their material blocks movement, so any
 *       open/close state change matches)</li>
 *   <li>fences, walls, ladders-adjacent solids</li>
 *   <li>water/lava flow and removal</li>
 * </ul>
 * while skipping pure-decoration updates (redstone wire, torches, note
 * blocks...) that cannot change path geometry.
 */
public final class TopologyRelevance {
    private TopologyRelevance() {}

    public static boolean isPathRelevant(BlockState oldState, BlockState newState) {
        if (oldState == newState) {
            return false;
        }
        return oldState.blocksMovement()
                || newState.blocksMovement()
                || !oldState.getFluidState().isEmpty()
                || !newState.getFluidState().isEmpty();
    }
}
