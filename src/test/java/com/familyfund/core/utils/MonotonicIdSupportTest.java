package com.familyfund.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonotonicIdSupportTest {

    @Test
    @DisplayName("Frozen millisecond burst produces zero duplicates across 50,000 IDs")
    void frozenMillisecond_ShouldProduceUniqueSortedIds() {
        MonotonicIdSupport support = new MonotonicIdSupport("CLN", 20);
        long frozenNow = 1_764_250_000_123L;
        Set<String> seen = new HashSet<>();
        int count = 50_000;

        String prev = null;
        for (int i = 0; i < count; i++) {
            String id = support.generate(frozenNow);
            assertEquals(17, id.length());
            assertTrue(id.startsWith("CLN"));
            for (int c = 11; c < id.length(); c++) {
                assertTrue(MonotonicIdSupport.ALPHABET.indexOf(id.charAt(c)) >= 0,
                        "Unexpected char in Crockford suffix: " + id);
            }
            if (prev != null) {
                assertNotEquals(prev, id);
            }
            prev = id;
            seen.add(id);
        }

        assertEquals(count, seen.size());
    }

    @Test
    @DisplayName("Suffix exhaustion wraps safely by advancing logical timestamp")
    void suffixExhaustion_ShouldAdvanceLogicalTimestampWithoutCollision() {
        MonotonicIdSupport support = new MonotonicIdSupport("CLN", 20);
        long baseMillis = 1_764_250_000_000L;
        support.seedForTesting(baseMillis, MonotonicIdSupport.SUFFIX_SPACE - 2);

        Set<String> ids = new HashSet<>();
        ids.add(support.generate(baseMillis));
        ids.add(support.generate(baseMillis));
        ids.add(support.generate(baseMillis));
        ids.add(support.generate(baseMillis - 500)); // simulated clock skew backward

        assertEquals(4, ids.size());
    }

    @Test
    @DisplayName("Concurrent threads produce unique IDs without synchronization races")
    void concurrentGeneration_ShouldRemainUnique() throws Exception {
        MonotonicIdSupport support = new MonotonicIdSupport("MEM", 20);
        int threads = 8;
        int perThread = 2_500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<String>>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tasks.add(() -> {
                    List<String> batch = new ArrayList<>(perThread);
                    for (int i = 0; i < perThread; i++) {
                        batch.add(support.generate());
                    }
                    return batch;
                });
            }

            Set<String> all = Collections.synchronizedSet(new HashSet<>());
            List<Future<List<String>>> futures = pool.invokeAll(tasks);
            for (Future<List<String>> future : futures) {
                all.addAll(future.get());
            }
            assertEquals(threads * perThread, all.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
