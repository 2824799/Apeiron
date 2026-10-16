package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.Nxer.TwistSpaceTechnology.common.machine.TST_MegaCraftingCenter;
import com.Nxer.TwistSpaceTechnology.config.Config;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPU;
import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;
import com.silvia.apeiron.ae.crafting.core.PatternBatchDispatch;
import com.silvia.apeiron.ae.crafting.core.PatternBatchMedium;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.tst.BigTstOutputController;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingUnit;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.crafting.MECraftingInventory;
import appeng.helpers.UltimatePatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Real CPU dispatch and TST processing, including save boundaries and ordinary CPU limits. */
public final class AEPatternBatchDispatchSmoke {

    private AEPatternBatchDispatchSmoke() {}

    private static final class Center extends TST_MegaCraftingCenter {

        Center() {
            super("apeiron.verify.mega_crafting_batch");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        protected ArrayList<ItemStack> checkPatternInput() {
            return new ArrayList<>();
        }

        @Override
        protected void flush() {}

        void returnPatterns() {
            toReturnPatterns = true;
        }

        void complete() {
            super.outputAfterRecipe();
        }
    }

    public static void verify() {
        try {
            ICraftingPatternDetails pattern = pattern(Items.diamond, 2, 3);
            verifyBudget(pattern, false);
            verifyBudget(pattern, true);
            verifyDispatch(pattern, false);
            verifyDispatch(pattern, true);
            verifyPowerAndRollback(pattern);
            verifySinglePush();
            com.silvia.apeiron.common.machine.me.input.verification.InfinitePatternInputSmoke.verifyBatchDispatch();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("AE/TST batch dispatch verification failed", error);
        }
        Apeiron.LOG.info(
            "AE 64x dispatch budget and TST exact batches, power, rollback, containers, save/reload and completion verification passed");
    }

    private static void verifyBudget(ICraftingPatternDetails pattern, boolean unlimited)
        throws ReflectiveOperationException {
        CraftingCPUCluster cpu = cpu(pattern, BigInteger.ONE, unlimited);
        ICraftingMedium busy = (ICraftingMedium) Proxy.newProxyInstance(
            ICraftingMedium.class.getClassLoader(),
            new Class<?>[] { ICraftingMedium.class },
            (proxy, method, args) -> method.getName()
                .equals("isBusy") ? true : null);
        cpu.updateCraftingLogic(null, energy(new double[] { 0 }, Double.MAX_VALUE), cache(busy));
        check(
            field("remainingOperations").getInt(cpu) == (unlimited ? 1_048_576 : 8),
            "CPU work budget changed incorrectly");
    }

    private static void verifyDispatch(ICraftingPatternDetails pattern, boolean unlimited)
        throws ReflectiveOperationException {
        BigInteger requested = BigInteger.TEN.pow(40)
            .add(BigInteger.valueOf(17));
        BigInteger dispatched = unlimited ? requested : BigInteger.valueOf(8);
        CraftingCPUCluster cpu = cpu(pattern, requested, unlimited);
        cpu.getInventory()
            .injectItems(
                BigAEStackValues
                    .copyWithSize(pattern.getCondensedAEInputs()[0], requested.multiply(BigInteger.valueOf(2))),
                Actionable.MODULATE);
        Center center = new Center();
        double[] power = { 0 };
        long started = System.nanoTime();
        cpu.updateCraftingLogic(null, energy(power, Double.MAX_VALUE), cache(center));
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        BigTaskProgress task = (BigTaskProgress) ((BigCraftingCPU) cpu).getTaskEntriesBig()
            .get(pattern);
        check(
            (task == null ? BigInteger.ZERO : task.getValueBig()).equals(requested.subtract(dispatched)),
            "CPU batch task count");
        BigInteger produced = dispatched.multiply(BigInteger.valueOf(3));
        check(
            ((BigCraftingCPU) cpu).getStackAmountBig(pattern.getCondensedAEOutputs()[0], CraftingItemList.ACTIVE)
                .equals(produced),
            "CPU batch output reservation");
        check(
            BigAEStackValues.get(
                cpu.getInventory()
                    .getAvailableItem(pattern.getCondensedAEInputs()[0]))
                .equals(
                    requested.subtract(dispatched)
                        .multiply(BigInteger.valueOf(2))),
            "CPU batch input conservation");
        double expectedPower = dispatched.multiply(BigInteger.valueOf(2))
            .doubleValue();
        check(
            Math.abs(power[0] - expectedPower) <= expectedPower * 1e-12,
            "batch energy cost differs from native crafts");

        NBTTagCompound accepted = new NBTTagCompound();
        center.saveNBTData(accepted);
        Center restored = new Center();
        restored.loadNBTData(accepted);
        restored.returnPatterns();
        check(
            restored.checkProcessing()
                .wasSuccessful(),
            "pattern return cycle failed");
        check(
            ((BigTstOutputController) (Object) restored).getRecipeItemOutputBig()
                .signum() == 0,
            "accepted crafts were emitted during pattern return");
        check(
            restored.checkProcessing()
                .wasSuccessful(),
            "TST rejected accepted batch");
        check(
            restored.mMaxProgresstime == Config.TickEveryProcess_MegaCraftingCenter,
            "batch changed native TST processing duration");
        check(
            ((BigTstOutputController) (Object) restored).getRecipeItemOutputBig()
                .equals(produced),
            "TST exact recipe output");
        NBTTagCompound running = new NBTTagCompound();
        restored.saveNBTData(running);
        Center completing = new Center();
        completing.loadNBTData(running);
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        completing.mOutputBusses.add(output);
        completing.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(produced),
            "completed output lost exact count");
        completing.complete();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(produced),
            "completed batch emitted twice");
        check(
            !completing.checkProcessing()
                .wasSuccessful(),
            "drained accepted batch ran again");
        if (unlimited) Apeiron.LOG.info("TST batch dispatch smoke: {} crafts dispatched in {} ms", dispatched, elapsed);
    }

    private static void verifyPowerAndRollback(ICraftingPatternDetails pattern) {
        MECraftingInventory inventory = new MECraftingInventory();
        inventory.injectItems(
            BigAEStackValues.copyWithSize(pattern.getCondensedAEInputs()[0], BigInteger.valueOf(18)),
            Actionable.MODULATE);
        MEInventoryCrafting table = new MEInventoryCrafting(new appeng.container.ContainerNull(), 1, 1);
        table.setInventorySlotContents(0, pattern.getAEInputs()[0].copy());
        Center center = new Center();
        double[] power = { 0 };
        BigInteger dispatched = PatternBatchDispatch.dispatch(
            (PatternBatchMedium) (Object) center,
            pattern,
            table,
            inventory,
            BigInteger.TEN,
            energy(power, 10));
        check(
            dispatched.equals(BigInteger.valueOf(5)) && power[0] == 8,
            "finite power did not bound/debit extra crafts");
        check(
            BigAEStackValues.get(inventory.getAvailableItem(pattern.getAEInputs()[0]))
                .equals(BigInteger.TEN),
            "power-limited batch consumed wrong inputs");
        PatternBatchMedium rejecting = (details, inputs, batches, simulate) -> simulate;
        power[0] = 0;
        check(
            PatternBatchDispatch
                .dispatch(rejecting, pattern, table, inventory, BigInteger.valueOf(4), energy(power, Double.MAX_VALUE))
                .signum() == 0,
            "failed commit was accepted");
        check(
            BigAEStackValues.get(inventory.getAvailableItem(pattern.getAEInputs()[0]))
                .equals(BigInteger.TEN) && power[0] == 0,
            "failed batch lost inputs or energy");
    }

    private static void verifySinglePush() {
        Center center = new Center();
        ICraftingPatternDetails pattern = pattern(Items.water_bucket, 1, 3);
        check(
            center.pushPattern(pattern, new InventoryCrafting(new appeng.container.ContainerNull(), 1, 1)),
            "ordinary single dispatch failed");
        check(
            center.checkProcessing()
                .wasSuccessful(),
            "ordinary single dispatch did not process");
        check(
            ((BigTstOutputController) (Object) center).getRecipeItemOutputBig()
                .equals(BigInteger.valueOf(4)),
            "single craft lost its container return");
    }

    private static CraftingCPUCluster cpu(ICraftingPatternDetails pattern, BigInteger count, boolean unlimited)
        throws ReflectiveOperationException {
        CraftingCPUCluster cpu = AESelfRecursiveCraftingSmoke.newCPU(null);
        if (unlimited) {
            java.lang.reflect.Method addTile = CraftingCPUCluster.class
                .getDeclaredMethod("addTile", appeng.tile.crafting.TileCraftingTile.class);
            addTile.setAccessible(true);
            addTile.invoke(cpu, new TileInfiniteCraftingUnit());
        } else field("accelerator").setInt(cpu, 7);
        field("isComplete").setBoolean(cpu, false);
        ((BigCraftingCPU) cpu).addCraftingBig(pattern, count);
        return cpu;
    }

    private static CraftingGridCache cache(ICraftingMedium medium) {
        return new CraftingGridCache(null) {

            @Override
            public List<ICraftingMedium> getMediums(ICraftingPatternDetails details) {
                return Collections.singletonList(medium);
            }
        };
    }

    private static IEnergyGrid energy(double[] charged, double available) {
        return (IEnergyGrid) Proxy.newProxyInstance(
            IEnergyGrid.class.getClassLoader(),
            new Class<?>[] { IEnergyGrid.class },
            (proxy, method, args) -> {
                if (!method.getName()
                    .equals("extractAEPower")) return null;
                double amount = (Double) args[0];
                if (args[1] == Actionable.MODULATE) charged[0] += amount;
                return Math.min(amount, available);
            });
    }

    private static ICraftingPatternDetails pattern(Item item, int input, int output) {
        ItemStack encoded = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("in", stacks(new ItemStack(item, input)));
        tag.setTag("out", stacks(new ItemStack(Items.emerald, output)));
        encoded.setTagCompound(tag);
        return new TST_MegaCraftingCenter.ActualPattern(new UltimatePatternHelper(encoded), 1);
    }

    private static NBTTagList stacks(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        AEItemStack.create(stack)
            .writeToNBTGeneric(tag);
        NBTTagList list = new NBTTagList();
        list.appendTag(tag);
        return list;
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = CraftingCPUCluster.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
