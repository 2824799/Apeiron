package com.silvia.apeiron.common.machine.energy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WirelessWailaDisplayTest {

    @Test
    public void preservesCasingTooltipWhenOmniHasNoTileNbt() {
        final List<String> lines = Collections.unmodifiableList(Arrays.asList("casing", "harvest tool"));
        assertSame(lines, WirelessWailaDisplay.updateOmni(lines, null));
        assertEquals(Arrays.asList("casing", "harvest tool"), lines);
    }

    @Test
    public void preservesNativeTooltipWhenTileNbtHasNotArrived() {
        final List<String> lines = Collections.singletonList("casing");
        WirelessWailaDisplay.updateNative(lines, null);
        assertEquals(Collections.singletonList("casing"), lines);
    }
}
