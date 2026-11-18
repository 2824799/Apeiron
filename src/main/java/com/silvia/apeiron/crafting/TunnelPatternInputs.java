package com.silvia.apeiron.crafting;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;

/** Uses AE2's network definitions while preserving exact quantities through nested references. */
public final class TunnelPatternInputs {

    private static final Class<?> TUNNEL_ITEM = tunnelItemClass();

    private TunnelPatternInputs() {}

    public interface Definitions {

        Map<UUID, ICraftingPatternDetails> getTunnelPatterns();
    }

    public static Map<UUID, ICraftingPatternDetails> definitions(Object cache) {
        return cache instanceof Definitions definitions ? definitions.getTunnelPatterns() : Collections.emptyMap();
    }

    private static Class<?> tunnelItemClass() {
        try {
            return Class
                .forName("appeng.items.misc.ItemTunnelPattern", false, CraftingGridCache.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static boolean isReference(IAEStack<?> input) {
        return TUNNEL_ITEM != null && input instanceof IAEItemStack item && TUNNEL_ITEM.isInstance(item.getItem());
    }

    public static boolean hasReferences(ICraftingPatternDetails pattern, IAEStack<?>[] inputs) {
        if (TUNNEL_ITEM == null || pattern.isCraftable()) return false;
        for (IAEStack<?> input : inputs) if (isReference(input)) return true;
        return false;
    }

    /** Returns null for malformed or circular references. Missing definitions retain the physical item. */
    public static List<IAEStack<?>> expand(IAEStack<?>[] inputs, Map<UUID, ICraftingPatternDetails> definitions) {
        List<IAEStack<?>> result = new ArrayList<>(inputs.length);
        Set<UUID> path = new HashSet<>();
        for (IAEStack<?> input : inputs) if (!expand(input, BigInteger.ONE, definitions, path, result)) return null;
        return result;
    }

    private static boolean expand(IAEStack<?> input, BigInteger factor, Map<UUID, ICraftingPatternDetails> definitions,
        Set<UUID> path, List<IAEStack<?>> result) {
        if (input == null) return true;
        BigInteger count = BigAEStackValues.get(input)
            .multiply(factor);
        if (!isReference(input)) {
            result.add(BigAEStackValues.copyWithSize(input, count));
            return true;
        }
        NBTTagCompound tag = ((IAEItemStack) input).getItemStack()
            .getTagCompound();
        if (tag == null || !tag.getBoolean("tunnel")) return false;
        UUID id;
        try {
            id = UUID.fromString(tag.getString("tunnelUuid"));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        ICraftingPatternDetails definition = definitions.get(id);
        if (definition == null || !definition.isInputOnly()) {
            result.add(BigAEStackValues.copyWithSize(input, count));
            return true;
        }
        if (!path.add(id)) return false;
        IAEStack<?>[] nested = definition.getCondensedAEInputs();
        if (nested.length == 0) return false;
        for (IAEStack<?> ingredient : nested) if (!expand(ingredient, count, definitions, path, result)) return false;
        path.remove(id);
        return true;
    }

    public static IAEStack<?>[] condensed(ICraftingPatternDetails pattern,
        Map<UUID, ICraftingPatternDetails> definitions) {
        IAEStack<?>[] declared = pattern.getCondensedAEInputs();
        if (!hasReferences(pattern, declared)) return declared;
        List<IAEStack<?>> expanded = expand(declared, definitions);
        return expanded == null ? null : condense(expanded);
    }

    public static IAEStack<?>[] condense(List<IAEStack<?>> inputs) {
        Map<IAEStack<?>, IAEStack<?>> byType = new LinkedHashMap<>();
        for (IAEStack<?> input : inputs) {
            IAEStack<?> existing = byType.get(input);
            if (existing == null) byType.put(input, input.copy());
            else BigAEStackValues.addStorage(existing, input);
        }
        return byType.values()
            .toArray(new IAEStack<?>[0]);
    }
}
