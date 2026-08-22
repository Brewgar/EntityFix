package com.entityfix;

import com.entityfix.cache.BoundedVersionMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundedVersionMapTest {

    @Test
    void incrementsAreMonotonicPerKey() {
        BoundedVersionMap map = new BoundedVersionMap(100);
        assertEquals(0, map.get(42L));
        map.increment(42L);
        map.increment(42L);
        map.increment(42L);
        assertEquals(3, map.get(42L));
        assertEquals(0, map.get(43L));
        assertEquals(1, map.size());
    }

    @Test
    void resetNeverFalselyValidatesStaleVersion() {
        BoundedVersionMap map = new BoundedVersionMap(4);
        // Fill to the bound.
        for (long k = 1; k <= 4; k++) {
            map.increment(k);
        }
        long staleObservedVersion = map.get(1L);
        assertEquals(1, staleObservedVersion);

        // Overflow triggers a reset; the stale observed version must NOT
        // validate against the fresh (0) reading.
        for (long k = 100; k < 110; k++) {
            map.increment(k);
        }
        assertTrue(map.resets() >= 1);
        assertNotEquals(staleObservedVersion, map.get(1L),
                "A reset must never reproduce an old version value (would falsely validate stale caches)");
    }

    @Test
    void boundIsRespected() {
        BoundedVersionMap map = new BoundedVersionMap(8);
        for (long k = 0; k < 1000; k++) {
            map.increment(k);
            assertTrue(map.size() <= 8, "size must stay bounded");
        }
        assertTrue(map.resets() >= 1);
    }

    @Test
    void clearForcesRecomputeDirection() {
        BoundedVersionMap map = new BoundedVersionMap(10);
        map.increment(7L);
        long observed = map.get(7L);
        map.clear();
        assertEquals(0, map.get(7L));
        assertNotEquals(observed, map.get(7L));
    }
}
