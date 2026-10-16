package com.silvia.apeiron.common.machine.me.input.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BigIntSyncValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.client.gui.machine.me.input.InfinitePatternInputGui;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;

/** Render the actual multiplier cells, including the largest supported editor value, without opening a world. */
public final class PatternMultiplierGuiSmoke {

    private PatternMultiplierGuiSmoke() {}

    public static void verify(MTEInfinitePatternInputAssembly source) {
        try {
            InfinitePatternInputGui gui = new InfinitePatternInputGui(source);
            Field view = InfinitePatternInputGui.class.getDeclaredField("multiplierView");
            view.setAccessible(true);
            view.set(gui, new BooleanSyncValue(() -> true));
            Field values = InfinitePatternInputGui.class.getDeclaredField("factors");
            values.setAccessible(true);
            BigIntSyncValue[] factors = (BigIntSyncValue[]) values.get(gui);
            BigInteger[] examples = { BigInteger.ONE, BigInteger.valueOf(999), BigInteger.valueOf(1000),
                BigInteger.valueOf(1234), BigInteger.valueOf(12345), BigInteger.valueOf(25000000),
                BigInteger.TEN.pow(9), BigInteger.TEN.pow(12), BigInteger.TEN.pow(600), BigInteger.TEN.pow(32766) };
            for (int i = 0; i < examples.length; i++) {
                BigInteger example = examples[i];
                factors[i] = new BigIntSyncValue(() -> example, ignored -> {});
            }
            PanelSyncManager sync = new PanelSyncManager(new ModularSyncManager(true), true);
            sync.registerSlotGroup("apeiron_patterns", 10);
            ModularPanel panel = ModularPanel.defaultPanel("apeiron_multiplier_preview", 224, 96);
            panel.child(
                IKey.lang("apeiron.machine.pattern_input.multiplier")
                    .asWidget()
                    .pos(7, 7)
                    .scale(0.7f));
            Method cell = InfinitePatternInputGui.class
                .getDeclaredMethod("patternCell", ModularPanel.class, PanelSyncManager.class, int.class);
            cell.setAccessible(true);
            Grid grid = new Grid().minColWidth(18)
                .minRowHeight(18)
                .size(180, 18)
                .pos(22, 30);
            for (int i = 0; i < examples.length; i++) grid.child(
                ((ParentWidget<?>) cell.invoke(gui, panel, sync, i)).getChildren()
                    .get(1));
            panel.child(grid);
            panel.child(
                IKey.str("k = 1,000     M = 1,000,000")
                    .asWidget()
                    .pos(22, 56)
                    .scale(0.7f));
            ModularScreen screen = new ModularScreen("apeiron", panel);
            screen.getContext()
                .setSettings(new UISettings());
            new GuiScreenWrapper(screen);
            screen.onResize(460, 270);
            // Reuse the existing native framebuffer capture; its output is also inspected visually.
            Class<?> capture = com.silvia.apeiron.common.machine.me.stocking.verification.StockingInputGuiSmoke.class;
            Method render = capture.getDeclaredMethod("render", ModularScreen.class, java.io.File.class);
            render.setAccessible(true);
            try {
                render.invoke(null, screen, new java.io.File("pattern-multiplier-preview.png").getAbsoluteFile());
            } finally {
                screen.getPanelManager()
                    .closeAll();
                screen.getPanelManager()
                    .dispose();
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Pattern multiplier GUI verification failed", error);
        }
        Apeiron.LOG.info("Pattern multiplier GUI verification passed: native cells rendered from 1 to 10^32766");
    }
}
