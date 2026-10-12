package com.silvia.apeiron.common.integration.waila;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mcp.mobius.waila.api.ITaggedList;

/** Waila passes one tagged list through every provider; later providers rely on its type and row tags. */
public final class WailaTooltipLists {

    private WailaTooltipLists() {}

    public static List<String> editable(List<String> original) {
        return original instanceof ITaggedList ? original : new ArrayList<>(original);
    }

    @SuppressWarnings("unchecked")
    public static void replace(List<String> lines, int index, String replacement) {
        if (!(lines instanceof ITaggedList)) {
            lines.set(index, replacement);
            return;
        }
        ITaggedList<String, Object> tagged = (ITaggedList<String, Object>) lines;
        Set<Object> tags = new HashSet<>(tagged.getTags(index));
        lines.set(index, replacement);
        for (Object tag : tags) tagged.addTag(index, tag);
    }
}
