package com.silvia.apeiron.common.machine.me.input.verification;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.client.gui.machine.me.input.InfinitePatternInputGui;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;
import com.silvia.apeiron.common.machine.me.input.pattern.MultipliedPatternDetails;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.container.ContainerNull;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;
import io.netty.buffer.ByteBuf;

/** Uses transformed AE and GT classes, detached inventories and one authoritative source for both mirrors. */
public final class InfinitePatternInputSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(600)
        .add(BigInteger.valueOf(37));

    private InfinitePatternInputSmoke() {}

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static final class Assembly extends MTEInfinitePatternInputAssembly {

        private int patternLookups;

        private Assembly() {
            super("apeiron.verification.pattern_input", 10, new String[0], null);
            setBaseMetaTileEntity(tile(4));
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public ICraftingPatternDetails getPatternDetails(int slot) {
            patternLookups++;
            return super.getPatternDetails(slot);
        }
    }

    /** Count real AE ultimate-pattern decodes without replacing the helper or its item/fluid NBT work. */
    private static final class CountingPatternItem extends Item implements ICraftingPatternItem {

        private int decodes;
        private final Item nativeItem = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get()
            .getItem();

        private ItemStack wrap(ItemStack nativePattern) {
            ItemStack result = new ItemStack(this, nativePattern.stackSize, nativePattern.getItemDamage());
            result.setTagCompound(
                (NBTTagCompound) nativePattern.getTagCompound()
                    .copy());
            return result;
        }

        @Override
        public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
            decodes++;
            if (stack.getTagCompound()
                .getBoolean("verificationBroken")) {
                stack.getTagCompound()
                    .setBoolean("decoderAnnotation", true);
                return null;
            }
            ItemStack nativePattern = new ItemStack(nativeItem, stack.stackSize, stack.getItemDamage());
            nativePattern.setTagCompound(stack.getTagCompound());
            return ((ICraftingPatternItem) nativeItem).getPatternForItem(nativePattern, world);
        }
    }

    /** Exercise the native transfer loop and the actual GUI's guarded slots, without a player or network. */
    private static final class QuickMoveContainer extends ModularContainer {

        private final List<ModularSlot> slots = new java.util.ArrayList<>();
        private final ModularSlot playerSlot = new ModularSlot(new ItemStackHandler(1), 0)
            .slotGroup(new SlotGroup("verification_player", 1));

        private QuickMoveContainer(Assembly source) {
            try {
                InfinitePatternInputGui gui = new InfinitePatternInputGui(source);
                Class<?> guarded = Class.forName(InfinitePatternInputGui.class.getName() + "$GuardedSlot");
                java.lang.reflect.Constructor<?> constructor = guarded
                    .getDeclaredConstructor(InfinitePatternInputGui.class, int.class, boolean.class);
                constructor.setAccessible(true);
                SlotGroup group = new SlotGroup("verification_patterns", 10);
                for (int slot = 0; slot < 360; slot++)
                    getShiftClickSlots().add(((ModularSlot) constructor.newInstance(gui, slot, true)).slotGroup(group));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not initialize pattern quick-move fixture", error);
            }
        }

        private ItemStack move(ItemStack stack) {
            return transferItem(playerSlot, stack);
        }

        @Override
        public List<ModularSlot> getShiftClickSlots() {
            return slots;
        }
    }

    private static final class Mirror extends MTEInfinitePatternInputMirror {

        private MTEInfinitePatternInputAssembly source;

        private Mirror(MTEInfinitePatternInputAssembly source) {
            super("apeiron.verification.pattern_mirror", 10, new String[0], null);
            this.source = source;
            setBaseMetaTileEntity(tile(5));
            setLink(0, 10, 20, 30);
        }

        @Override
        protected MTEInfinitePatternInputAssembly lookupSource() {
            return source;
        }
    }

    private static ItemStack pattern() {
        ItemStack encoded = AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        inputs.appendTag(new ItemStack(Items.diamond, 2).writeToNBT(new NBTTagCompound()));
        NBTTagList outputs = new NBTTagList();
        outputs.appendTag(new ItemStack(Items.emerald).writeToNBT(new NBTTagCompound()));
        tag.setTag("in", inputs);
        tag.setTag("out", outputs);
        tag.setBoolean("crafting", false);
        encoded.setTagCompound(tag);
        return encoded;
    }

    private static List<IAEStack<?>> input(BigInteger amount) {
        return Arrays.asList(
            BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), amount),
            BigAEStackValues.copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), amount));
    }

    public static void verifyGlobalCircuit() {
        Assembly source = new Assembly();
        ItemStack circuit = gregtech.api.util.GTUtility.getIntegratedCircuit(7);
        circuit.stackSize = 1;
        source.setInventorySlotContents(source.getCircuitSlot(), circuit);
        check(circuit.stackSize == 1, "global circuit modified a real item");
        check(source.getStackInSlot(source.getCircuitSlot()).stackSize == 0, "global circuit became a real item");
        ItemStack catalyst = new ItemStack(Items.gold_ingot).setStackDisplayName("Global mold");
        source.setInventorySlotContents(MTEInfinitePatternInputAssembly.SHARED_CATALYST_START, catalyst);
        source.updateCraftingIcon(new ItemStack(Items.diamond));
        source.mRecipeMap = gregtech.api.recipe.RecipeMaps.cutterRecipes;
        gregtech.common.tileentities.machines.MTEHatchCraftingInputME nativeInput = new gregtech.common.tileentities.machines.MTEHatchCraftingInputME(
            "apeiron.verification.native_name",
            10,
            new String[0],
            null,
            true);
        nativeInput.setBaseMetaTileEntity(new BaseMetaTileEntity());
        nativeInput.updateCraftingIcon(new ItemStack(Items.diamond));
        nativeInput.mRecipeMap = source.mRecipeMap;
        nativeInput.setInventorySlotContents(nativeInput.getCircuitSlot(), circuit);
        nativeInput.setInventorySlotContents(nativeInput.getCircuitSlot() + 1, catalyst.copy());
        check(
            source.getRawName()
                .equals(nativeInput.getRawName()),
            "raw terminal name differs from native GT");
        String suffix = source.getNameSuffix()
            .getUnformattedText();
        check(
            suffix.equals(
                nativeInput.getNameSuffix()
                    .getUnformattedText()),
            "mode/circuit/catalyst suffix differs from native GT");
        String encodedSuffix = net.minecraft.util.IChatComponent.Serializer.func_150696_a(source.getNameSuffix());
        check(
            net.minecraft.util.IChatComponent.Serializer.func_150699_a(encodedSuffix)
                .getUnformattedText()
                .equals(suffix),
            "terminal suffix lost client-localized components during serialization");
        source.setInventorySlotContents(source.getCircuitSlot(), gregtech.api.util.GTUtility.getIntegratedCircuit(8));
        check(
            !encodedSuffix.equals(net.minecraft.util.IChatComponent.Serializer.func_150696_a(source.getNameSuffix())),
            "circuit name did not update");
        source.mRecipeMap = gregtech.api.recipe.RecipeMaps.compressorRecipes;
        check(
            !source.getNameSuffix()
                .getUnformattedText()
                .equals(suffix),
            "mode name did not update");
        source.setCustomName("Named machine");
        check(
            source.getNameSuffix() == null && source.getRawName()
                .equals("Named machine"),
            "custom name lost precedence");
        source.setCustomName("");
        source.setInventorySlotContents(0, pattern());
        check(
            source.pushPatternBig(source.getPatternDetails(0), input(BigInteger.valueOf(5)), BigInteger.ONE, false),
            "circuit recipe dispatch failed");
        BigPatternBuffer buffer = source.getBuffers()
            .get(0);
        ItemStack selector = gregtech.api.util.GTUtility.getIntegratedCircuit(8);
        selector.stackSize = 0;
        gregtech.api.util.GTRecipe recipe = new gregtech.api.util.GTRecipe(
            false,
            new ItemStack[] { new ItemStack(Items.diamond, 2), selector },
            new ItemStack[] { new ItemStack(Items.emerald) },
            null,
            null,
            null,
            null,
            null,
            new FluidStack[0],
            null,
            20,
            8,
            0);
        MTEElectricBlastFurnace controller = new MTEElectricBlastFurnace("apeiron.verification.circuit_recipe");
        controller.setBaseMetaTileEntity(new BaseMetaTileEntity());
        controller.mDualInputHatches.add(source);
        source.beginRecipeProcessing();
        check(
            recipe.isRecipeInputEqual(false, new FluidStack[0], buffer.getItemInputs()),
            "native recipe did not see the global circuit");
        com.silvia.apeiron.common.machine.parallel.BigRecipeInputs exact = new com.silvia.apeiron.common.machine.parallel.BigRecipeInputs(
            controller,
            recipe,
            buffer.getItemInputs(),
            buffer.getFluidInputs());
        exact.consume(BigInteger.valueOf(2));
        source.endRecipeProcessing();
        check(
            buffer.getItemAmountBig()
                .equals(BigInteger.ONE) && source.getStackInSlot(source.getCircuitSlot()).stackSize == 0,
            "exact recipe consumed the ghost circuit or wrong materials");
        source.setInventorySlotContents(source.getCircuitSlot(), null);
        check(
            !recipe.isRecipeInputEqual(false, new FluidStack[0], buffer.getItemInputs()),
            "cleared circuit remained in buffered recipe");
        source.getInventoryHandler()
            .setStackInSlot(source.getCircuitSlot(), circuit);
        check(
            buffer.getSelectors()
                .stream()
                .anyMatch(item -> item.getItemDamage() == 7 && item.getItem() == circuit.getItem()),
            "native GUI handler did not update existing buffer selectors");
        NBTTagCompound saved = new NBTTagCompound();
        source.saveNBTData(saved);
        Assembly loaded = new Assembly();
        loaded.loadNBTData(saved);
        check(
            loaded.getStackInSlot(loaded.getCircuitSlot())
                .getItemDamage() == 7,
            "world reload lost circuit");
        NBTTagCompound itemState = new NBTTagCompound();
        source.setItemNBT(itemState);
        ItemStack block = source.getStackForm(1);
        block.setTagCompound(itemState);
        com.silvia.apeiron.common.machine.block.MachineItemNbt.normalize(block);
        loaded.loadNBTData(block.getTagCompound());
        check(
            loaded.getStackInSlot(loaded.getCircuitSlot())
                .getItemDamage() == 7,
            "machine item normalization lost circuit");
        Assembly circuitOnly = new Assembly();
        circuitOnly.setInventorySlotContents(circuitOnly.getCircuitSlot(), circuit);
        NBTTagCompound only = new NBTTagCompound();
        circuitOnly.setItemNBT(only);
        block.setTagCompound(only);
        com.silvia.apeiron.common.machine.block.MachineItemNbt.normalize(block);
        loaded.loadNBTData(block.getTagCompound());
        check(
            loaded.getStackInSlot(loaded.getCircuitSlot())
                .getItemDamage() == 7,
            "circuit-only machine item lost settings");
        Apeiron.LOG.info(
            "Global pattern circuit verification passed: native terminal suffix, mode/name updates, native/exact matching, buffer edits and world/item persistence");
    }

    public static void verify() {
        check(
            GregTechAPI.METATILEENTITIES[ApeironConfig.getMachineId(4)] == ApeironMachines.patternInputAssembly,
            "source registration");
        check(
            GregTechAPI.METATILEENTITIES[ApeironConfig.getMachineId(5)] == ApeironMachines.patternInputMirror,
            "mirror registration");
        check(
            AEApi.instance()
                .registries()
                .interfaceTerminal()
                .getSupportedClasses()
                .contains(MTEInfinitePatternInputAssembly.class),
            "terminal registration missing");
        verifyOptimization();
        verifyMultiplierLifecycle();
        verifyBatchDispatch();
        verifyDuplicatePatterns();
        verifyInsertionCache();
        Assembly source = new Assembly();
        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) PatternMultiplierGuiSmoke.verify(source);
        verifyTerminalRepresentation(source);
        check(
            source.getPatterns()
                .getSizeInventory() == 360 && source.rowSize() == 9
                && source.rows() == 40,
            "360 AE-visible patterns");
        check(359 / source.rowSize() == 39 && 359 % source.rowSize() == 8, "terminal last pattern was clipped");
        final gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace attached = new gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace(
            "apeiron.verification.attached_blast_furnace");
        final String ownName = source.getName();
        source.addWatcherCompat(attached);
        source.addWatcherCompat(attached);
        verifyTerminalRepresentation(source);
        check(
            source.getName()
                .equals(attached.getLocalName()),
            "terminal did not use attached machine name");
        source.removeWatcherCompat(attached);
        check(
            source.getName()
                .equals(attached.getLocalName()),
            "partial watcher removal lost machine name");
        source.setCustomName("Apeiron named input");
        verifyTerminalRepresentation(source);
        check(
            source.getName()
                .equals("Apeiron named input"),
            "custom name did not override machine name");
        source.setCustomName("");
        source.removeWatcherCompat(attached);
        check(
            source.getName()
                .equals(ownName),
            "detached terminal retained machine name");
        check(
            source.getBuffers()
                .size() == 24,
            "24 isolated buffers");
        ItemStack encoded = pattern();
        NBTTagCompound untouched = encoded.writeToNBT(new NBTTagCompound());
        source.getPatterns()
            .setInventorySlotContents(359, encoded);
        source.setMultiplierBig(359, HUGE);
        ICraftingPatternDetails details = source.getPatternDetails(359);
        check(details instanceof MultipliedPatternDetails, "block-local multiplier wrapper");
        check(
            BigAEStackValues.get(details.getAEInputs()[0])
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "exact multiplied input");
        check(
            BigAEStackValues.get(details.getAEOutputs()[0])
                .equals(HUGE),
            "exact multiplied output");
        check(details.getAEInputs()[0].getStackSize() == Long.MAX_VALUE, "legacy multiplied count saturation");
        check(
            encoded.writeToNBT(new NBTTagCompound())
                .equals(untouched),
            "multiplier modified original encoded pattern");
        ItemStack serialized = details.getPattern();
        ICraftingPatternDetails decoded = ((ICraftingPatternItem) serialized.getItem())
            .getPatternForItem(serialized, null);
        check(
            details.equals(decoded) && BigAEStackValues.get(decoded.getAEInputs()[0])
                .equals(HUGE.multiply(BigInteger.valueOf(2))),
            "in-flight multiplied pattern reload");
        int[] advertised = { 0 };
        source.provideCrafting(
            (appeng.api.networking.crafting.ICraftingProviderHelper) java.lang.reflect.Proxy.newProxyInstance(
                InfinitePatternInputSmoke.class.getClassLoader(),
                new Class<?>[] { appeng.api.networking.crafting.ICraftingProviderHelper.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("addCraftingOption")) advertised[0]++;
                    return null;
                }));
        check(advertised[0] == 1, "slot 359 not advertised to AE");
        source.mInventory[MTEInfinitePatternInputAssembly.SHARED_CATALYST_START] = new ItemStack(Items.iron_ingot, 1);
        source.mInventory[source.catalystSlotStart(359)] = new ItemStack(Items.gold_ingot, 1);
        check(source.pushPatternBig(details, input(HUGE), BigInteger.ONE, true), "big input simulation");
        check(
            source.getBuffers()
                .stream()
                .allMatch(BigPatternBuffer::isEmpty),
            "simulation modified buffers");
        MEInventoryCrafting table = new MEInventoryCrafting(new ContainerNull(), 2, 1);
        table.setInventorySlotContents(0, input(HUGE).get(0));
        table.setInventorySlotContents(1, input(HUGE).get(1));
        check(source.pushPattern(details, table), "native AE exact table push");
        BigPatternBuffer buffer = source.getBuffers()
            .get(0);
        check(
            buffer.getItemAmountBig()
                .equals(HUGE)
                && buffer.getFluidAmountBig()
                    .equals(HUGE),
            "AE table truncated exact amounts");
        check(
            buffer.getItemInputs()[1].stackSize == 0 && buffer.getItemInputs()[2].stackSize == 0,
            "catalysts are consumable materials");
        Mirror first = new Mirror(source);
        Mirror second = new Mirror(source);
        verifyTerminalRepresentation(first.getInputSource());
        check(
            first.inventories()
                .next() == buffer
                && second.inventories()
                    .next() == buffer,
            "mirrors copied source inventory");
        MTEElectricBlastFurnace controller = new MTEElectricBlastFurnace("apeiron.verification.pattern_controller");
        controller.setBaseMetaTileEntity(new BaseMetaTileEntity());
        check(controller.addToMachineList(source.getBaseMetaTileEntity(), 0), "source not recognized as dual input");
        check(
            controller.addToMachineList(first.getBaseMetaTileEntity(), 0),
            "first mirror not recognized as dual input");
        check(
            controller.addToMachineList(second.getBaseMetaTileEntity(), 0),
            "second mirror not recognized as dual input");
        check(
            controller.addInputHatchToMachineList(source.getBaseMetaTileEntity(), 0)
                && controller.addInputBusToMachineList(source.getBaseMetaTileEntity(), 0)
                && controller.mDualInputHatches.size() == 3,
            "duplicate combined input structure registration");
        check(
            gregtech.api.enums.HatchElement.InputHatch.mteClasses()
                .contains(MTEInfinitePatternInputAssembly.class)
                && gregtech.api.enums.HatchElement.InputHatch.mteClasses()
                    .contains(MTEInfinitePatternInputMirror.class),
            "fluid-input structure position recognition");
        controller.startRecipeProcessing();
        check(
            buffer.getItemInputs()[0].stackSize == Integer.MAX_VALUE
                && buffer.getFluidInputs()[0].amount == Integer.MAX_VALUE,
            "legacy int views");
        buffer.getItemInputs()[0].stackSize -= 17;
        first.inventories()
            .next()
            .getFluidInputs()[0].amount -= 29;
        controller.endRecipeProcessing();
        controller.endRecipeProcessing();
        check(
            buffer.getItemAmountBig()
                .equals(HUGE.subtract(BigInteger.valueOf(17)))
                && buffer.getFluidAmountBig()
                    .equals(HUGE.subtract(BigInteger.valueOf(29))),
            "native recipe reconciliation or duplicate mirror debit");
        check(
            source.mInventory[source.catalystSlotStart(359)].stackSize == 1,
            "native recipe consumed physical catalyst");
        check(source.pushPatternBig(details, input(HUGE), HUGE, false), "batch input exceeded fixed integer range");
        check(
            source.getSavedPushCallsBig()
                .equals(HUGE.subtract(BigInteger.ONE)),
            "batch optimization counter did not increase");
        buffer.setLocked(true);
        BigInteger items = HUGE.multiply(HUGE.add(BigInteger.ONE))
            .subtract(BigInteger.valueOf(17));
        BigInteger fluids = HUGE.multiply(HUGE.add(BigInteger.ONE))
            .subtract(BigInteger.valueOf(29));
        check(
            buffer.getItemAmountBig()
                .equals(items)
                && buffer.getFluidAmountBig()
                    .equals(fluids),
            "exact batch accumulation");
        NBTTagCompound saved = new NBTTagCompound();
        source.saveNBTData(saved);
        MTEInfinitePatternInputAssembly restored = (MTEInfinitePatternInputAssembly) tile(4).getMetaTileEntity();
        restored.loadNBTData(saved);
        restored.loadNBTData(saved);
        check(
            restored.getMultiplierBig(359)
                .equals(HUGE)
                && restored.getPatterns()
                    .getStackInSlot(359) != null,
            "360th pattern and multiplier reload");
        check(
            restored.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(items)
                && restored.getBuffers()
                    .get(0)
                    .getFluidAmountBig()
                    .equals(fluids),
            "repeated NBT reload duplicated or lost input");
        check(
            restored.getBuffers()
                .get(0)
                .isLocked()
                && restored.getBuffers()
                    .get(0)
                    .hasRecipe(),
            "recipe lock or input shape not persisted");
        NBTTagCompound drop = new NBTTagCompound();
        source.setItemNBT(drop);
        MTEInfinitePatternInputAssembly replaced = (MTEInfinitePatternInputAssembly) tile(4).getMetaTileEntity();
        replaced.loadNBTData(drop);
        check(
            replaced.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(items) && replaced.mInventory[359] != null
                && replaced.mInventory[replaced.catalystSlotStart(359)] != null
                && !source.shouldDropItemAt(359),
            "drop lost or duplicated patterns, catalysts or inputs");
        NBTTagCompound link = new NBTTagCompound();
        first.saveNBTData(link);
        first.clearLink();
        check(
            !first.inventories()
                .hasNext(),
            "unbound mirror retained inventory");
        first.loadNBTData(link);
        check(
            first.inventories()
                .next() == buffer,
            "saved mirror link not restored");
        first.source = null;
        check(
            !first.inventories()
                .hasNext(),
            "unloaded source remained usable");
        first.source = source;
        check(
            first.inventories()
                .next() == buffer,
            "reloaded source not reattached");
        verifyWatchers(source, first);
        verifyBufferBounds();
        check(
            ApeironMachines.patternInputAssembly.getBuffers()
                .stream()
                .allMatch(BigPatternBuffer::isEmpty),
            "registered source prototype mutated");
        Apeiron.LOG.info(
            "Infinite pattern inputs: 360 patterns, exact AE tables, multipliers, two mirrors, native consumption, catalysts and persistence verification passed");
    }

    public static void verifyBatchDispatch() {
        Assembly machine = new Assembly();
        machine.getPatterns()
            .setInventorySlotContents(0, pattern());
        ICraftingPatternDetails details = machine.getPatternDetails(0);
        appeng.crafting.MECraftingInventory inventory = new appeng.crafting.MECraftingInventory();
        BigInteger batches = BigInteger.TEN.pow(60);
        inventory.injectItems(
            BigAEStackValues.copyWithSize(
                AEItemStack.create(new ItemStack(Items.diamond)),
                batches.subtract(BigInteger.ONE)
                    .multiply(BigInteger.valueOf(2))),
            appeng.api.config.Actionable.MODULATE);
        MEInventoryCrafting table = new MEInventoryCrafting(new ContainerNull(), 1, 1);
        table.setInventorySlotContents(0, AEItemStack.create(new ItemStack(Items.diamond, 2)));
        double[] power = { 0 };
        appeng.api.networking.energy.IEnergyGrid energy = (appeng.api.networking.energy.IEnergyGrid) java.lang.reflect.Proxy
            .newProxyInstance(
                InfinitePatternInputSmoke.class.getClassLoader(),
                new Class<?>[] { appeng.api.networking.energy.IEnergyGrid.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("extractAEPower")) {
                        if (args[1] == appeng.api.config.Actionable.MODULATE) power[0] += (Double) args[0];
                        return args[1] == appeng.api.config.Actionable.SIMULATE ? Double.MAX_VALUE : args[0];
                    }
                    return null;
                });
        BigInteger dispatched = com.silvia.apeiron.ae.crafting.core.PatternBatchDispatch
            .dispatch(machine, details, table, inventory, batches, energy);
        check(dispatched.equals(batches), "CPU batch dispatch capped at long");
        check(
            machine.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(batches.multiply(BigInteger.valueOf(2))),
            "CPU batch input conservation");
        check(
            machine.getSavedPushCallsBig()
                .equals(batches.subtract(BigInteger.ONE)),
            "CPU batch skipped-call statistic");
        check(
            inventory.getAvailableItem(AEItemStack.create(new ItemStack(Items.diamond)))
                .getStackSize() == 0 && power[0] > 0,
            "CPU batch debit or power consumption missing");
        BigPatternBuffer buffer = machine.getBuffers()
            .get(0);
        buffer.setLocked(true);
        check(
            buffer.getPossibleBatchesBig()
                .equals(batches),
            "locked exact recipe copies");
        machine.setMultiplierBig(0, BigInteger.valueOf(2));
        check(
            machine.pushPatternBig(
                machine.getPatternDetails(0),
                java.util.Collections.singletonList(AEItemStack.create(new ItemStack(Items.diamond, 4))),
                BigInteger.ONE,
                false),
            "new multiplier blocked all buffers");
        check(
            machine.getBuffers()
                .get(1)
                .getItemAmountBig()
                .equals(BigInteger.valueOf(4)),
            "new recipe shape reused locked old buffer");
        table.setInventorySlotContents(
            0,
            BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), BigInteger.TEN.pow(59)));
        machine.setMultiplierBig(0, BigInteger.TEN.pow(60));
        BigInteger rejected = com.silvia.apeiron.ae.crafting.core.PatternBatchDispatch
            .dispatch(machine, machine.getPatternDetails(0), table, inventory, BigInteger.ONE, energy);
        check(rejected.signum() == 0, "saturated partial first input created full output");
        try {
            Assembly cpuMachine = new Assembly();
            cpuMachine.getPatterns()
                .setInventorySlotContents(0, pattern());
            ICraftingPatternDetails cpuPattern = cpuMachine.getPatternDetails(0);
            appeng.me.cluster.implementations.CraftingCPUCluster cpu = new appeng.me.cluster.implementations.CraftingCPUCluster(
                new appeng.api.util.WorldCoord(0, 0, 0),
                new appeng.api.util.WorldCoord(0, 0, 1));
            java.lang.reflect.Method add = cpu.getClass()
                .getDeclaredMethod("addTile", appeng.tile.crafting.TileCraftingTile.class);
            add.setAccessible(true);
            add.invoke(cpu, new com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingUnit());
            ((com.silvia.apeiron.ae.crafting.core.BigCraftingCPU) cpu).addCraftingBig(cpuPattern, batches);
            cpu.getInventory()
                .injectItems(
                    BigAEStackValues.copyWithSize(
                        AEItemStack.create(new ItemStack(Items.diamond)),
                        batches.subtract(BigInteger.ONE)
                            .multiply(BigInteger.valueOf(2))),
                    appeng.api.config.Actionable.MODULATE);
            java.lang.reflect.Field operations = cpu.getClass()
                .getDeclaredField("remainingOperations");
            operations.setAccessible(true);
            operations.setInt(cpu, 1);
            table.setInventorySlotContents(0, AEItemStack.create(new ItemStack(Items.diamond, 2)));
            java.lang.reflect.Method dispatch = null;
            for (java.lang.reflect.Method method : cpu.getClass()
                .getDeclaredMethods())
                if (method.getName()
                    .endsWith("$apeiron$batch")) dispatch = method;
            check(dispatch != null, "native CPU batch hook missing");
            dispatch.setAccessible(true);
            check(
                (Boolean) dispatch.invoke(cpu, cpuMachine, cpuPattern, table, energy),
                "native CPU batch hook rejected complete inputs");
            com.silvia.apeiron.ae.crafting.core.BigTaskProgress progress = (com.silvia.apeiron.ae.crafting.core.BigTaskProgress) ((com.silvia.apeiron.ae.crafting.core.BigCraftingCPU) cpu)
                .getTaskEntriesBig()
                .get(cpuPattern);
            check(
                progress.getValueBig()
                    .equals(BigInteger.ONE) && operations.getInt(cpu) == 1,
                "CPU extra batch task accounting or operation budget");
            check(
                ((com.silvia.apeiron.ae.crafting.core.BigCraftingCPU) cpu)
                    .getStackAmountBig(
                        cpuPattern.getCondensedAEOutputs()[0],
                        appeng.api.networking.crafting.CraftingItemList.ACTIVE)
                    .equals(batches.subtract(BigInteger.ONE)),
                "CPU exact batch pending-output accounting");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native CPU batch verification failed", error);
        }

    }

    private static void verifyOptimization() {
        Assembly source = new Assembly();
        source.getPatterns()
            .setInventorySlotContents(0, pattern());
        source.setTerminalVisible(false);
        check(!source.shouldDisplay(), "terminal visibility switch");
        source.setTerminalVisible(true);
        if (cpw.mods.fml.common.Loader.isModLoaded("programmablehatches")) {
            check(
                (Object) source instanceof reobf.proghatches.gt.metatileentity.util.ISpecialOptimize,
                "optimization bridge missing");
            codechicken.nei.ItemStackMap<org.apache.commons.lang3.tuple.Pair<Object, Integer>> map = new codechicken.nei.ItemStackMap<>();
            map.put(
                source.getPatternDetails(0)
                    .getPattern(),
                org.apache.commons.lang3.tuple.Pair.of(null, 7));
            ((reobf.proghatches.gt.metatileentity.util.ISpecialOptimize) (Object) source).optimize(map);
            check(
                source.getMultiplierBig(0)
                    .equals(BigInteger.valueOf(128)),
                "optimizer failed to apply 128 multiplier");
            source.setPatternOptimization(false);
            map.clear();
            map.put(
                source.getPatternDetails(0)
                    .getPattern(),
                org.apache.commons.lang3.tuple.Pair.of(null, 1));
            ((reobf.proghatches.gt.metatileentity.util.ISpecialOptimize) (Object) source).optimize(map);
            check(
                source.getMultiplierBig(0)
                    .equals(BigInteger.valueOf(128)),
                "disabled optimization changed multiplier");
        }
    }

    private static void verifyMultiplierLifecycle() {
        Assembly source = new Assembly();
        net.minecraft.inventory.IInventory patterns = source.getPatterns();
        patterns.setInventorySlotContents(0, pattern());
        source.setMultiplierBig(0, HUGE);
        ICraftingPatternDetails oldTask = source.getPatternDetails(0);
        check(patterns.decrStackSize(0, 1) != null, "terminal extraction lost physical pattern");
        check(
            source.getMultiplierBig(0)
                .equals(BigInteger.ONE),
            "terminal extraction retained multiplier");
        patterns.setInventorySlotContents(0, pattern());
        check(
            !(source.getPatternDetails(0) instanceof MultipliedPatternDetails),
            "reinserted pattern inherited multiplier");
        check(
            ((MultipliedPatternDetails) oldTask).getMultiplierBig()
                .equals(HUGE),
            "removal changed in-flight task multiplier");

        source.setMultiplierBig(0, HUGE);
        ItemStack replacement = pattern();
        replacement.getTagCompound()
            .setString("author", "Replacement encoder");
        patterns.setInventorySlotContents(0, replacement);
        check(
            source.getMultiplierBig(0)
                .equals(BigInteger.ONE),
            "terminal replacement retained multiplier");
        source.setMultiplierBig(0, HUGE);
        patterns.setInventorySlotContents(0, replacement.copy());
        check(
            source.getMultiplierBig(0)
                .equals(BigInteger.ONE),
            "identical replacement retained multiplier");

        source.setMultiplierBig(0, HUGE);
        source.getInventoryHandler()
            .extractItem(0, 1, true);
        check(
            source.getMultiplierBig(0)
                .equals(HUGE),
            "simulated GUI extraction reset multiplier");
        source.getInventoryHandler()
            .extractItem(0, 1, false);
        check(
            source.getMultiplierBig(0)
                .equals(BigInteger.ONE),
            "GUI extraction retained multiplier");
        source.getInventoryHandler()
            .setStackInSlot(0, pattern());
        source.setMultiplierBig(0, HUGE);
        source.getInventoryHandler()
            .setStackInSlot(0, replacement.copy());
        check(
            source.getMultiplierBig(0)
                .equals(BigInteger.ONE),
            "GUI replacement retained multiplier");

        source.setMultiplierBig(0, HUGE);
        source.refreshPatterns();
        patterns.markDirty();
        check(
            source.getMultiplierBig(0)
                .equals(HUGE),
            "unchanged inventory refresh reset multiplier");
        patterns.getStackInSlot(0)
            .getTagCompound()
            .setString("author", "Edited in place");
        patterns.markDirty();
        check(
            source.getMultiplierBig(0)
                .equals(BigInteger.ONE),
            "in-place edit after refresh retained multiplier");

        source.setMultiplierBig(0, HUGE);
        source.mInventory[source.catalystSlotStart(0)] = new ItemStack(Items.gold_ingot);
        source.onContentsChanged(source.catalystSlotStart(0));
        check(
            source.getMultiplierBig(0)
                .equals(HUGE),
            "catalyst edit reset pattern multiplier");
        NBTTagCompound saved = new NBTTagCompound();
        source.saveNBTData(saved);
        Assembly restored = new Assembly();
        restored.loadNBTData(saved);
        restored.getPatterns()
            .markDirty();
        check(
            restored.getMultiplierBig(0)
                .equals(HUGE),
            "reload reset occupied pattern multiplier");
        check(restored.getPatternDetails(0) instanceof MultipliedPatternDetails, "reload lost advertised multiplier");
        Mirror mirror = new Mirror(restored);
        mirror.getInputSource()
            .getPatterns()
            .getStackInSlotOnClosing(0);
        check(
            restored.getMultiplierBig(0)
                .equals(BigInteger.ONE),
            "mirror removal retained multiplier");

        source.scaleMultipliers(false, false);
        source.getInventoryHandler()
            .setStackInSlot(1, pattern());
        check(
            source.getMultiplierBig(1)
                .equals(BigInteger.ONE),
            "new pattern inherited bulk multiplier from empty slot");
        source.setMultiplierBig(1, HUGE);
        source.setInventorySlotContents(1, null);
        check(
            source.getMultiplierBig(1)
                .equals(BigInteger.ONE),
            "native GT removal retained multiplier");
        source.getPatterns()
            .setInventorySlotContents(1, pattern());
        source.setMultiplierBig(1, HUGE);
        source.mInventory[1] = null;
        source.getPatterns()
            .markDirty();
        check(
            source.getMultiplierBig(1)
                .equals(BigInteger.ONE),
            "in-place removal retained multiplier");
        Apeiron.LOG.info(
            "Pattern multiplier lifecycle verification passed: GUI/AE/mirror/GT removal and replacement, simulations, bulk edits, in-place edits, reload, catalysts and in-flight tasks");
    }

    private static void verifyWatchers(Assembly source, Mirror mirror) {
        int[] calls = { 0 };
        Object watcher = new WatcherProbe(calls);
        source.addWatcherCompat(watcher);
        mirror.addWatcherCompat(watcher);
        source.setMultiplierBig(0, BigInteger.valueOf(2));
        check(calls[0] == 1, "shared watcher notified more than once");
        mirror.removeWatcherCompat(watcher);
        source.setMultiplierBig(0, BigInteger.valueOf(3));
        check(calls[0] == 2, "removing mirror removed direct source watcher");
        mirror.addWatcherCompat(watcher);
        mirror.onUnload();
        source.setMultiplierBig(0, BigInteger.valueOf(4));
        check(calls[0] == 3, "unloaded mirror removed direct source watcher");
        mirror.getInputSource();
        source.setMultiplierBig(0, BigInteger.valueOf(5));
        check(calls[0] == 4, "reloaded mirror duplicated watcher subscription");
        mirror.removeWatcherCompat(watcher);
        source.removeWatcherCompat(watcher);
    }

    public static final class WatcherProbe {

        private final int[] calls;

        public WatcherProbe(int[] calls) {
            this.calls = calls;
        }

        public void scheduleRecipeCheckImmediate() {
            calls[0]++;
        }
    }

    private static void verifyBufferBounds() {
        BigPatternBuffer buffer = new BigPatternBuffer();
        buffer.assign(0, pattern(), java.util.Collections.emptyList());
        check(buffer.add(input(HUGE), BigInteger.ONE), "fresh buffer insertion");
        BigInteger before = buffer.getItemAmountBig();
        buffer.getItemInputs()[0].stackSize++;
        boolean rejected = false;
        try {
            buffer.reconcile();
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "invalid recipe mutation accepted");
        check(
            buffer.getItemAmountBig()
                .equals(before),
            "failed reconciliation partially mutated exact inventory");
        buffer.getItemInputs()[0].stackSize = Integer.MAX_VALUE;
        buffer.reconcile();
        check(
            buffer.getItemAmountBig()
                .equals(HUGE),
            "reconciliation changed unconsumed quantity");
        buffer.getItemInputs()[0].stackSize -= 17;
        buffer.removeBig(input(HUGE).get(0), HUGE.subtract(BigInteger.valueOf(17)));
        check(
            buffer.getItemAmountBig()
                .signum() == 0 && buffer.getItemInputs().length == 0,
            "refund retained stale item view or missed native consumption");
        buffer.removeBig(input(HUGE).get(1), HUGE);
        check(
            buffer.isEmpty() && buffer.getStacksBig()
                .isEmpty(),
            "refund retained zero-quantity types");

        List<IAEStack<?>> types = new java.util.ArrayList<>();
        for (int i = 0; i < BigPatternBuffer.ITEM_SLOTS; i++)
            types.add(BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.dye, 1, i)), HUGE));
        check(buffer.add(types, BigInteger.ONE), "32 distinct item types rejected");
        check(
            !buffer.add(input(HUGE), BigInteger.ONE) && buffer.getFluidAmountBig()
                .signum() == 0,
            "type overflow partially committed fluid input");
        buffer.removeBig(types.get(0), HUGE);
        check(buffer.add(input(HUGE), BigInteger.ONE), "refunded type did not release its buffer slot");
    }

    private static void verifyDuplicatePatterns() {
        Assembly source = new Assembly();
        ItemStack original = pattern();
        original.getTagCompound()
            .setString("author", "First encoder");
        source.getPatterns()
            .setInventorySlotContents(0, original);
        ItemStack duplicate = pattern();
        duplicate.getTagCompound()
            .setString("author", "Second encoder");
        duplicate.getTagCompound()
            .setLong("createdAt", 123456789L);
        duplicate.setStackDisplayName("Different label, same recipe");
        NBTTagCompound untouched = duplicate.writeToNBT(new NBTTagCompound());
        check(!source.isItemValidForSlot(359, duplicate), "duplicate with different bookkeeping accepted");
        check(
            !source.getPatterns()
                .isItemValidForSlot(359, duplicate),
            "AE terminal accepted duplicate");
        com.cleanroommc.modularui.widgets.slot.ModularSlot guiSlot = new com.cleanroommc.modularui.widgets.slot.ModularSlot(
            source.getInventoryHandler(),
            359);
        check(!guiSlot.isItemValid(duplicate), "GUI slot accepted a duplicate pattern");
        check(
            duplicate.stackSize == 1 && source.mInventory[359] == null
                && untouched.equals(duplicate.writeToNBT(new NBTTagCompound())),
            "GUI duplicate rejection modified or consumed a pattern");
        check(source.isItemValidForSlot(0, duplicate), "replacement compared against its own slot");
        Mirror mirror = new Mirror(source);
        check(
            !mirror.getInputSource()
                .isItemValidForSlot(1, duplicate),
            "mirror bypassed source duplicate validation");

        ItemStack split = processingPattern(
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.diamond)),
                AEItemStack.create(new ItemStack(Items.diamond)) },
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald)) });
        check(!source.isItemValidForSlot(1, split), "equivalent split inputs were not recognized");
        ItemStack amount = processingPattern(
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.diamond, 3)) },
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald)) });
        check(source.isItemValidForSlot(1, amount), "different input amount was rejected");
        ItemStack output = processingPattern(
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.diamond, 2)) },
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald, 2)) });
        check(source.isItemValidForSlot(1, output), "different output amount was rejected");
        ItemStack taggedDiamond = new ItemStack(Items.diamond, 2);
        taggedDiamond.setTagCompound(new NBTTagCompound());
        taggedDiamond.getTagCompound()
            .setString("recipeVariant", "different ingredient");
        ItemStack tagged = processingPattern(
            new IAEStack<?>[] { AEItemStack.create(taggedDiamond) },
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald)) });
        check(source.isItemValidForSlot(1, tagged), "ingredient NBT was ignored");
        for (String setting : new String[] { "substitute", "beSubstitute" }) {
            ItemStack variant = duplicate.copy();
            variant.getTagCompound()
                .setBoolean(setting, true);
            check(source.isItemValidForSlot(1, variant), "different substitution setting was rejected");
        }
        source.setMultiplierBig(1, BigInteger.valueOf(2));
        check(!source.isItemValidForSlot(1, duplicate), "empty slot multiplier bypassed insertion duplicate check");
        source.setMultiplierBig(0, BigInteger.valueOf(2));
        check(source.isItemValidForSlot(1, duplicate), "different stored effective multiplier was rejected");
        source.setMultiplierBig(0, BigInteger.ONE);
        source.setMultiplierBig(1, BigInteger.ONE);
        source.mInventory[source.catalystSlotStart(1)] = new ItemStack(Items.gold_ingot);
        check(source.isItemValidForSlot(1, duplicate), "different per-pattern catalyst was rejected");
        source.mInventory[source.catalystSlotStart(1)] = null;

        source.getPatterns()
            .setInventorySlotContents(0, amount);
        check(source.isItemValidForSlot(1, duplicate), "replacing a recipe retained stale duplicate data");
        source.getPatterns()
            .setInventorySlotContents(0, null);
        check(source.isItemValidForSlot(1, duplicate), "removing a recipe retained stale duplicate data");
        ItemStack broken = pattern();
        broken.getTagCompound()
            .removeTag("out");
        NBTTagCompound brokenBefore = broken.writeToNBT(new NBTTagCompound());
        check(!source.isItemValidForSlot(1, broken), "broken recipe accepted");
        check(brokenBefore.equals(broken.writeToNBT(new NBTTagCompound())), "validation mutated broken pattern NBT");

        ItemStack exact = processingPattern(
            input(HUGE).toArray(new IAEStack<?>[0]),
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald)) });
        source.getPatterns()
            .setInventorySlotContents(0, exact);
        ItemStack reordered = processingPattern(
            new IAEStack<?>[] { input(HUGE).get(1), input(HUGE).get(0) },
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald)) });
        check(!source.isItemValidForSlot(1, reordered), "same mixed recipe in different input order accepted");
        ItemStack changed = processingPattern(
            input(HUGE.add(BigInteger.ONE)).toArray(new IAEStack<?>[0]),
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald)) });
        check(source.isItemValidForSlot(1, changed), "exact quantities were compared using saturated counts");
        List<IAEStack<?>> changedFluid = input(HUGE);
        changedFluid.set(1, AEFluidStack.create(new FluidStack(FluidRegistry.LAVA, 1)));
        ItemStack lava = processingPattern(
            changedFluid.toArray(new IAEStack<?>[0]),
            new IAEStack<?>[] { AEItemStack.create(new ItemStack(Items.emerald)) });
        check(source.isItemValidForSlot(1, lava), "different fluid ingredient was rejected");
        NBTTagCompound saved = new NBTTagCompound();
        source.saveNBTData(saved);
        Assembly restored = new Assembly();
        restored.loadNBTData(saved);
        check(!restored.isItemValidForSlot(1, reordered), "duplicate validation did not survive reload");
        Apeiron.LOG.info(
            "Pattern duplicate verification passed: AE/GUI/mirror insertion, bookkeeping ignored, exact mixed recipes, amounts, ingredient NBT, settings, multipliers, catalysts and reload");
    }

    private static void verifyInsertionCache() {
        Assembly source = new Assembly();
        CountingPatternItem item = new CountingPatternItem();
        for (int slot = 0; slot < 359; slot++) {
            source.getPatterns()
                .setInventorySlotContents(slot, item.wrap(largePattern(slot)));
            check(source.getPatternDetails(slot) != null, "performance fixture did not decode");
        }
        check(item.decodes == 359, "adding a pattern reparsed unchanged occupied slots");
        ICraftingPatternDetails retained = source.getPatternDetails(0);
        ItemStack duplicate = item.wrap(largePattern(0));
        duplicate.getTagCompound()
            .setLong("encodedAt", 123456789L);
        duplicate.getTagCompound()
            .setString("author", "Another encoder");
        int before = item.decodes, lookups = source.patternLookups;
        for (int slot = 0; slot < 360; slot++)
            check(source.isItemValidForSlot(slot, duplicate) == (slot == 0), "duplicate quick-move probe changed");
        check(item.decodes == before + 1, "quick move decoded one candidate repeatedly");
        check(source.patternLookups == lookups + 360, "quick move repeated its full duplicate scan per slot");
        for (int slot = 0; slot < 360; slot++) check(
            !source.getInventoryHandler()
                .isItemValid(slot, duplicate) || slot == 0,
            "GUI duplicate bypass");
        check(
            !source.getPatterns()
                .isItemValidForSlot(359, duplicate),
            "AE duplicate bypass");
        check(
            !new Mirror(source).getInputSource()
                .isItemValidForSlot(359, duplicate),
            "mirror duplicate bypass");
        check(item.decodes == before + 1 && source.patternLookups == lookups + 360, "entry points missed shared cache");
        QuickMoveContainer container = new QuickMoveContainer(source);
        ItemStack rejected = container.move(duplicate.copy());
        check(
            rejected != null && rejected.stackSize == 1 && source.mInventory[359] == null,
            "native quick move accepted duplicate");
        check(
            item.decodes == before + 1 && source.patternLookups == lookups + 360,
            "native quick move repeated cached work: decodes=" + (item.decodes - before)
                + ", lookups="
                + (source.patternLookups - lookups));

        ItemStack next = item.wrap(largePattern(359));
        before = item.decodes;
        lookups = source.patternLookups;
        for (int slot = 0; slot < 360; slot++) check(source.isItemValidForSlot(slot, next), "unique recipe rejected");
        check(item.decodes == before + 1 && source.patternLookups == lookups + 360, "unique insertion rescanned slots");
        ItemStack remainder = container.move(next.copy());
        check(
            (remainder == null || remainder.stackSize == 0) && source.mInventory[359] != null,
            "native quick move missed empty slot");
        source.getPatterns()
            .markDirty();
        check(source.getPatternDetails(359) != null, "validated recipe was not advertised");
        check(item.decodes == before + 1, "inserted candidate was decoded again instead of promoted");
        check(source.getPatternDetails(0) == retained, "insertion discarded unrelated advertised pattern identity");
        check(!source.isItemValidForSlot(358, next), "newly inserted pattern missing from duplicate check");
        source.getPatterns()
            .setInventorySlotContents(359, null);
        check(source.isItemValidForSlot(358, next), "removed pattern retained duplicate match");

        source.setMultiplierBig(359, HUGE);
        before = item.decodes;
        check(!source.isItemValidForSlot(359, duplicate), "empty slot multiplier bypassed cached duplicates");
        check(item.decodes == before + 1, "candidate switch did not decode exactly once");
        source.setMultiplierBig(359, BigInteger.ONE);
        check(!source.isItemValidForSlot(359, duplicate), "multiplier reset retained stale matches");
        source.scaleMultipliers(false, true);
        check(!source.isItemValidForSlot(359, duplicate), "bulk multiplier reset changed duplicates");
        check(item.decodes == before + 1, "multiplier edits reparsed stored recipes");
        source.mInventory[source.catalystSlotStart(359)] = new ItemStack(Items.gold_ingot);
        source.onContentsChanged(source.catalystSlotStart(359));
        check(source.isItemValidForSlot(359, duplicate), "catalyst change retained stale validity");
        source.mInventory[source.catalystSlotStart(359)] = null;
        source.onContentsChanged(source.catalystSlotStart(359));
        check(!source.isItemValidForSlot(359, duplicate), "catalyst removal bypassed duplicate rejection");

        duplicate.setTagCompound(
            (NBTTagCompound) largePattern(400).getTagCompound()
                .copy());
        check(source.isItemValidForSlot(359, duplicate), "in-place candidate edit reused old recipe");
        source.mInventory[20].setTagCompound(
            (NBTTagCompound) duplicate.getTagCompound()
                .copy());
        source.getPatterns()
            .markDirty();
        check(!source.isItemValidForSlot(359, duplicate), "in-place stored edit retained old recipe");
        check(
            source.getPatternDetails(0) != null && item.decodes == before + 2,
            "in-place edit reparsed unrelated slots");

        ItemStack broken = item.wrap(largePattern(500));
        broken.getTagCompound()
            .setBoolean("verificationBroken", true);
        NBTTagCompound original = (NBTTagCompound) broken.getTagCompound()
            .copy();
        before = item.decodes;
        for (int slot = 0; slot < 360; slot++)
            check(!source.isItemValidForSlot(slot, broken), "broken recipe accepted");
        check(item.decodes == before + 1 && original.equals(broken.getTagCompound()), "broken probe cache was mutated");
        source.getPatterns()
            .setInventorySlotContents(359, broken);
        for (int attempt = 0; attempt < 5; attempt++)
            check(source.getPatternDetails(359) == null, "broken stored recipe accepted");
        check(item.decodes == before + 1, "invalid stored recipe was repeatedly decoded");
        source.refreshPatterns();
        check(
            source.getPatternDetails(359) == null && item.decodes == before + 2,
            "explicit refresh missed invalid recipe");
        check(original.equals(broken.getTagCompound()), "stored decoder mutated physical pattern");

        // Reload a warmed real-item assembly: neither null results nor old recipes may survive loading another state.
        Assembly savedSource = new Assembly();
        savedSource.getPatterns()
            .setInventorySlotContents(0, largePattern(700));
        NBTTagCompound saved = new NBTTagCompound();
        savedSource.saveNBTData(saved);
        Assembly restored = new Assembly();
        restored.getPatterns()
            .setInventorySlotContents(0, largePattern(701));
        check(restored.isItemValidForSlot(1, largePattern(700)), "reload fixture unexpectedly duplicates");
        restored.loadNBTData(saved);
        check(!restored.isItemValidForSlot(1, largePattern(700)), "reload retained insertion or stored decode cache");
        check(restored.isItemValidForSlot(1, largePattern(701)), "reload retained removed recipe");
        Apeiron.LOG.info(
            "Pattern insertion cache verification passed: 359 occupied ultimate patterns, one candidate decode and one scan per multiplier, GUI/AE/mirror probes, incremental insertion/removal, catalysts, in-place edits, invalid recipes and reload");
    }

    private static ItemStack largePattern(int variant) {
        IAEStack<?>[] inputs = new IAEStack<?>[32];
        for (int index = 0; index < 31; index++) {
            ItemStack input = new ItemStack(Items.dye, 1, index);
            input.setTagCompound(new NBTTagCompound());
            input.getTagCompound()
                .setInteger("variant", variant);
            inputs[index] = BigAEStackValues
                .copyWithSize(AEItemStack.create(input), HUGE.add(BigInteger.valueOf(index)));
        }
        inputs[31] = BigAEStackValues.copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), HUGE);
        ItemStack output = new ItemStack(Items.emerald);
        output.setTagCompound(new NBTTagCompound());
        output.getTagCompound()
            .setInteger("variant", variant);
        return processingPattern(inputs, new IAEStack<?>[] { AEItemStack.create(output) });
    }

    private static ItemStack processingPattern(IAEStack<?>[] inputs, IAEStack<?>[] outputs) {
        ItemStack result = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList in = new NBTTagList(), out = new NBTTagList();
        for (IAEStack<?> stack : inputs) in.appendTag(stack.toNBTGeneric());
        for (IAEStack<?> stack : outputs) out.appendTag(stack.toNBTGeneric());
        tag.setTag("in", in);
        tag.setTag("out", out);
        result.setTagCompound(tag);
        return result;
    }

    private static void verifyTerminalRepresentation(MTEInfinitePatternInputAssembly source) {
        ItemStack self = source.getSelfRep();
        check(
            self != null && ItemStack.areItemStacksEqual(self, source.getStackForm(1)),
            "terminal self representation must identify the pattern assembly");
        PacketInterfaceTerminalUpdate packet = new PacketInterfaceTerminalUpdate();
        NBTTagList patterns = new NBTTagList();
        for (int slot = 0; slot < MTEInfinitePatternInputAssembly.PATTERN_COUNT; slot++)
            patterns.appendTag(new NBTTagCompound());
        packet.addNewEntry(1L, source.getName(), true)
            .setLoc(10, 20, 30, 0, net.minecraftforge.common.util.ForgeDirection.UNKNOWN.ordinal())
            .setItems(source.rows(), source.rowSize(), MTEInfinitePatternInputAssembly.PATTERN_COUNT, patterns)
            .setReps(self, source.getDisplayRep())
            .setSupportedStackTypes(source.getSupportedStackTypes())
            .setTerminalVisible(source.shouldDisplay());
        packet.encode();
        ByteBuf payload = packet.getProxy()
            .payload();
        try {
            ByteBuf reader = payload.duplicate();
            reader.readInt();
            PacketInterfaceTerminalUpdate received = new PacketInterfaceTerminalUpdate(reader);
            check(!reader.isReadable(), "terminal packet left unread bytes");
            Field commandsField = PacketInterfaceTerminalUpdate.class.getDeclaredField("commands");
            commandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<PacketInterfaceTerminalUpdate.PacketEntry> commands = (List<PacketInterfaceTerminalUpdate.PacketEntry>) commandsField
                .get(received);
            check(commands.size() == 1, "terminal entry was lost in transit");
            PacketInterfaceTerminalUpdate.PacketAdd entry = (PacketInterfaceTerminalUpdate.PacketAdd) commands.get(0);
            check(
                ItemStack.areItemStacksEqual(entry.selfRep, self) && !entry.selfRep.getDisplayName()
                    .isEmpty(),
                "highlight device name was lost in transit");
            check(entry.name.equals(source.getName()), "terminal machine/custom name changed in transit");
            check(entry.x == 10 && entry.y == 20 && entry.z == 30 && entry.dim == 0, "highlight location changed");
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide()
                .isClient()) InterfaceTerminalRepresentationSmoke.verify(commands);
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Interface terminal representation verification failed", error);
        } finally {
            payload.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Infinite pattern input: " + message);
    }
}
