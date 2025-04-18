/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import org.h2.mvstore.cache.CacheLongKeyLIRS;
import org.h2.test.TestBase;

/**
 * Tests the cache algorithm.
 */
public class TestCacheLongKeyLIRS extends TestBase {

    private static final int MEMORY_OVERHEAD = CacheLongKeyLIRS.getMemoryOverhead();

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().testFromMain();
    }

    @Override
    public void test() throws Exception {
        testCache();
    }

    private void testCache() {
        testRandomSmallCache();
        testEdgeCases();
        testSize();
        testClear();
        testGetPutPeekRemove();
        testPruneStack();
        testLimitHot();
        testLimitNonResident();
        testLimitMemory();
        testScanResistance();
        testRandomOperations();
    }

    private void testRandomSmallCache() {
        Random r = new Random(1);
        for (int i = 0; i < 10000; i++) {
            int j = 0;
            StringBuilder buff = new StringBuilder();
            int maxSize = 1 + r.nextInt(10);
            buff.append("size:").append(maxSize).append('\n');
            CacheLongKeyLIRS<Integer> test = createCache(maxSize, maxSize);
            for (; j < 30; j++) {
                String lastState = toString(test);
                try {
                    int key = r.nextInt(5);
                    switch (r.nextInt(3)) {
                        case 0:
                            int memory = r.nextInt(5) + 1;
                            buff.append("add ").append(key).append(' ').
                                    append(memory).append('\n');
                            test.put(key, j, memory);
                            break;
                        case 1:
                            buff.append("remove ").append(key).append('\n');
                            test.remove(key);
                            break;
                        case 2:
                            buff.append("get ").append(key).append('\n');
                            test.get(key);
                    }
                    verify(test, 0, null);
                } catch (Throwable ex) {
                    println(i + "\n" + buff + "\n" + lastState + "\n" + toString(test));
                    throw ex;
                }
            }
        }
    }

    private void testEdgeCases() {
        CacheLongKeyLIRS<Integer> test = createCache(1, 1);
        test.put(1, 10, 100);
        assertEquals(0, test.size());
        assertThrows(IllegalArgumentException.class, () -> test.put(1, null, 100));
        assertThrows(IllegalArgumentException.class, () -> test.setMaxMemory(0));
    }

    private void testSize() {
        verifyMapSize(7, 16);
        verifyMapSize(13, 32);
        verifyMapSize(25, 64);
        verifyMapSize(49, 128);
        verifyMapSize(97, 256);
        verifyMapSize(193, 512);
        verifyMapSize(385, 1024);
        verifyMapSize(769, 2048);

        CacheLongKeyLIRS<Integer> test;

        test = createCache(1000 * 16, 1000);
        for (int j = 0; j < 2000; j++) {
            test.put(j, j);
        }
        // for a cache of size 1000,
        // there are 32 cold entries (about 1/32).
        assertEquals(32, test.size() - test.sizeHot());
        // at most as many non-resident elements
        // as there are entries in the stack
        assertEquals(1000, test.size());
        assertEquals(1000, test.sizeNonResident());
    }

    private void verifyMapSize(int elements, int expectedMapSize) {
        CacheLongKeyLIRS<Integer> test;
        test = createCache((elements - 1) * 16, elements - 1);
        for (int i = 0; i < elements - 1; i++) {
            test.put(i, i * 10);
        }
        assertTrue(test.sizeMapArray() + "<" + expectedMapSize,
                test.sizeMapArray() < expectedMapSize);
        test = createCache(elements * 16, elements);
        for (int i = 0; i < elements + 1; i++) {
            test.put(i, i * 10);
        }
        assertEquals(expectedMapSize, test.sizeMapArray());
        test = createCache(elements * 2 * 16, elements * 2);
        for (int i = 0; i < elements * 2; i++) {
            test.put(i, i * 10);
        }
        assertTrue(test.sizeMapArray() + ">" + expectedMapSize,
                test.sizeMapArray() > expectedMapSize);
    }

    private void testGetPutPeekRemove() {
        CacheLongKeyLIRS<Integer> test = createCache(4, 4);
        test.put(1,  10, 1);
        test.put(2,  20, 1);
        test.put(3,  30, 1);
        assertNull(test.peek(4));
        assertNull(test.get(4));
        test.put(4,  40, 1);
        verify(test, 4, "stack: 4 3 2 1 cold: non-resident:");
        // move middle to front
        assertEquals(30, test.get(3).intValue());
        assertEquals(20, test.get(2).intValue());
        assertEquals(20, test.peek(2).intValue());
        // already on (an optimization)
        assertEquals(20, test.get(2).intValue());
        assertEquals(10, test.peek(1).intValue());
        assertEquals(10, test.get(1).intValue());
        verify(test, 4, "stack: 1 2 3 4 cold: non-resident:");
        test.put(3,  30, 1);
        verify(test, 4, "stack: 3 1 2 4 cold: non-resident:");
        // 5 is cold; will make 4 non-resident
        test.put(5,  50, 1);
        verify(test, 4, "stack: 5 3 1 2 cold: 5 non-resident: 4");
        assertEquals(1 + MEMORY_OVERHEAD, test.getMemory(1));
        assertEquals(1 + MEMORY_OVERHEAD, test.getMemory(5));
        assertEquals(0, test.getMemory(4));
        assertEquals(0, test.getMemory(100));
        assertNotNull(test.peek(4));
        assertNotNull(test.get(4));
        assertEquals(10, test.get(1).intValue());
        assertEquals(20, test.get(2).intValue());
        assertEquals(30, test.get(3).intValue());
        verify(test, 5, "stack: 3 2 1 cold: 4 5 non-resident:");
        assertEquals(50, test.get(5).intValue());
        verify(test, 5, "stack: 5 3 2 1 cold: 5 4 non-resident:");
        assertEquals(50, test.get(5).intValue());
        verify(test, 5, "stack: 5 3 2 cold: 1 4 non-resident:");

        // remove
        assertEquals(50, test.remove(5).intValue());
        assertNull(test.remove(5));
        verify(test, 4, "stack: 3 2 1 cold: 4 non-resident:");
        assertNotNull(test.remove(4));
        verify(test, 3, "stack: 3 2 1 cold: non-resident:");
        assertNull(test.remove(4));
        verify(test, 3, "stack: 3 2 1 cold: non-resident:");
        test.put(4,  40, 1);
        test.put(5,  50, 1);
        verify(test, 4, "stack: 5 4 3 2 cold: 5 non-resident: 1");
        test.get(5);
        test.get(2);
        test.get(3);
        test.get(4);
        verify(test, 4, "stack: 4 3 2 5 cold: 2 non-resident: 1");
        assertEquals(50, test.remove(5).intValue());
        verify(test, 3, "stack: 4 3 2 cold: non-resident: 1");
        assertEquals(20, test.remove(2).intValue());
        assertFalse(test.containsKey(1));
        assertEquals(10, test.remove(1).intValue());
        assertFalse(test.containsKey(1));
        verify(test, 2, "stack: 4 3 cold: non-resident:");
        test.put(1,  10, 1);
        test.put(2,  20, 1);
        verify(test, 4, "stack: 2 1 4 3 cold: non-resident:");
        test.get(1);
        test.get(3);
        test.get(4);
        verify(test, 4, "stack: 4 3 1 2 cold: non-resident:");
        assertEquals(10, test.remove(1).intValue());
        verify(test, 3, "stack: 4 3 2 cold: non-resident:");
        test.remove(2);
        test.remove(3);
        test.remove(4);

        // test clear
        test.clear();
        verify(test, 0, "stack: cold: non-resident:");

        // strange situation where there is only a non-resident entry
        test.put(1, 10, 1);
        test.put(2, 20, 1);
        test.put(3, 30, 1);
        test.put(4, 40, 1);
        test.put(5, 50, 1);
        assertTrue(test.containsValue(50));
        verify(test, 4, "stack: 5 4 3 2 cold: 5 non-resident: 1");
        // 1 was non-resident, so this should make it hot
        test.put(1, 10, 1);
        verify(test, 4, "stack: 1 5 4 3 cold: 2 non-resident: 5");
        assertTrue(test.containsValue(50));
        test.remove(2);
        test.remove(3);
        test.remove(4);
        verify(test, 1, "stack: 1 cold: non-resident: 5");
        assertTrue(test.containsKey(1));
        test.remove(1);
        assertFalse(test.containsKey(1));
        verify(test, 0, "stack: cold: non-resident: 5");
        assertFalse(test.containsKey(5));
        assertTrue(test.isEmpty());

        // verify that converting a hot to cold entry will prune the stack
        test.clear();
        test.put(1, 10, 1);
        test.put(2, 20, 1);
        test.put(3, 30, 1);
        test.put(4, 40, 1);
        test.put(5, 50, 1);
        test.get(4);
        test.get(3);
        verify(test, 4, "stack: 3 4 5 2 cold: 5 non-resident: 1");
        test.put(6, 60, 1);
        verify(test, 4, "stack: 6 3 4 5 2 cold: 6 non-resident: 5 1");
        // this will prune the stack (remove entry 5 as entry 2 becomes cold)
        test.get(6);
        verify(test, 4, "stack: 6 3 4 cold: 2 non-resident: 5 1");
    }

    private void testPruneStack() {
        CacheLongKeyLIRS<Integer> test = createCache(5, 5);
        for (int i = 0; i < 7; i++) {
            test.put(i, i * 10, 1);
        }
        verify(test, 5, "stack: 6 5 4 3 2 1 cold: 6 non-resident: 5 0");
        test.get(4);
        test.get(3);
        test.get(2);
        verify(test, 5, "stack: 2 3 4 6 5 1 cold: 6 non-resident: 5 0");
        // this call needs to prune the stack
        test.remove(1);
        verify(test, 4, "stack: 2 3 4 6 cold: non-resident: 5 0");
        test.put(0,  0, 1);
        test.put(1,  10, 1);
        // the stack was not pruned, the following will fail
        verify(test, 5, "stack: 1 0 2 3 4 cold: 1 non-resident: 6 5");
    }

    private void testClear() {
        CacheLongKeyLIRS<Integer> test = createCache(40, 4);
        for (int i = 0; i < 5; i++) {
            test.put(i, 10 * i, 9);
        }
        verify(test, 4, 9, "stack: 4 3 2 1 cold: 4 non-resident: 0");
        for (Entry<Long, Integer> e : test.entrySet()) {
            assertTrue(e.getKey() >= 1 && e.getKey() <= 4);
            assertTrue(e.getValue() >= 10 && e.getValue() <= 40);
        }
        for (int x : test.values()) {
            assertTrue(x >= 10 && x <= 40);
        }
        for (long x : test.keySet()) {
            assertTrue(x >= 1 && x <= 4);
        }
        assertEquals(40 + 4 * MEMORY_OVERHEAD, test.getMaxMemory());
        assertEquals(36 + 4 * MEMORY_OVERHEAD, test.getUsedMemory());
        assertEquals(4, test.size());
        assertEquals(3,  test.sizeHot());
        assertEquals(1,  test.sizeNonResident());
        assertFalse(test.isEmpty());

        long maxMemory = test.getMaxMemory();
        // changing the limit is not supposed to modify the map
        test.setMaxMemory(10);
        assertEquals(10, test.getMaxMemory());
        test.setMaxMemory(maxMemory);
        verify(test, 4, 9, "stack: 4 3 2 1 cold: 4 non-resident: 0");

        test.putAll(test.getMap());
        if (MEMORY_OVERHEAD < 7) {
            verify(test, 2, 16, "stack: 4 cold: 3 non-resident: 2 1 0");
        } else {
            verify(test, 3, 16, "stack: 4 3 cold: 2 non-resident: 1 0");
        }

        test.clear();
        verify(test, 0, 16, "stack: cold: non-resident:");

        assertEquals(40 + 4 * MEMORY_OVERHEAD, test.getMaxMemory());
        assertEquals(0, test.getUsedMemory());
        assertEquals(0, test.size());
        assertEquals(0, test.sizeHot());
        assertEquals(0, test.sizeNonResident());
        assertTrue(test.isEmpty());
    }

    private void testLimitHot() {
        CacheLongKeyLIRS<Integer> test = createCache(100 * 16, 100);
        for (int i = 0; i < 300; i++) {
            test.put(i, 10 * i);
        }
        assertEquals(100, test.size());
        assertEquals(200, test.sizeNonResident());
        assertEquals(96, test.sizeHot());
    }

    private void testLimitNonResident() {
        CacheLongKeyLIRS<Integer> test = createCache(4, 4);
        for (int i = 0; i < 20; i++) {
            test.put(i, 10 * i, 1);
        }
        verify(test, 4, "stack: 19 18 17 16 15 14 13 12 11 10 9 8 7 6 5 4 3 2 1 " +
                "cold: 19 non-resident: 18 17 16 15 14 13 12 11 10 9 8 7 6 5 4 0");
    }

    private void testLimitMemory() {
        CacheLongKeyLIRS<Integer> test = createCache(4, 4);
        for (int i = 0; i < 5; i++) {
            test.put(i, 10 * i, 1);
        }
        verify(test, 4, "stack: 4 3 2 1 cold: 4 non-resident: 0");
        assertTrue("" + test.getUsedMemory(), test.getUsedMemory() <= 4 * (MEMORY_OVERHEAD + 1));
        test.put(6, 60, 3 + 2 * MEMORY_OVERHEAD);
        verify(test, 4, "stack: 6 4 3 cold: 6 non-resident: 2 1 4 0");
        assertTrue("" + test.getUsedMemory(), test.getUsedMemory() <= 4 * (MEMORY_OVERHEAD + 1));
        test.put(7, 70, 3 + 2 * MEMORY_OVERHEAD);
        verify(test, 4, "stack: 7 6 4 3 cold: 7 non-resident: 6 2 1 4 0");
        assertTrue("" + test.getUsedMemory(), test.getUsedMemory() <= 4 * (MEMORY_OVERHEAD + 1));
        test.put(8, 80, 4 + 3 * MEMORY_OVERHEAD);
        verify(test, 4, "stack: 8 cold: non-resident:");
        assertTrue("" + test.getUsedMemory(), test.getUsedMemory() <= 4 * (MEMORY_OVERHEAD + 1));
    }

    private void testScanResistance() {
        boolean log = false;
        int size = 20;
        // cache size 11 (10 hot, 2 cold)
        CacheLongKeyLIRS<Integer> test = createCache((size / 2 + 2) * 16, (size / 2) + 2);
        // init the cache with some dummy entries
        for (int i = 0; i < size; i++) {
            test.put(-i, -i * 10);
        }
        verify(test, 0, null);
        // init with 0..9, ensure those are hot entries
        for (int i = 0; i < size / 2; i++) {
            test.put(i, i * 10);
            test.get(i);
            if (log) {
                println("get " + i + " -> " + test);
            }
        }
        verify(test, 0, null);
        // read 0..9, add 10..19 (cold)
        for (int i = 0; i < size; i++) {
            Integer x = test.get(i);
            Integer y = test.peek(i);
            if (i < size / 2) {
                assertNotNull("i: " + i, x);
                assertNotNull("i: " + i, y);
                assertEquals(i * 10, x.intValue());
                assertEquals(i * 10, y.intValue());
            } else {
                assertNull(x);
                assertNull(y);
                test.put(i, i * 10);
                // peek should have no effect
                assertEquals(i * 10, test.peek(i).intValue());
            }
            if (log) {
                System.out.println("get " + i + " -> " + test);
            }
            verify(test, 0, null);
        }

        // ensure 0..9 are hot, 10..17 are not resident, 18..19 are cold
        for (int i = 0; i < size; i++) {
            Integer x = test.get(i);
            if (i < size / 2 || i == size - 1 || i == size - 2) {
                assertNotNull("i: " + i, x);
                assertEquals(i * 10, x.intValue());
            }
            verify(test, 0, null);
        }
    }

    private void testRandomOperations() {
        boolean log = false;
        int size = 10;
        Random r = new Random(1);
        for (int j = 0; j < 100; j++) {
            CacheLongKeyLIRS<Integer> test = createCache(size / 2 * 16, size / 2);
            HashMap<Integer, Integer> good = new HashMap<>();
            for (int i = 0; i < 10000; i++) {
                int key = r.nextInt(size);
                int value = r.nextInt();
                switch (r.nextInt(3)) {
                case 0:
                    if (log) {
                        System.out.println(i + " put " + key + " " + value);
                    }
                    good.put(key, value);
                    test.put(key, value);
                    break;
                case 1:
                    if (log) {
                        System.out.println(i + " get " + key);
                    }
                    Integer a = good.get(key);
                    Integer b = test.get(key);
                    if (a == null) {
                        assertNull(b);
                    } else if (b != null) {
                        assertEquals(a, b);
                    }
                    break;
                case 2:
                    if (log) {
                        System.out.println(i + " remove " + key);
                    }
                    good.remove(key);
                    test.remove(key);
                    break;
                }
                if (log) {
                    System.out.println(" -> " + toString(test));
                }
            }
            verify(test, 0, null);
        }
    }

    private static <V> String toString(CacheLongKeyLIRS<V> cache) {
        StringBuilder buff = new StringBuilder();
        buff.append("mem: " + cache.getUsedMemory());
        buff.append(" stack:");
        for (long k : cache.keys(false,  false)) {
            buff.append(' ').append(k);
        }
        buff.append(" cold:");
        for (long k : cache.keys(true,  false)) {
            buff.append(' ').append(k);
        }
        buff.append(" non-resident:");
        for (long k : cache.keys(true,  true)) {
            buff.append(' ').append(k);
        }
        return buff.toString();
    }

    private <V> void verify(CacheLongKeyLIRS<V> cache, int expectedMemory, String expected) {
        verify(cache, expectedMemory, 1, expected);
    }

    private <V> void verify(CacheLongKeyLIRS<V> cache, int expectedMemory, int valueSize, String expected) {
        if (expected != null) {
            String got = toString(cache);
            assertEquals("mem: " + expectedMemory * (valueSize + MEMORY_OVERHEAD) + ' '
                    + expected, got);
        }
        int mem = 0;
        for (long k : cache.keySet()) {
            mem += cache.getMemory(k);
        }
        assertEquals(mem, cache.getUsedMemory());
        List<Long> stack = cache.keys(false, false);
        List<Long> cold = cache.keys(true, false);
        List<Long> nonResident = cache.keys(true, true);
        assertEquals(nonResident.size(), cache.sizeNonResident());
        HashSet<Long> hot = new HashSet<>(stack);
        hot.removeAll(cold);
        hot.removeAll(nonResident);
        assertEquals(hot.size(), cache.sizeHot());
        assertEquals(hot.size() + cold.size(), cache.size());
        if (stack.size() > 0) {
            long lastStack = stack.get(stack.size() - 1);
            assertTrue(hot.contains(lastStack));
        }
    }

    private static <V> CacheLongKeyLIRS<V> createCache(int maxSize, int elements) {
        CacheLongKeyLIRS.Config cc = new CacheLongKeyLIRS.Config();
        cc.maxMemory = maxSize + elements * MEMORY_OVERHEAD;
        cc.segmentCount = 1;
        cc.stackMoveDistance = 0;
        return new CacheLongKeyLIRS<>(cc);
    }

}
