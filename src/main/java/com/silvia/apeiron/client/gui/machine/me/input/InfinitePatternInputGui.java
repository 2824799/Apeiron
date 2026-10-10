// SPDX-License-Identifier: GPL-3.0-only
// Pattern-first scrolling layout and catalyst dialogs adapted from GT Not Leisure's SuperCraftingInputHatchMEGui.
package com.silvia.apeiron.client.gui.machine.me.input;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.sync.BigIntSyncValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.FluidDisplayWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.client.gui.sync.ChunkedNbtSyncValue;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.input.storage.PatternBufferQuickMove;
import com.silvia.apeiron.common.machine.me.input.storage.PatternBufferTransfers;
import com.silvia.apeiron.math.BigNumberFormatter;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.gui.modularui.util.PatternSlot;

/** One 10-column scroll position for the pattern and multiplier views; exact contents live in a second panel. */
public final class InfinitePatternInputGui extends MTEHatchBaseGui<MTEInfinitePatternInputAssembly> {

    public static final int VISIBLE_ROWS = 4;
    private final BooleanSupplier accessible;
    private BooleanSyncValue multiplierView;
    private BooleanSyncValue optimization;
    private BooleanSyncValue terminalVisible;
    private final BigIntSyncValue[] factors = new BigIntSyncValue[MTEInfinitePatternInputAssembly.PATTERN_COUNT];

    public InfinitePatternInputGui(MTEInfinitePatternInputAssembly machine) {
        this(machine, () -> true);
    }

    public InfinitePatternInputGui(MTEInfinitePatternInputAssembly machine, BooleanSupplier accessible) {
        super(machine);
        this.accessible = accessible;
    }

    @Override
    protected int getBasePanelWidth() {
        return 224;
    }

    @Override
    protected int getBasePanelHeight() {
        return 206;
    }

    @Override
    protected boolean supportsFluidScreen() {
        return false;
    }

    @Override
    protected boolean supportsFluidIOColumn() {
        return false;
    }

    @Override
    protected boolean supportsMuffler() {
        return false;
    }

    @Override
    protected boolean doesAddCircuitSlot() {
        return false;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager sync) {
        super.registerSyncValues(sync);
        multiplierView = new BooleanSyncValue(
            machine::isMultiplierView,
            value -> { if (accessible.getAsBoolean()) machine.setMultiplierView(value); }).allowC2S();
        sync.syncValue("apeiron_multiplier_view", multiplierView);
        optimization = new BooleanSyncValue(
            machine::allowsPatternOptimization,
            value -> { if (accessible.getAsBoolean()) machine.setPatternOptimization(value); }).allowC2S();
        sync.syncValue("apeiron_optimization", optimization);
        terminalVisible = new BooleanSyncValue(
            machine::shouldDisplay,
            value -> { if (accessible.getAsBoolean()) machine.setTerminalVisible(value); }).allowC2S();
        sync.syncValue("apeiron_terminal_visible", terminalVisible);
        for (int i = 0; i < factors.length; i++) {
            final int index = i;
            factors[i] = new BigIntSyncValue(
                () -> machine.getMultiplierBig(index),
                value -> { if (accessible.getAsBoolean()) machine.setMultiplierBig(index, value); });
            sync.syncValue("apeiron_factor_" + i, factors[i]);
        }
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager sync) {
        sync.registerSlotGroup("apeiron_patterns", MTEInfinitePatternInputAssembly.PATTERN_COLUMNS);
        Grid grid = new Grid().scrollable(new VerticalScrollData())
            .showScrollShadows(false)
            .minColWidth(SLOT_SIZE)
            .minRowHeight(SLOT_SIZE)
            .size(SLOT_SIZE * MTEInfinitePatternInputAssembly.PATTERN_COLUMNS + 4, SLOT_SIZE * VISIBLE_ROWS)
            .child(
                new Grid().coverChildren()
                    .gridOfWidthHeight(
                        MTEInfinitePatternInputAssembly.PATTERN_COLUMNS,
                        MTEInfinitePatternInputAssembly.PATTERN_COUNT / MTEInfinitePatternInputAssembly.PATTERN_COLUMNS,
                        (x, y, index) -> patternCell(panel, sync, index)));
        return super.createContentSection(panel, sync).child(grid)
            .child(
                new ToggleButton().value(multiplierView)
                    .background(false, GTGuiTextures.BUTTON_STANDARD)
                    .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_PATTERN_OPTIMIZE)
                    .pos(SLOT_SIZE * MTEInfinitePatternInputAssembly.PATTERN_COLUMNS + 7, 0)
                    .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.switch_view"))))
            .child(
                new ButtonWidget<>().background(GTGuiTextures.BUTTON_STANDARD)
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_X2)
                    .pos(SLOT_SIZE * MTEInfinitePatternInputAssembly.PATTERN_COLUMNS + 7, SLOT_SIZE + 2)
                    .setEnabledIf(w -> multiplierView.getBoolValue())
                    .syncHandler(
                        new InteractionSyncHandler().setOnMousePressed(
                            data -> {
                                if (!data.isClient() && accessible.getAsBoolean())
                                    machine.scaleMultipliers(data.mouseButton == 1, data.shift);
                            }))
                    .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.multiply_all"))));
    }

    private ParentWidget<?> patternCell(ModularPanel parent, PanelSyncManager sync, int index) {
        IPanelHandler catalysts = sync.syncedPanel(
            "apeiron_pattern_catalysts_" + index,
            true,
            (manager, handler) -> catalystPanel(
                parent,
                manager,
                machine.catalystSlotStart(index),
                "apeiron_pattern_catalysts_" + index));
        IPanelHandler multiplier = sync.syncedPanel(
            "apeiron_multiplier_editor_" + index,
            true,
            (manager, handler) -> multiplierPanel(parent, manager, index));
        PatternCatalystSlot slot = new PatternCatalystSlot(catalysts);
        slot.slot(
            new GuardedSlot(index, true).filter(stack -> stack.getItem() instanceof ICraftingPatternItem)
                .slotGroup("apeiron_patterns"));
        slot.setEnabledIf(widget -> !multiplierView.getBoolValue());
        slot.tooltip(
            t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.pattern_slot", index + 1))
                .addLine(IKey.lang("apeiron.machine.pattern_input.pattern_catalysts"))
                .addLine(IKey.lang("apeiron.machine.pattern_input.duplicate_rule")));
        ButtonWidget<?> factor = new ButtonWidget<>().background(GTGuiTextures.BUTTON_STANDARD)
            .overlay(
                IKey.dynamic(
                    () -> (factors[index].getValue()
                        .toString()
                        .length() <= 3 ? factors[index].getStringValue() : "×…"))
                    .scale(0.7f))
            .tooltip(
                t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.pattern_slot", index + 1))
                    .addLine(IKey.dynamic(() -> "× " + BigNumberFormatter.formatExact(factors[index].getValue()))))
            .onMousePressed(button -> {
                if (button == 0) {
                    multiplier.togglePanel();
                    return true;
                }
                return false;
            })
            .setEnabledIf(widget -> multiplierView.getBoolValue());
        return new ParentWidget<>().size(SLOT_SIZE)
            .child(slot)
            .child(factor.size(SLOT_SIZE));
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager sync) {
        IPanelHandler catalysts = sync.syncedPanel(
            "apeiron_shared_catalysts",
            true,
            (manager, handler) -> catalystPanel(
                panel,
                manager,
                MTEInfinitePatternInputAssembly.SHARED_CATALYST_START,
                "apeiron_shared_catalysts"));
        return super.createBottomLeftCornerFlow(panel, sync).child(
            new ToggleButton().value(optimization)
                .background(false, GTGuiTextures.BUTTON_STANDARD)
                .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_BATCH_MODE_ON)
                .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.optimization"))))
            .child(
                new ToggleButton().value(terminalVisible)
                    .background(false, GTGuiTextures.BUTTON_STANDARD)
                    .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_WHITELIST)
                    .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.terminal_visible"))))
            .child(serverButton(machine::refreshPatterns, GTGuiTextures.OVERLAY_BUTTON_WHITELIST, "refresh"))
            .child(serverButton(machine::refundAll, GTGuiTextures.OVERLAY_BUTTON_EXPORT, "refund"))
            .child(
                new ButtonWidget<>().background(GTGuiTextures.BUTTON_STANDARD)
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_PLUS_LARGE)
                    .onMousePressed(button -> {
                        if (button == 0) {
                            catalysts.togglePanel();
                            return true;
                        }
                        return false;
                    })
                    .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.catalysts"))));
    }

    @Override
    protected Flow createBottomRightCornerFlow(ModularPanel panel, PanelSyncManager sync) {
        IPanelHandler contents = sync
            .syncedPanel("apeiron_input_contents", true, (manager, handler) -> contentsPanel(panel, manager));
        return super.createBottomRightCornerFlow(panel, sync).child(
            new ButtonWidget<>().background(GTGuiTextures.BUTTON_STANDARD)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_WHITELIST)
                .onMousePressed(button -> {
                    if (button == 0) {
                        contents.togglePanel();
                        return true;
                    }
                    return false;
                })
                .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.contents"))));
    }

    private ButtonWidget<?> serverButton(Runnable action, com.cleanroommc.modularui.api.drawable.IDrawable icon,
        String key) {
        return new ButtonWidget<>().background(GTGuiTextures.BUTTON_STANDARD)
            .overlay(icon)
            .syncHandler(
                new InteractionSyncHandler()
                    .setOnMousePressed(data -> { if (!data.isClient() && accessible.getAsBoolean()) action.run(); }))
            .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input." + key)));
    }

    private Dialog<?> dialog(String key, ModularPanel parent, int width, int height, String title) {
        Dialog<?> panel = new Dialog<>(key, null);
        panel.relative(parent)
            .background(GTGuiTextures.BACKGROUND_POPUP_STANDARD)
            .size(width, height)
            .leftRel(1)
            .topRel(0);
        panel.setDisablePanelsBelow(false)
            .setCloseOnOutOfBoundsClick(false)
            .setDraggable(true);
        panel.child(
            IKey.lang(title)
                .asWidget()
                .pos(7, 5));
        panel.child(ButtonWidget.panelCloseButton());
        return panel;
    }

    private ModularPanel catalystPanel(ModularPanel parent, PanelSyncManager sync, int start, String key) {
        Dialog<?> panel = dialog(key, parent, 176, 60, "apeiron.machine.pattern_input.catalysts");
        sync.registerSlotGroup(key + "_slots", 9);
        panel.child(
            new Grid().coverChildren()
                .gridOfWidthHeight(
                    9,
                    1,
                    (x, y, index) -> new ItemSlot()
                        .slot(new GuardedSlot(start + index, false).slotGroup(key + "_slots")))
                .pos(7, 20));
        return panel;
    }

    private ModularPanel multiplierPanel(ModularPanel parent, PanelSyncManager sync, int index) {
        Dialog<?> panel = dialog(
            "apeiron_multiplier_editor_" + index,
            parent,
            176,
            58,
            "apeiron.machine.pattern_input.multiplier");
        StringSyncValue value = new StringSyncValue(
            () -> machine.getMultiplierBig(index)
                .toString(),
            text -> {
                if (!accessible.getAsBoolean()) return;
                try {
                    BigInteger factor = new BigInteger(text);
                    if (factor.signum() > 0) machine.setMultiplierBig(index, factor);
                } catch (NumberFormatException ignored) {}
            }).allowC2S();
        panel.child(
            new TextFieldWidget().size(160, 18)
                .pos(7, 22)
                .value(value)
                .setPattern(Pattern.compile("[0-9]*"))
                .acceptsExpressions(false)
                .autoUpdateOnChange(false)
                .setMaxLength(32767)
                .setValidator(text -> {
                    try {
                        BigInteger factor = new BigInteger(text);
                        if (factor.signum() > 0) return factor.toString();
                    } catch (NumberFormatException ignored) {}
                    return machine.getMultiplierBig(index)
                        .toString();
                }));
        return panel;
    }

    private ModularPanel contentsPanel(ModularPanel parent, PanelSyncManager sync) {
        Dialog<?> panel = dialog(
            "apeiron_input_contents",
            parent,
            244,
            330,
            "apeiron.machine.pattern_input.buffer_contents");
        panel.relativeToScreen()
            .center();
        panel.setDisablePanelsBelow(true);
        final int[] selected = { 0 };
        IntSyncValue page = new IntSyncValue(() -> selected[0], value -> {
            if (accessible.getAsBoolean() && value >= 0 && value < MTEInfinitePatternInputAssembly.BUFFER_COUNT)
                selected[0] = value;
        }).allowC2S();
        sync.syncValue("apeiron_buffer_page", page);
        BigPatternBuffer preview = new BigPatternBuffer();
        ChunkedNbtSyncValue snapshot = new ChunkedNbtSyncValue(
            () -> machine.getBuffers()
                .get(selected[0])
                .writeNBT(),
            preview::readNBT);
        sync.syncValue("apeiron_buffer_snapshot", snapshot);
        panel.child(
            IKey.dynamic(
                () -> IKey.lang("apeiron.machine.pattern_input.page", page.getIntValue() + 1)
                    .get())
                .asWidget()
                .pos(10, 20));
        panel.child(
            new Grid().coverChildren()
                .gridOfWidthHeight(8, 4, (x, y, slot) -> displaySlot(preview, slot, false, sync, page))
                .pos(10, 33));
        panel.child(
            IKey.lang("apeiron.machine.pattern_input.fluid_contents")
                .asWidget()
                .pos(10, 110));
        panel.child(
            new Grid().coverChildren()
                .gridOfWidthHeight(8, 4, (x, y, slot) -> displaySlot(preview, slot, true, sync, page))
                .pos(10, 123));
        panel.child(
            new Grid().coverChildren()
                .gridOfWidthHeight(
                    12,
                    2,
                    (x, y, index) -> new ButtonWidget<>().size(18)
                        .background(GTGuiTextures.BUTTON_STANDARD)
                        .overlay(
                            IKey.str(String.valueOf(index + 1))
                                .scale(0.7f))
                        .onMousePressed(button -> {
                            if (button == 0) {
                                page.setIntValue(index);
                                return true;
                            }
                            return false;
                        })
                        .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.page", index + 1))))
                .pos(14, 202));
        BooleanSyncValue locked = new BooleanSyncValue(
            () -> machine.getBuffers()
                .get(selected[0])
                .isLocked(),
            value -> { if (accessible.getAsBoolean()) machine.setBufferLocked(selected[0], value); }).allowC2S();
        sync.syncValue("apeiron_buffer_lock", locked);
        panel.child(
            new ToggleButton().value(locked)
                .background(false, GTGuiTextures.BUTTON_STANDARD)
                .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                .overlay(GTGuiTextures.OVERLAY_BUTTON_LOCK)
                .pos(195, 178)
                .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.pattern_input.lock_buffer"))));
        panel.child(
            new Grid().scrollable(new VerticalScrollData())
                .showScrollShadows(false)
                .size(22, 144)
                .pos(195, 33)
                .child(
                    new Grid().coverChildren()
                        .gridOfWidthHeight(
                            1,
                            50,
                            (x, y, index) -> new ItemDisplayWidget()
                                .item(new ObjectValue.Dynamic<>(ItemStack.class, () -> {
                                    java.util.List<ItemStack> circuits = preview.getSelectors();
                                    if (index >= circuits.size()) return null;
                                    ItemStack circuit = circuits.get(index);
                                    circuit.stackSize = 1;
                                    return circuit;
                                }, ignored -> {}))
                                .displayAmount(false)
                                .size(18)
                                .background(com.cleanroommc.modularui.drawable.GuiTextures.SLOT_ITEM))));
        sync.addOpenListener(
            player -> PatternBufferQuickMove.open(sync.getContainer(), machine, page::getIntValue, accessible));
        sync.addCloseListener(player -> PatternBufferQuickMove.close(sync.getContainer()));
        panel.bindPlayerInventory();
        return panel;
    }

    private IAEStack<?> stackAt(BigPatternBuffer buffer, int index, boolean fluid) {
        return PatternBufferTransfers.stackAt(buffer, index, fluid);
    }

    private ParentWidget<?> displaySlot(BigPatternBuffer buffer, int index, boolean fluid, PanelSyncManager sync,
        IntSyncValue page) {
        PatternBufferInteraction interaction = new PatternBufferInteraction(
            machine,
            buffer,
            page::getIntValue,
            accessible,
            index,
            fluid);
        sync.syncValue("apeiron_buffer_click_" + fluid + "_" + index, interaction);
        ParentWidget<?> cell = new ParentWidget<>().size(18, 18)
            .background(com.cleanroommc.modularui.drawable.GuiTextures.SLOT_ITEM);
        if (fluid) cell.child(new FluidDisplayWidget().value(new ObjectValue.Dynamic<>(FluidStack.class, () -> {
            IAEStack<?> stack = stackAt(buffer, index, true);
            return stack == null ? null : ((IAEFluidStack) stack).getFluidStack();
        }, ignored -> {}))
            .displayAmount(false)
            .size(18));
        else cell.child(new ItemDisplayWidget().item(new ObjectValue.Dynamic<>(ItemStack.class, () -> {
            IAEStack<?> stack = stackAt(buffer, index, false);
            return stack == null ? null : ((IAEItemStack) stack).getItemStack();
        }, ignored -> {}))
            .displayAmount(false)
            .size(18));
        cell.child(
            new com.cleanroommc.modularui.widget.Widget<>().size(18)
                .overlay((context, x, y, width, height, theme) -> {
                    IAEStack<?> stack = stackAt(buffer, index, fluid);
                    if (stack != null) stack.drawOverlayInGui(
                        net.minecraft.client.Minecraft.getMinecraft(),
                        x + 1,
                        y + 1,
                        true,
                        true,
                        false,
                        false);
                }));
        // The transparent button is the topmost child and owns hover/click handling.
        cell.child(
            new ButtonWidget<>().size(18, 18)
                .invisible()
                .disableHoverBackground()
                .onMousePressed(interaction::click)
                .tooltip(t -> {
                    IAEStack<?> stack = stackAt(buffer, index, fluid);
                    if (stack != null) t.addLine(
                        IKey.str(
                            fluid ? ((IAEFluidStack) stack).getFluidStack()
                                .getLocalizedName()
                                : ((IAEItemStack) stack).getItemStack()
                                    .getDisplayName()))
                        .addLine(IKey.str(BigNumberFormatter.formatExact(BigAEStackValues.get(stack))));
                    t.addLine(IKey.lang("apeiron.machine.pattern_input.manual_transfer"));
                }));
        return cell;
    }

    private final class GuardedSlot extends ModularSlot {

        private final boolean pattern;

        private GuardedSlot(int slot, boolean pattern) {
            super(machine.getInventoryHandler(), slot);
            this.pattern = pattern;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return accessible.getAsBoolean() && super.isItemValid(stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return accessible.getAsBoolean() && super.canTakeStack(player);
        }

        @Override
        public int getItemStackLimit(ItemStack stack) {
            return pattern ? 1 : super.getItemStackLimit(stack);
        }
    }

    private static final class PatternCatalystSlot extends PatternSlot {

        private final IPanelHandler panel;

        private PatternCatalystSlot(IPanelHandler panel) {
            this.panel = panel;
        }

        @Override
        public @NotNull Interactable.Result onMousePressed(int button) {
            if (button == 2) {
                panel.togglePanel();
                return Interactable.Result.SUCCESS;
            }
            return super.onMousePressed(button);
        }
    }
}
