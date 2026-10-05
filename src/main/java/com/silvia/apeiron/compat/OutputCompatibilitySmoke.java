package com.silvia.apeiron.compat;

import java.lang.reflect.Method;
import java.math.BigInteger;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

/** Checks the actual GT-facing proxy, plus a finite tank on the old non-transaction API. */
public final class OutputCompatibilitySmoke {

    private OutputCompatibilitySmoke() {}

    public static void verify() {
        try {
            ApeironMachineTile itemTile = tile(0);
            MTEBoundlessMEOutputBus bus = (MTEBoundlessMEOutputBus) itemTile.getMetaTileEntity();
            Object transaction = bus.createTransaction();
            ItemStack items = new ItemStack(Items.diamond, 7);
            check(
                store(transaction, GTUtility.ItemId.createNoCopy(items), items),
                "native item transaction rejected input");
            check(
                items.stackSize == 0 && bus.getProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
                "native item transaction committed during simulation");
            commit(transaction);
            check(
                bus.getProvider()
                    .getCachedAmountBig()
                    .equals(BigInteger.valueOf(7)),
                "native item transaction lost output");

            if (DependencyCapabilities.hasClass("gregtech.api.interfaces.IOutputHatchTransaction")) {
                MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) tile(1).getMetaTileEntity();
                Object fluidTransaction = NativeMEOutputTransactions.wrap(hatch, hatch.createTransactionBig(), true);
                FluidStack fluid = new FluidStack(FluidRegistry.WATER, 13);
                check(
                    store(fluidTransaction, GTUtility.FluidId.create(fluid), fluid),
                    "native fluid transaction rejected input");
                check(
                    fluid.amount == 0 && hatch.getProvider()
                        .getCachedAmountBig()
                        .signum() == 0,
                    "native fluid transaction committed during simulation");
                commit(fluidTransaction);
                check(
                    hatch.getProvider()
                        .getCachedAmountBig()
                        .equals(BigInteger.valueOf(13)),
                    "native fluid transaction lost output");
            }

            MTEHatchOutput tank = new MTEHatchOutput("apeiron.verify.legacy_fluid_transaction", 1, new String[0], null);
            tank.setBaseMetaTileEntity(new BaseMetaTileEntity());
            OutputTransactions.Handle finite = OutputTransactions.fluids(tank);
            finite.configure(true, true);
            FluidStack offered = new FluidStack(FluidRegistry.WATER, tank.getCapacity() + 5);
            check(
                finite.storePartial(GTUtility.FluidId.create(offered), offered, 1, 1),
                "finite fluid tank rejected input");
            check(
                offered.amount == 5 && tank.getFluidAmount() == 0,
                "finite fluid simulation exceeded tank capacity or changed tank");
            finite.commit();
            check(tank.getFluidAmount() == tank.getCapacity(), "finite fluid commit did not conserve output");
            verifyNativeBatches();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("GregTech output API verification failed", error);
        }
        Apeiron.LOG
            .info("GregTech output API verification passed: native transactions, simulation and finite fluid capacity");
    }

    private static void verifyNativeBatches() throws ReflectiveOperationException {
        MTEElectricBlastFurnace machine = new MTEElectricBlastFurnace("apeiron.verify.native_batch");
        machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
        MTEBoundlessMEOutputBus bus = (MTEBoundlessMEOutputBus) tile(0).getMetaTileEntity();
        MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) tile(1).getMetaTileEntity();
        bus.getProvider()
            .setCheckMode(true);
        hatch.getProvider()
            .setCheckMode(true);
        machine.addOutputBusToMachineList(bus.getBaseMetaTileEntity(), 0);
        machine.addOutputHatchToMachineList(hatch.getBaseMetaTileEntity(), 0);
        check(InfiniteEnergyHatches.find(machine) == null, "native batch unexpectedly has an energy hatch");
        ItemStack[] items = new ItemStack[257];
        FluidStack[] fluids = new FluidStack[257];
        for (int i = 0; i < items.length; i++) {
            items[i] = new ItemStack(Items.diamond, Integer.MAX_VALUE);
            fluids[i] = new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE);
        }
        check(machine.addItemOutputs(items), "native item batch failed");
        Method outputFluids = MTEMultiBlockBase.class.getDeclaredMethod("addFluidOutputs", FluidStack[].class);
        outputFluids.setAccessible(true);
        outputFluids.invoke(machine, (Object) fluids);
        BigInteger expected = BigInteger.valueOf(Integer.MAX_VALUE)
            .multiply(BigInteger.valueOf(items.length));
        check(
            bus.getProvider()
                .getCachedAmountBig()
                .equals(expected),
            "native item batch truncated or repeated");
        check(
            hatch.getProvider()
                .getCachedAmountBig()
                .equals(expected),
            "native fluid batch truncated or repeated");
        check(
            items[0].stackSize == Integer.MAX_VALUE && fluids[0].amount == Integer.MAX_VALUE,
            "batch changed native source stacks");
        check(
            ((BigWirelessController) (Object) machine).getWirelessRecipeState()
                .pending()
                .isEmpty(),
            "batch retained int-sized output cycles");
    }

    private static ApeironMachineTile tile(int offset) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static boolean store(Object transaction, Object id, Object stack) throws ReflectiveOperationException {
        for (Method method : transaction.getClass()
            .getMethods()) {
            if (!method.getName()
                .equals("storePartial")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length < 2 || !parameters[1].isInstance(stack)) continue;
            return (Boolean) method.invoke(
                transaction,
                parameters.length == 4 ? new Object[] { id, stack, 1L, 1L } : new Object[] { id, stack });
        }
        throw new IllegalStateException("Missing native output store operation");
    }

    private static void commit(Object transaction) throws ReflectiveOperationException {
        transaction.getClass()
            .getMethod("commit")
            .invoke(transaction);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
