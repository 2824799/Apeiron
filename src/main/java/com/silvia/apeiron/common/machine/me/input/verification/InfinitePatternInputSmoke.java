package com.silvia.apeiron.common.machine.me.input.verification;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
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
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

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

        private Assembly() {
            super("apeiron.verification.pattern_input", 10, new String[0], null);
            setBaseMetaTileEntity(tile(4));
        }

        @Override
        public boolean isActive() {
            return true;
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
        verifyBatchDispatch();
        Assembly source = new Assembly();
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

    private static void verifyBatchDispatch() {
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Infinite pattern input: " + message);
    }
}
