# EntityFix

**Entity performance optimizations for Minecraft 1.20.1 (Fabric)**

EntityFix reduces the main-thread CPU cost of entity simulation by eliminating
*redundant* computation - not by throttling AI, skipping ticks, or lowering
simulation accuracy. May cause problems with 
