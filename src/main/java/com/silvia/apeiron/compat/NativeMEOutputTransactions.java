package com.silvia.apeiron.compat;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.me.output.BigFluidOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigItemOutputTransaction;
import com.silvia.apeiron.api.machine.me.output.BigOutputTransaction;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;

import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.util.GTUtility;

/** Native transaction interfaces changed between beta-1, beta-2 and beta-3. */
public final class NativeMEOutputTransactions {

    private NativeMEOutputTransactions() {}

    public static Object wrap(Object owner, BigOutputTransaction exact, boolean fluid) {
        List<Class<?>> interfaces = new ArrayList<>();
        interfaces.add(fluid ? BigFluidOutputTransaction.class : BigItemOutputTransaction.class);
        String name = fluid ? "IOutputHatchTransaction" : "IOutputBusTransaction";
        addInterface(interfaces, "gregtech.api.interfaces." + name, true);
        addInterface(interfaces, "gregtech.api.interfaces.IOutputTransaction$IRecipeCheckAware", false);
        addInterface(interfaces, "gregtech.api.interfaces.IOutputTransaction$IProtectOutputAware", false);
        addInterface(interfaces, "gregtech.api.interfaces." + name + "$IRecipeCheckAware", false);
        return Proxy.newProxyInstance(
            NativeMEOutputTransactions.class.getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            (proxy, method, args) -> {
                String operation = method.getName();
                if (operation.equals("equals")) return proxy == args[0];
                if (operation.equals("hashCode")) return System.identityHashCode(proxy);
                if (operation.equals("toString")) return "Apeiron exact ME output transaction";
                if (operation.equals("getBus") || operation.equals("getHatch")) return owner;
                if (operation.equals("isFiltered")) return fluid ? ((MTEBoundlessMEOutputHatch) owner).isFiltered()
                    : ((MTEBoundlessMEOutputBus) owner).isFiltered();
                if (operation.startsWith("isFilteredTo"))
                    return fluid ? ((MTEBoundlessMEOutputHatch) owner).isFilteredToFluid((GTUtility.FluidId) args[0])
                        : ((MTEBoundlessMEOutputBus) owner).isFilteredToItem((GTUtility.ItemId) args[0]);
                if (operation.equals("hasAvailableSpace")) return true;
                if (operation.equals("needsTotalParallelData")) return false;
                if (operation.startsWith("complete") || operation.equals("setRecipeCheck")
                    || operation.equals("setProtectOutput")) return null;
                if (operation.equals("commit")) {
                    exact.commit();
                    return null;
                }
                if (operation.equals("storePartialBig")) {
                    return fluid
                        ? ((BigFluidOutputTransaction) exact).storePartialBig(
                            (appeng.api.storage.data.IAEFluidStack) args[0],
                            (BigInteger) args[1],
                            (BigInteger) args[2])
                        : ((BigItemOutputTransaction) exact).storePartialBig(
                            (appeng.api.storage.data.IAEItemStack) args[0],
                            (BigInteger) args[1],
                            (BigInteger) args[2]);
                }
                if (operation.equals("storePartial")) {
                    IAEStack<?> stack = fluid ? AEFluidStack.create((FluidStack) args[1])
                        : AEItemStack.create((ItemStack) args[1]);
                    BigInteger before = BigAEStackValues.get(stack);
                    BigInteger total = args.length == 4 ? BigInteger.valueOf((Long) args[2]) : BigInteger.ONE;
                    BigInteger per = args.length == 4 ? BigInteger.valueOf((Long) args[3]) : BigInteger.ONE;
                    if (fluid) ((BigFluidOutputTransaction) exact)
                        .storePartialBig((appeng.api.storage.data.IAEFluidStack) stack, total, per);
                    else((BigItemOutputTransaction) exact)
                        .storePartialBig((appeng.api.storage.data.IAEItemStack) stack, total, per);
                    int inserted = before.subtract(BigAEStackValues.get(stack))
                        .intValueExact();
                    if (fluid) ((FluidStack) args[1]).amount -= inserted;
                    else((ItemStack) args[1]).stackSize -= inserted;
                    return inserted > 0;
                }
                throw new UnsupportedOperationException("Unknown GregTech transaction method: " + method);
            });
    }

    private static void addInterface(List<Class<?>> types, String name, boolean required) {
        try {
            types.add(Class.forName(name, false, NativeMEOutputTransactions.class.getClassLoader()));
        } catch (ClassNotFoundException missing) {
            if (required) throw new IllegalStateException("Missing GregTech output API " + name, missing);
        }
    }
}
