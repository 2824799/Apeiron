package com.silvia.apeiron.ae.crafting.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import org.junit.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public class CraftingPatternComparatorTest {

    @Test
    public void collisionsRemainSeparateButEqualDecodedCopiesMerge() {
        CraftingPatternComparator comparator = new CraftingPatternComparator();
        TreeMap<ICraftingPatternDetails, Integer> tasks = new TreeMap<>(comparator);
        for (int i = 0; i < 128; i++) tasks.put(pattern(i, 0, 7), i);
        assertEquals(128, tasks.size());
        for (int i = 0; i < 128; i++) {
            assertEquals(Integer.valueOf(i), tasks.get(pattern(i, 0, 7)));
            tasks.put(pattern(i, 0, 7), i + 1);
        }
        assertEquals(128, tasks.size());
        for (int i = 0; i < 128; i++) assertEquals(Integer.valueOf(i + 1), tasks.remove(pattern(i, 0, 7)));
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void priorityHashAndCollisionOrderSurviveCopiesAndSortedReload() {
        List<ICraftingPatternDetails> patterns = new ArrayList<>();
        for (int i = 0; i < 40; i++) patterns.add(pattern(i, i % 3, i % 5));
        Collections.shuffle(patterns, new Random(17));
        CraftingPatternComparator comparator = new CraftingPatternComparator();
        TreeMap<ICraftingPatternDetails, Integer> tasks = new TreeMap<>(comparator);
        for (int i = 0; i < patterns.size(); i++) tasks.put(patterns.get(i), i);
        TreeMap<ICraftingPatternDetails, Integer> backup = new TreeMap<>(comparator);
        backup.putAll(tasks);
        assertEquals(tasks, backup);
        TreeMap<ICraftingPatternDetails, Integer> restored = new TreeMap<>(new CraftingPatternComparator());
        tasks.forEach((key, value) -> restored.put(copy(key), value));
        assertEquals(new ArrayList<>(tasks.keySet()), new ArrayList<>(restored.keySet()));
        assertEquals(tasks, restored);
        List<ICraftingPatternDetails> sorted = new ArrayList<>(tasks.keySet());
        for (ICraftingPatternDetails a : sorted) for (ICraftingPatternDetails b : sorted) {
            int ab = Integer.signum(comparator.compare(a, b));
            assertEquals(-ab, Integer.signum(comparator.compare(b, a)));
            assertEquals(ab, Integer.signum(comparator.compare(copy(a), b)));
            for (ICraftingPatternDetails c : sorted) {
                if (ab <= 0 && comparator.compare(b, c) <= 0) assertTrue(comparator.compare(a, c) <= 0);
            }
        }
    }

    private static ICraftingPatternDetails copy(ICraftingPatternDetails original) {
        return pattern(Integer.parseInt(original.toString()), original.getPriority(), original.hashCode());
    }

    private static ICraftingPatternDetails pattern(int identity, int priority, int hash) {
        return (ICraftingPatternDetails) Proxy.newProxyInstance(
            ICraftingPatternDetails.class.getClassLoader(),
            new Class<?>[] { ICraftingPatternDetails.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "hashCode":
                        return hash;
                    case "equals":
                        return args[0] instanceof ICraftingPatternDetails && Integer.toString(identity)
                            .equals(args[0].toString());
                    case "getPriority":
                        return priority;
                    case "toString":
                        return Integer.toString(identity);
                    default:
                        throw new AssertionError("Comparator decoded a recipe: " + method.getName());
                }
            });
    }
}
