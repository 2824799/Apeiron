package com.silvia.apeiron.ae.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.storage.IMEInventoryHandler;

/** Removes duplicate mounts of a physical cell's channel inside one provider. */
public final class StorageCellMounts {

    private StorageCellMounts() {}

    @SuppressWarnings("rawtypes")
    public static List<IMEInventoryHandler> unique(final List<IMEInventoryHandler> handlers) {
        if (handlers == null || handlers.size() < 2) return handlers;
        final Map<Object, Set<String>> seen = new IdentityHashMap<>();
        List<IMEInventoryHandler> unique = null;
        for (int index = 0; index < handlers.size(); index++) {
            final IMEInventoryHandler handler = handlers.get(index);
            final Object identity = identity(handler);
            final boolean duplicate = identity != null && !seen.computeIfAbsent(identity, ignored -> new HashSet<>())
                .add(
                    handler.getStackType()
                        .getId());
            if (duplicate) {
                if (unique == null) unique = new ArrayList<>(handlers.subList(0, index));
            } else if (unique != null) unique.add(handler);
        }
        return unique == null ? handlers : unique;
    }

    private static Object identity(final Object inventory) {
        Object current = inventory;
        final Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            if (current instanceof StorageInventoryIdentity) {
                return ((StorageInventoryIdentity) current).getStorageInventoryIdentity();
            }
            if (!(current instanceof IMEInventoryHandler)) return null;
            current = ((IMEInventoryHandler<?>) current).getInternal();
        }
        return null;
    }
}
