package com.silvia.apeiron.common.machine.tectech.verification;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;
import com.silvia.apeiron.api.machine.tectech.BigEyeOfHarmonyOutput;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import gregtech.api.enums.OutputBusType;
import gregtech.api.enums.OutputHatchType;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.util.GTUtility;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;
import tectech.util.FluidStackLong;
import tectech.util.ItemStackLong;

/** Real Mixin and exact-output regression checks without operating any world or player machines. */
public final class EyeOfHarmonyOutputSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private EyeOfHarmonyOutputSmoke() {}

    public static void verify() {
        if (!ApeironConfig.isEyeOfHarmonyBigOutputEnabled()) {
            check(!BigEyeOfHarmonyOutput.class.isAssignableFrom(MTEEyeOfHarmony.class), "disabled mixin still applied");
            Apeiron.LOG.info("Eye of Harmony output integration disabled as configured");
            return;
        }
        try {
            verifyControllerRouting();
            verifyRetainedOutputs();
            verifyInProgressRecipeCounts();
            verifyScaledRecipeAmounts();
            verifyFailureOutput();
            verifyPhysicalBudget();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Eye of Harmony verification failed", error);
        }
        Apeiron.LOG.info("Eye of Harmony exact output, bounded routing and persistence runtime verification passed");
    }

    private static VerificationEye eye(final List<IOutputBus> busses, final List<IOutputHatch> hatches) {
        final VerificationEye eye = new VerificationEye(busses, hatches);
        eye.setBaseMetaTileEntity(new BaseMetaTileEntity());
        check(eye instanceof BigEyeOfHarmonyOutput, "EOH mixin missing");
        return eye;
    }

    private static MTEBoundlessMEOutputBus bus() {
        final ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(0));
        final MTEBoundlessMEOutputBus bus = (MTEBoundlessMEOutputBus) tile.getMetaTileEntity();
        return bus;
    }

    private static MTEBoundlessMEOutputHatch hatch() {
        final ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(1));
        final MTEBoundlessMEOutputHatch hatch = (MTEBoundlessMEOutputHatch) tile.getMetaTileEntity();
        return hatch;
    }

    private static void verifyControllerRouting() throws ReflectiveOperationException {
        final MTEBoundlessMEOutputBus bus = bus();
        final MTEBoundlessMEOutputHatch hatch = hatch();
        final int[] itemCalls = { 0 };
        final int[] fluidCalls = { 0 };
        final VerificationEye eye = eye(
            Collections.singletonList(countingBus(bus, itemCalls)),
            Collections.singletonList(countingHatch(hatch, fluidCalls)));
        final BigEyeOfHarmonyOutput output = (BigEyeOfHarmonyOutput) eye;
        output.outputItemToAENetworkBig(new ItemStack(Items.diamond), HUGE);
        output.outputItemToAENetworkBig(new ItemStack(Items.diamond), HUGE);
        output.outputFluidToAENetworkBig(new FluidStack(FluidRegistry.WATER, 1), HUGE);
        final Method itemLegacy = MTEEyeOfHarmony.class
            .getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
        itemLegacy.setAccessible(true);
        itemLegacy.invoke(eye, new ItemStack(Items.diamond), Long.MAX_VALUE);
        final Method fluidLegacy = MTEEyeOfHarmony.class
            .getDeclaredMethod("outputFluidToAENetwork", FluidStack.class, long.class);
        fluidLegacy.setAccessible(true);
        fluidLegacy.invoke(eye, new FluidStack(FluidRegistry.WATER, 1), Long.MAX_VALUE);
        final BigInteger expectedItems = HUGE.multiply(BigInteger.valueOf(2))
            .add(BigInteger.valueOf(Long.MAX_VALUE));
        final BigInteger expectedFluid = HUGE.add(BigInteger.valueOf(Long.MAX_VALUE));
        check(
            output.getPendingItemOutputBig()
                .equals(expectedItems),
            "legacy method lost item count");
        check(
            output.getPendingFluidOutputBig()
                .equals(expectedFluid),
            "legacy method lost fluid count");
        output.flushOutputsBig();
        check(
            output.getPendingItemOutputBig()
                .signum() == 0
                && output.getPendingFluidOutputBig()
                    .signum() == 0,
            "boundless outputs rejected exact amounts");
        check(
            bus.getProvider()
                .getCachedAmountBig()
                .equals(expectedItems),
            "item transport count");
        check(
            hatch.getProvider()
                .getCachedAmountBig()
                .equals(expectedFluid),
            "fluid transport count");
        check(itemCalls[0] == 1 && fluidCalls[0] == 1, "outputs were split by represented quantity");
    }

    private static void verifyRetainedOutputs() {
        final VerificationEye eye = eye(Collections.emptyList(), Collections.emptyList());
        final BigEyeOfHarmonyOutput output = (BigEyeOfHarmonyOutput) eye;
        output.outputItemToAENetworkBig(new ItemStack(Items.diamond), HUGE);
        output.outputFluidToAENetworkBig(new FluidStack(FluidRegistry.WATER, 1), HUGE);
        output.flushOutputsBig();
        check(
            output.getPendingItemOutputBig()
                .equals(HUGE),
            "missing bus voided outputs");
        final NBTTagCompound saved = new NBTTagCompound();
        eye.saveNBTData(saved);
        final MTEBoundlessMEOutputBus bus = bus();
        final MTEBoundlessMEOutputHatch hatch = hatch();
        final VerificationEye restored = eye(Collections.singletonList(bus), Collections.singletonList(hatch));
        restored.loadNBTData(saved);
        restored.loadNBTData(saved);
        final BigEyeOfHarmonyOutput reload = (BigEyeOfHarmonyOutput) restored;
        check(
            reload.getPendingItemOutputBig()
                .equals(HUGE)
                && reload.getPendingFluidOutputBig()
                    .equals(HUGE),
            "EOH pending NBT duplicated or truncated counts");
        reload.flushOutputsBig();
        check(
            bus.getProvider()
                .getCachedAmountBig()
                .equals(HUGE),
            "reload item retry");
        check(
            hatch.getProvider()
                .getCachedAmountBig()
                .equals(HUGE),
            "reload fluid retry");
        final NBTTagCompound drop = new NBTTagCompound();
        eye.setItemNBT(drop);
        final VerificationEye replaced = eye(Collections.emptyList(), Collections.emptyList());
        replaced.loadNBTData(drop);
        check(
            ((BigEyeOfHarmonyOutput) replaced).getPendingItemOutputBig()
                .equals(HUGE)
                && ((BigEyeOfHarmonyOutput) replaced).getPendingFluidOutputBig()
                    .equals(HUGE),
            "drop lost pending outputs");
    }

    @SuppressWarnings("unchecked")
    private static void verifyInProgressRecipeCounts() throws ReflectiveOperationException {
        final VerificationEye eye = eye(Collections.emptyList(), Collections.emptyList());
        final Field itemField = MTEEyeOfHarmony.class.getDeclaredField("outputItems");
        final Field fluidField = MTEEyeOfHarmony.class.getDeclaredField("outputFluids");
        itemField.setAccessible(true);
        fluidField.setAccessible(true);
        ((List<ItemStackLong>) itemField.get(eye)).add(new ItemStackLong(new ItemStack(Items.diamond), Long.MAX_VALUE));
        ((List<FluidStackLong>) fluidField.get(eye))
            .add(new FluidStackLong(new FluidStack(FluidRegistry.WATER, 1), Long.MAX_VALUE));
        final NBTTagCompound saved = new NBTTagCompound();
        eye.saveNBTData(saved);
        BigValueCodec.writeNBT(
            saved.getTagList("ApeironRecipeItemAmounts", 10)
                .getCompoundTagAt(0),
            "Count",
            "ExactCount",
            new AdaptiveInteger(HUGE));
        BigValueCodec.writeNBT(
            saved.getTagList("ApeironRecipeFluidAmounts", 10)
                .getCompoundTagAt(0),
            "Count",
            "ExactCount",
            new AdaptiveInteger(HUGE));
        final VerificationEye restored = eye(Collections.emptyList(), Collections.emptyList());
        restored.loadNBTData(saved);
        restored.loadNBTData(saved);
        final BigEyeOfHarmonyOutput output = (BigEyeOfHarmonyOutput) restored;
        check(
            output.getRecipeItemOutputBig(0)
                .equals(HUGE)
                && output.getRecipeFluidOutputBig(0)
                    .equals(HUGE),
            "in-progress recipe count NBT");
        check(
            ((List<?>) itemField.get(restored)).size() == 1 && ((List<?>) fluidField.get(restored)).size() == 1,
            "recipe reload appended duplicate entries");
        final NBTTagCompound second = new NBTTagCompound();
        restored.saveNBTData(second);
        check(
            BigValueCodec.readNBT(
                second.getTagList("ApeironRecipeItemAmounts", 10)
                    .getCompoundTagAt(0),
                "Count",
                "ExactCount")
                .toBigInteger()
                .equals(HUGE),
            "resaving truncated recipe count");
        final ItemStackLong restoredItem = ((List<ItemStackLong>) itemField.get(restored)).get(0);
        final FluidStackLong restoredFluid = ((List<FluidStackLong>) fluidField.get(restored)).get(0);
        invokeMergedHandler(restored, "apeiron$readItemOutput", restoredItem);
        final Method itemLegacy = MTEEyeOfHarmony.class
            .getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
        itemLegacy.setAccessible(true);
        itemLegacy.invoke(restored, restoredItem.itemStack, restoredItem.stackSize);
        invokeMergedHandler(restored, "apeiron$readFluidOutput", restoredFluid);
        final Method fluidLegacy = MTEEyeOfHarmony.class
            .getDeclaredMethod("outputFluidToAENetwork", FluidStack.class, long.class);
        fluidLegacy.setAccessible(true);
        fluidLegacy.invoke(restored, restoredFluid.fluidStack, restoredFluid.amount);
        check(
            output.getPendingItemOutputBig()
                .equals(HUGE)
                && output.getPendingFluidOutputBig()
                    .equals(HUGE),
            "legacy output method used saturated recipe counts");
    }

    private static void invokeMergedHandler(final VerificationEye eye, final String name, final Object... arguments)
        throws ReflectiveOperationException {
        for (final Method method : MTEEyeOfHarmony.class.getDeclaredMethods()) {
            if (method.getName()
                .contains(name)) {
                method.setAccessible(true);
                method.invoke(eye, arguments);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    @SuppressWarnings("unchecked")
    private static void verifyScaledRecipeAmounts() throws ReflectiveOperationException {
        final VerificationEye eye = eye(Collections.emptyList(), Collections.emptyList());
        setField(eye, "yield", 0.75D);
        setField(eye, "successfulParallelAmount", 4L);
        final ItemStackLong item = new ItemStackLong(new ItemStack(Items.diamond), Long.MAX_VALUE);
        final FluidStackLong fluid = new FluidStackLong(new FluidStack(FluidRegistry.WATER, 1), Long.MAX_VALUE);
        final Field itemField = MTEEyeOfHarmony.class.getDeclaredField("outputItems");
        final Field fluidField = MTEEyeOfHarmony.class.getDeclaredField("outputFluids");
        itemField.setAccessible(true);
        fluidField.setAccessible(true);
        ((List<ItemStackLong>) itemField.get(eye)).add(item);
        ((List<FluidStackLong>) fluidField.get(eye)).add(fluid);
        invokeMergedHandler(eye, "apeiron$scaleItem", item, Long.MAX_VALUE);
        invokeMergedHandler(eye, "apeiron$scaleFluid", fluid, Long.MAX_VALUE);
        final BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(BigInteger.valueOf(3));
        final BigEyeOfHarmonyOutput output = (BigEyeOfHarmonyOutput) eye;
        check(
            output.getRecipeItemOutputBig(0)
                .equals(expected)
                && output.getRecipeFluidOutputBig(0)
                    .equals(expected),
            "scaled recipe quantities truncated before transport");
        check(
            item.stackSize == Long.MAX_VALUE && fluid.amount == Long.MAX_VALUE,
            "legacy scaled projection did not saturate");
    }

    private static void verifyFailureOutput() throws ReflectiveOperationException {
        final VerificationEye eye = eye(Collections.emptyList(), Collections.emptyList());
        setField(eye, "parallelAmount", 4L);
        setField(eye, "successfulParallelAmount", 0L);
        setField(eye, "successChance", 0.75D);
        setField(eye, "currentRecipeRocketTier", 63L);
        final Method failed = MTEEyeOfHarmony.class.getDeclaredMethod("outputFailedChance");
        failed.setAccessible(true);
        failed.invoke(eye);
        final BigInteger expected = BigInteger.valueOf(43200L)
            .shiftLeft(64);
        check(
            ((BigEyeOfHarmonyOutput) eye).getPendingFluidOutputBig()
                .equals(expected),
            "failed spacetime output clipped to long");
    }

    private static void setField(final VerificationEye eye, final String name, final Object value)
        throws ReflectiveOperationException {
        final Field field = MTEEyeOfHarmony.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(eye, value);
    }

    private static void verifyPhysicalBudget() {
        final int[] calls = { 0 };
        final IOutputBus physical = new IOutputBus() {

            @Override
            public boolean isFiltered() {
                return false;
            }

            @Override
            public boolean isFilteredToItem(final GTUtility.ItemId id) {
                return true;
            }

            @Override
            public OutputBusType getBusType() {
                return OutputBusType.StandardUnfiltered;
            }

            @Override
            public boolean storePartial(final ItemStack stack, final boolean simulate) {
                throw new AssertionError();
            }

            @Override
            public IOutputBusTransaction createTransaction() {
                final IOutputBus bus = this;
                return new IOutputBusTransaction() {

                    @Override
                    public IOutputBus getBus() {
                        return bus;
                    }

                    @Override
                    public boolean hasAvailableSpace() {
                        return true;
                    }

                    @Override
                    public boolean storePartial(final GTUtility.ItemId id, final ItemStack stack, final long total,
                        final long per) {
                        calls[0]++;
                        stack.stackSize = 0;
                        return true;
                    }

                    @Override
                    public void complete(final GTUtility.ItemId id) {}

                    @Override
                    public void commit() {}
                };
            }
        };
        final BigMachineOutputQueue queue = new BigMachineOutputQueue();
        queue.addItem(new ItemStack(Items.diamond), HUGE);
        queue.flush(Collections.singletonList(physical), Collections.emptyList(), true, true);
        check(calls[0] == 64, "legacy work was not bounded");
        check(
            queue.getItemAmountBig()
                .equals(
                    HUGE.subtract(
                        BigInteger.valueOf(Integer.MAX_VALUE)
                            .multiply(BigInteger.valueOf(64)))),
            "legacy transfer lost exact remainder");
    }

    private static IOutputBus countingBus(final MTEBoundlessMEOutputBus bus, final int[] calls) {
        return new IOutputBus() {

            @Override
            public boolean isFiltered() {
                return bus.isFiltered();
            }

            @Override
            public boolean isFilteredToItem(final GTUtility.ItemId id) {
                return bus.isFilteredToItem(id);
            }

            @Override
            public OutputBusType getBusType() {
                return bus.getBusType();
            }

            @Override
            public boolean storePartial(final ItemStack stack, final boolean simulate) {
                throw new AssertionError();
            }

            @Override
            public IOutputBusTransaction createTransaction() {
                final BigItemOutputTransaction delegate = bus.createTransactionBig();
                return (IOutputBusTransaction) countedTransaction(delegate, BigItemOutputTransaction.class, calls);
            }
        };
    }

    private static IOutputHatch countingHatch(final MTEBoundlessMEOutputHatch hatch, final int[] calls) {
        return new IOutputHatch() {

            @Override
            public boolean isFiltered() {
                return hatch.isFiltered();
            }

            @Override
            public boolean isFilteredToFluid(final GTUtility.FluidId id) {
                return hatch.isFilteredToFluid(id);
            }

            @Override
            public OutputHatchType getHatchType() {
                return ((IOutputHatch) hatch).getHatchType();
            }

            @Override
            public boolean storePartial(final FluidStack stack, final boolean simulate) {
                throw new AssertionError();
            }

            @Override
            public IOutputHatchTransaction createTransaction() {
                final BigFluidOutputTransaction delegate = hatch.createTransactionBig();
                return (IOutputHatchTransaction) countedTransaction(delegate, BigFluidOutputTransaction.class, calls);
            }
        };
    }

    private static Object countedTransaction(final Object delegate, final Class<?> api, final int[] calls) {
        List<Class<?>> interfaces = new java.util.ArrayList<>();
        interfaces.add(api);
        interfaces
            .add(api == BigFluidOutputTransaction.class ? IOutputHatchTransaction.class : IOutputBusTransaction.class);
        for (String name : new String[] { "IRecipeCheckAware", "IProtectOutputAware" }) {
            String type = "gregtech.api.interfaces.IOutputTransaction$" + name;
            if (com.silvia.apeiron.compat.DependencyCapabilities.hasClass(type)) {
                try {
                    interfaces.add(Class.forName(type));
                } catch (ClassNotFoundException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        }
        return Proxy.newProxyInstance(
            EyeOfHarmonyOutputSmoke.class.getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("storePartialBig")) calls[0]++;
                if (method.getName()
                    .equals("setRecipeCheck")
                    || method.getName()
                        .equals("setProtectOutput"))
                    return null;
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
            });
    }

    private static class VerificationEye extends MTEEyeOfHarmony {

        private final List<IOutputBus> busses;
        private final List<IOutputHatch> hatches;

        private VerificationEye(final List<IOutputBus> busses, final List<IOutputHatch> hatches) {
            super("apeiron.eoh.verification");
            this.busses = busses;
            this.hatches = hatches;
        }

        @Override
        public List<IOutputBus> getOutputBusses() {
            return busses;
        }

        @Override
        public List<IOutputHatch> getOutputHatches() {
            return hatches;
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException("Eye of Harmony output verification: " + message);
    }
}
