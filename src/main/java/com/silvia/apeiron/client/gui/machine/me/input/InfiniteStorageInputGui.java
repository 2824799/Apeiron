// SPDX-License-Identifier: GPL-3.0-only
// Shared mark/stock grids and automatic network selection settings for all three stocking inputs.
package com.silvia.apeiron.client.gui.machine.me.input;

import java.util.Arrays;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.FluidDisplayWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.client.gui.sync.ChunkedNbtSyncValue;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternStackCodec;
import com.silvia.apeiron.common.machine.me.stocking.StockingFilter;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.ScientificInteger;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

public final class InfiniteStorageInputGui extends MTEHatchBaseGui<MTEHatch> {

    private final StockingInputLogic logic;
    private BooleanSyncValue autoPull;
    private final IAEStack<?>[] previewStocks = new IAEStack<?>[StockingInputLogic.SLOT_COUNT];
    private final ItemStack[] previewMarks = new ItemStack[StockingInputLogic.SLOT_COUNT];
    private final NBTTagCompound[] previewCells = new NBTTagCompound[StockingInputLogic.SLOT_COUNT];

    public InfiniteStorageInputGui(MTEHatch machine) {
        super(machine);
        logic = ((StockingInputHost) machine).getStockingInput();
    }

    @Override
    protected int getBasePanelWidth() {
        return 406;
    }

    @Override
    protected int getBasePanelHeight() {
        return 222;
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
    protected void registerSyncValues(PanelSyncManager sync) {
        super.registerSyncValues(sync);
        autoPull = new BooleanSyncValue(logic::isAutoPull, logic::setAutoPull).allowC2S();
        sync.syncValue("apeiron_stocking_auto", autoPull);
        sync.syncValue("apeiron_stocking_snapshot", new ChunkedNbtSyncValue(logic::snapshot, this::readSnapshot));
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager sync) {
        sync.registerSlotGroup("apeiron_stocking_marks", 10);
        IPanelHandler config = sync
            .syncedPanel("apeiron_stocking_config", true, (manager, handler) -> configPanel(manager));
        ItemStackHandler marks = new ItemStackHandler(StockingInputLogic.SLOT_COUNT) {

            @Override
            public ItemStack getStackInSlot(int slot) {
                return machine.getBaseMetaTileEntity()
                    .isClientSide() ? previewMarks[slot] : logic.markerItem(slot);
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                if (machine.getBaseMetaTileEntity()
                    .isClientSide()) return;
                FluidStack fluid = stack == null ? null
                    : stack.getItem() instanceof ItemFluidPacket ? ItemFluidPacket.getFluidStack(stack)
                        : GTUtility.getFluidForFilledItem(stack, true);
                IAEStack<?> marker = fluid != null && logic.getKind() != StockingInputLogic.Kind.ITEMS
                    ? AEFluidStack.create(fluid)
                    : stack == null ? null : AEItemStack.create(stack);
                logic.setMark(slot, marker);
            }
        };
        Grid grid = new Grid().scrollable(new VerticalScrollData())
            .showScrollShadows(false)
            .size(386, SLOT_SIZE * 4)
            .pos(0, 14)
            .child(
                new Grid().coverChildren()
                    .gridOfWidthHeight(1, 36, (x, y, row) -> {
                        ParentWidget<?> line = new ParentWidget<>().size(382, SLOT_SIZE);
                        for (int col = 0; col < 10; col++) {
                            int index = row * 10 + col;
                            line.child(
                                new StockingMarkSlot()
                                    .slot(new ModularSlot(marks, index).slotGroup("apeiron_stocking_marks"))
                                    .backgroundOverlay(
                                        GTGuiTextures.SLOT_ITEM_STANDARD,
                                        GTGuiTextures.OVERLAY_SLOT_ARROW_ME)
                                    .pos(col * SLOT_SIZE, 0)
                                    .name("apeiron_mark_" + index)
                                    .setEnabledIf(widget -> !autoPull.getBoolValue()));
                            line.child(stockWidget(index).pos(202 + col * SLOT_SIZE, 0));
                        }
                        return line;
                    }));
        return super.createContentSection(panel, sync).child(
            IKey.lang("apeiron.machine.stocking.marks")
                .asWidget()
                .pos(0, 0))
            .child(
                IKey.lang("apeiron.machine.stocking.available")
                    .asWidget()
                    .pos(202, 0))
            .child(grid)
            .child(
                new ToggleButton().value(autoPull)
                    .pos(182, 14)
                    .background(false, GTGuiTextures.BUTTON_STANDARD)
                    .background(true, GTGuiTextures.BUTTON_STANDARD_PRESSED)
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_IMPORT)
                    .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.stocking.auto"))))
            .child(
                new ButtonWidget<>().pos(182, 36)
                    .background(GTGuiTextures.BUTTON_STANDARD)
                    .overlay(GTGuiTextures.OVERLAY_BUTTON_WHITELIST)
                    .onMousePressed(button -> {
                        if (button == 0) {
                            config.togglePanel();
                            return true;
                        }
                        return false;
                    })
                    .tooltip(t -> t.addLine(IKey.lang("apeiron.machine.stocking.configure"))));
    }

    private NBTTagCompound cell(int index) {
        return previewCells[index] == null ? new NBTTagCompound() : previewCells[index];
    }

    private IAEStack<?> available(int index) {
        return previewStocks[index];
    }

    private void readSnapshot(NBTTagCompound tag) {
        Arrays.fill(previewCells, null);
        Arrays.fill(previewStocks, null);
        Arrays.fill(previewMarks, null);
        NBTTagList cells = tag.getTagList("slots", 10);
        for (int i = 0; i < cells.tagCount(); i++) {
            NBTTagCompound cell = cells.getCompoundTagAt(i);
            int slot = cell.getInteger("slot");
            if (slot < 0 || slot >= previewCells.length) continue;
            previewCells[slot] = cell;
            IAEStack<?> stack = cell.hasKey("available") ? BigPatternStackCodec.read(cell.getCompoundTag("available"))
                : null;
            if (stack instanceof com.silvia.apeiron.ae.stack.InfiniteAEStack && cell.getBoolean("infinite"))
                ((com.silvia.apeiron.ae.stack.InfiniteAEStack) stack).setInfinite(true);
            previewStocks[slot] = stack;
            IAEStack<?> marker = cell.hasKey("mark") ? BigPatternStackCodec.read(cell.getCompoundTag("mark")) : null;
            if (marker instanceof IAEItemStack) {
                ItemStack item = ((IAEItemStack) marker).getItemStack();
                item.stackSize = 1;
                previewMarks[slot] = item;
            } else if (marker instanceof IAEFluidStack) {
                FluidStack fluid = ((IAEFluidStack) marker).getFluidStack();
                fluid.amount = 1;
                previewMarks[slot] = ItemFluidPacket.newStack(fluid);
            }
        }
    }

    private ParentWidget<?> stockWidget(int index) {
        ParentWidget<?> result = new ParentWidget<>().size(SLOT_SIZE)
            .background(GTGuiTextures.SLOT_ITEM_DARK);
        result.child(new ItemDisplayWidget().item(new ObjectValue.Dynamic<>(ItemStack.class, () -> {
            IAEStack<?> stack = available(index);
            return stack instanceof IAEItemStack ? displayItem(stack) : null;
        }, ignored -> {}))
            .displayAmount(false)
            .size(SLOT_SIZE));
        result.child(new FluidDisplayWidget().value(new ObjectValue.Dynamic<>(FluidStack.class, () -> {
            IAEStack<?> stack = available(index);
            return stack instanceof IAEFluidStack ? displayFluid(stack) : null;
        }, ignored -> {}))
            .displayAmount(false)
            .size(SLOT_SIZE));
        result.child(
            new StockTooltipWidget(index).size(SLOT_SIZE)
                .name("apeiron_stock_" + index)
                .overlay((context, x, y, width, height, theme) -> {
                    IAEStack<?> stack = available(index);
                    if (stack != null) stack.drawOverlayInGui(
                        net.minecraft.client.Minecraft.getMinecraft(),
                        x + 1,
                        y + 1,
                        true,
                        true,
                        false,
                        false);
                })
                .tooltipDynamic(t -> {
                    IAEStack<?> stack = available(index);
                    if (stack == null) return;
                    if (stack instanceof IAEItemStack) t.addFromItem(displayItem(stack));
                    else if (stack instanceof IAEFluidStack) t.addFromFluid(displayFluid(stack));
                    String amount = cell(index).getBoolean("infinite") ? "∞"
                        : BigNumberFormatter.formatExact(BigAEStackValues.get(stack));
                    t.addLine(
                        IKey.lang(
                            stack instanceof IAEFluidStack ? "apeiron.machine.stocking.fluid_amount"
                                : "apeiron.machine.stocking.item_amount",
                            amount));
                })
                .tooltipAutoUpdate(true));
        return result;
    }

    private static ItemStack displayItem(IAEStack<?> stack) {
        ItemStack item = stack instanceof IAEItemStack ? ((IAEItemStack) stack).getItemStack()
            : stack instanceof IAEFluidStack ? GTUtility.getFluidDisplayStack(displayFluid(stack), false) : null;
        if (item != null) item.stackSize = 1;
        return item;
    }

    private static FluidStack displayFluid(IAEStack<?> stack) {
        FluidStack fluid = ((IAEFluidStack) stack).getFluidStack();
        fluid.amount = 1;
        return fluid;
    }

    /** Marks select a type, so wheel input belongs to the surrounding shared scroll grid. */
    private static final class StockingMarkSlot extends PhantomItemSlot {

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            return false;
        }
    }

    /** The topmost quantity overlay also owns the tooltip and NEI ingredient lookup. */
    private final class StockTooltipWidget extends Widget<StockTooltipWidget>
        implements RecipeViewerIngredientProvider {

        private final int index;

        private StockTooltipWidget(int index) {
            this.index = index;
        }

        @Override
        public ItemStack getStackForRecipeViewer() {
            return displayItem(available(index));
        }
    }

    private ModularPanel configPanel(PanelSyncManager sync) {
        Dialog<?> dialog = new Dialog<>("apeiron_stocking_config", null);
        dialog.relativeToScreen()
            .background(GTGuiTextures.BACKGROUND_POPUP_STANDARD)
            .size(406, 246)
            .center();
        dialog.setDisablePanelsBelow(true)
            .setCloseOnOutOfBoundsClick(false)
            .setDraggable(true);
        dialog.child(ButtonWidget.panelCloseButton());
        dialog.child(
            IKey.lang("apeiron.machine.stocking.settings")
                .asWidget()
                .pos(12, 10));
        section(dialog, "stock_range", 12);
        section(dialog, "pull_strategy", 142);
        section(dialog, "type_filter", 272);
        StockingFilter filter = logic.getFilter();
        addField(dialog, sync, "minimum", 12, 52, filter::getMinimumText, filter::setMinimum, true);
        addField(dialog, sync, "maximum", 12, 90, filter::getMaximumText, filter::setMaximum, true);
        note(dialog, "range_hint", 12, 124, 116, 42);
        addField(
            dialog,
            sync,
            "type_limit",
            142,
            52,
            () -> Integer.toString(filter.getTypeLimit()),
            value -> filter.setTypeLimit(
                ScientificInteger.positive(value)
                    .intValueExact()),
            true);
        IntSyncValue priority = new IntSyncValue(
            () -> filter.getPriority()
                .ordinal(),
            value -> {
                if (logic.isProcessing() || value < 0 || value >= StockingFilter.Priority.values().length) return;
                filter.setPriority(StockingFilter.Priority.values()[value]);
                logic.changed();
            }).allowC2S();
        sync.syncValue("apeiron_stocking_priority", priority);
        dialog.child(
            IKey.lang("apeiron.machine.stocking.priority")
                .asWidget()
                .pos(142, 90));
        CycleButtonWidget priorityButton = new CycleButtonWidget().value(priority)
            .stateCount(StockingFilter.Priority.values().length)
            .size(116, 18)
            .pos(142, 102)
            .name("apeiron_stocking_priority")
            .background(GTGuiTextures.BUTTON_STANDARD);
        for (StockingFilter.Priority order : StockingFilter.Priority.values()) {
            String key = "apeiron.machine.stocking.priority." + order.name()
                .toLowerCase(java.util.Locale.ROOT);
            priorityButton.stateOverlay(
                order,
                IKey.lang(key)
                    .scale(0.8f));
            priorityButton.tooltip(order.ordinal(), t -> t.addLine(IKey.lang(key + ".hint")));
        }
        dialog.child(priorityButton);
        addField(
            dialog,
            sync,
            "refresh",
            142,
            128,
            () -> Integer.toString(filter.getRefreshTicks()),
            value -> filter.setRefreshTicks(
                ScientificInteger.positive(value)
                    .intValueExact()),
            true);
        note(dialog, "strategy_hint", 142, 166, 116, 38);
        addField(dialog, sync, "mod", 272, 52, filter::getModId, filter::setModId, false);
        addField(dialog, sync, "item", 272, 90, filter::getItemId, filter::setItemId, false);
        addField(
            dialog,
            sync,
            "meta",
            272,
            128,
            () -> Integer.toString(filter.getMeta()),
            value -> filter.setMeta(Integer.parseInt(value)),
            false);
        addField(dialog, sync, "ore", 272, 166, filter::getOre, filter::setOre, false);
        note(dialog, "settings_hint", 12, 214, 380, 24);
        return dialog;
    }

    private static void section(ModularPanel panel, String key, int x) {
        panel.child(
            IKey.lang("apeiron.machine.stocking." + key)
                .asWidget()
                .pos(x, 32)
                .color(0xFF234A70));
    }

    private static void note(ModularPanel panel, String key, int x, int y, int width, int height) {
        panel.child(
            IKey.lang("apeiron.machine.stocking." + key)
                .asWidget()
                .pos(x, y)
                .size(width, height)
                .alignment(Alignment.TopLeft)
                .scale(0.8f)
                .color(0xFF555555));
    }

    private void addField(ModularPanel panel, PanelSyncManager sync, String key, int x, int y,
        java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter, boolean numeric) {
        StringSyncValue value = new StringSyncValue(getter, text -> {
            if (logic.isProcessing()) return;
            try {
                setter.accept(text);
                logic.changed();
            } catch (IllegalArgumentException | ArithmeticException ignored) {}
        }).allowC2S();
        sync.syncValue("apeiron_stocking_" + key, value);
        panel.child(
            IKey.lang("apeiron.machine.stocking." + key)
                .asWidget()
                .pos(x, y)
                .scale(0.9f));
        TextFieldWidget field = new TextFieldWidget().size(116, 18)
            .pos(x, y + 12)
            .name("apeiron_stocking_" + key)
            .value(value)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setMaxLength(numeric ? 4096 : 256);
        if (numeric) field.setPattern(Pattern.compile("[0-9.eE+\\-]*"));
        panel.child(field.setValidator(text -> {
            try {
                if (numeric && !(key.equals("maximum") && text.trim()
                    .isEmpty())) {
                    java.math.BigInteger parsed = ScientificInteger.positive(text);
                    if (key.equals("refresh")) parsed.intValueExact();
                    if (key.equals("type_limit") && (parsed.compareTo(java.math.BigInteger.ONE) < 0
                        || parsed.compareTo(java.math.BigInteger.valueOf(StockingInputLogic.SLOT_COUNT)) > 0))
                        throw new IllegalArgumentException("Type limit out of range");
                } else if (key.equals("meta")) Integer.parseInt(text);
                return text;
            } catch (IllegalArgumentException | ArithmeticException ignored) {
                return getter.get();
            }
        }));
    }
}
