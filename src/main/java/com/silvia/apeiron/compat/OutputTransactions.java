package com.silvia.apeiron.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.fluid.IFluidStore;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;

/** An internal transaction API keeps beta-1 fluid outputs independent of later GT interfaces. */
public final class OutputTransactions {

    private static final ClassValue<Map<String, Method>> METHODS = new ClassValue<Map<String, Method>>() {

        @Override
        protected Map<String, Method> computeValue(Class<?> type) {
            Map<String, Method> result = new HashMap<>();
            for (Method method : type.getMethods()) {
                method.setAccessible(true);
                result.put(method.getName() + ":" + method.getParameterTypes().length, method);
            }
            return result;
        }
    };

    private OutputTransactions() {}

    public static List<?> hatches(MTEMultiBlockBase machine) {
        Method method = method(machine, "getOutputHatches", 0);
        if (method != null) return (List<?>) invoke(method, machine);
        List<Object> result = new ArrayList<>();
        for (Object hatch : machine.mOutputHatches) {
            if (((gregtech.api.metatileentity.MetaTileEntity) hatch).isValid()) result.add(hatch);
        }
        return result;
    }

    public static int order(Object hatch) {
        Method method = method(hatch, "getHatchType", 0);
        return method == null ? 0 : ((Enum<?>) invoke(method, hatch)).ordinal();
    }

    public static Handle items(IOutputBus bus) {
        return new Handle(
            bus,
            bus instanceof MTEBoundlessMEOutputBus ? ((MTEBoundlessMEOutputBus) bus).createTransactionBig()
                : bus.createTransaction());
    }

    public static Handle fluids(Object hatch) {
        if (hatch instanceof MTEBoundlessMEOutputHatch)
            return new Handle(hatch, ((MTEBoundlessMEOutputHatch) hatch).createTransactionBig());
        Method create = method(hatch, "createTransaction", 0);
        if (create != null) return new Handle(hatch, invoke(create, hatch));
        if (!(hatch instanceof IFluidStore)) throw new IllegalStateException("Unsupported GT fluid output " + hatch);
        return new Handle(hatch, new LegacyFluidTransaction((IFluidStore) hatch));
    }

    public static final class Handle {

        private final Object owner;
        private final Object transaction;

        private Handle(Object owner, Object transaction) {
            this.owner = owner;
            this.transaction = transaction;
        }

        public boolean hasExactItems() {
            return transaction instanceof BigItemOutputTransaction;
        }

        public boolean hasExactFluids() {
            return transaction instanceof BigFluidOutputTransaction;
        }

        public boolean storePartialBig(IAEItemStack input, BigInteger total, BigInteger per) {
            return ((BigItemOutputTransaction) transaction).storePartialBig(input, total, per);
        }

        public boolean storePartialBig(IAEFluidStack input, BigInteger total, BigInteger per) {
            return ((BigFluidOutputTransaction) transaction).storePartialBig(input, total, per);
        }

        public void configure(boolean recipeCheck, boolean protectOutput) {
            optional(transaction, "setRecipeCheck", recipeCheck);
            optional(transaction, "setProtectOutput", protectOutput);
        }

        public boolean isFiltered() {
            Method method = method(transaction, "isFiltered", 0);
            return method != null && (Boolean) invoke(method, transaction);
        }

        public boolean isFilteredTo(Object id) {
            String specific = id instanceof GTUtility.ItemId ? "isFilteredToItem" : "isFilteredToFluid";
            Method method = method(transaction, "isFilteredTo", 1);
            if (method == null) method = method(transaction, specific, 1);
            return method == null || (Boolean) invoke(method, transaction, id);
        }

        public boolean hasAvailableSpace() {
            if (hasExactItems() || hasExactFluids()) return true;
            return (Boolean) required(transaction, "hasAvailableSpace");
        }

        public boolean storePartial(Object id, Object stack, long total, long per) {
            int before = stack instanceof FluidStack ? ((FluidStack) stack).amount : ((ItemStack) stack).stackSize;
            Method weighted = method(transaction, "storePartial", 4);
            boolean accepted = weighted != null ? (Boolean) invoke(weighted, transaction, id, stack, total, per)
                : (Boolean) required(transaction, "storePartial", id, stack);
            int after = stack instanceof FluidStack ? ((FluidStack) stack).amount : ((ItemStack) stack).stackSize;
            // beta-2's fluid transaction reports false after a partial insertion. Count movement is authoritative.
            return accepted || after < before;
        }

        public void complete(Object id) {
            Method method = method(transaction, "complete", 1);
            if (method == null)
                method = method(transaction, id instanceof GTUtility.ItemId ? "completeItem" : "completeFluid", 1);
            if (method != null) invoke(method, transaction, id);
        }

        public void commit() {
            if (transaction instanceof com.silvia.apeiron.api.machine.me.output.BigOutputTransaction)
                ((com.silvia.apeiron.api.machine.me.output.BigOutputTransaction) transaction).commit();
            else required(transaction, "commit");
        }
    }

    /** Stages a finite beta-1 tank without changing it during capacity checks. */
    public static final class LegacyFluidTransaction {

        private final IFluidStore tank;
        private FluidStack type;
        private int reserved;
        private final int free;

        private LegacyFluidTransaction(IFluidStore tank) {
            this.tank = tank;
            type = tank.getFluid() == null ? null
                : tank.getFluid()
                    .copy();
            free = Math.max(0, tank.getCapacity() - tank.getFluidAmount());
        }

        public boolean isFiltered() {
            return true;
        }

        public boolean isFilteredToFluid(GTUtility.FluidId id) {
            FluidStack stack = id.getFluidStack();
            return tank.canStoreFluid(stack) && (type == null || type.isFluidEqual(stack));
        }

        public boolean hasAvailableSpace() {
            return reserved < free;
        }

        public boolean storePartial(GTUtility.FluidId id, FluidStack stack) {
            if (!isFilteredToFluid(id)) return false;
            int inserted = Math.min(stack.amount, free - reserved);
            if (inserted <= 0) return false;
            if (type == null) type = stack.copy();
            reserved += inserted;
            stack.amount -= inserted;
            return true;
        }

        public void completeFluid(GTUtility.FluidId id) {}

        public void commit() {
            if (reserved == 0) return;
            FluidStack offered = new FluidStack(type, reserved);
            if (tank.fill(offered, true) != reserved)
                throw new IllegalStateException("GT output tank changed during transaction");
            reserved = 0;
        }
    }

    private static Method method(Object owner, String name, int parameters) {
        return METHODS.get(owner.getClass())
            .get(name + ":" + parameters);
    }

    private static void optional(Object owner, String name, Object... arguments) {
        Method method = method(owner, name, arguments.length);
        if (method != null) invoke(method, owner, arguments);
    }

    private static Object required(Object owner, String name, Object... arguments) {
        Method method = method(owner, name, arguments.length);
        if (method == null) throw new IllegalStateException("Unsupported GT output operation " + name);
        return invoke(method, owner, arguments);
    }

    private static Object invoke(Method method, Object owner, Object... arguments) {
        try {
            return method.invoke(owner, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("GT output operation failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot invoke GT output operation", failure);
        }
    }
}
