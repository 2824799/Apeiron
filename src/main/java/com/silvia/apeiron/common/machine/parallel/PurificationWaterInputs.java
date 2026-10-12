package com.silvia.apeiron.common.machine.parallel;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputLogic;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;

/** Shares the normal exact inventory and debit transaction with native purification cycles. */
public final class PurificationWaterInputs {

    private static final ClassValue<Method[]> OPTIONAL_SPLIT_CYCLE = new ClassValue<Method[]>() {

        @Override
        protected Method[] computeValue(Class<?> type) {
            try {
                return new Method[] { type.getMethod("isGtnl$wirelessMode") };
            } catch (NoSuchMethodException absent) {
                return new Method[0];
            }
        }
    };

    private PurificationWaterInputs() {}

    public static boolean enabled(MTEMultiBlockBase machine) {
        for (Object hatch : machine.mInputHatches) if (hatch instanceof StockingInputHost) return true;
        return machine instanceof MTEPurificationUnitBase
            && PurificationParallels.source((MTEPurificationUnitBase<?>) machine) != null;
    }

    /** GTNL's wireless cycle already splits its long fluid outputs into multiple native stacks. */
    public static boolean usesSplitCycleOutputs(MTEMultiBlockBase machine) {
        Method[] methods = OPTIONAL_SPLIT_CYCLE.get(machine.getClass());
        try {
            return methods.length > 0 && (Boolean) methods[0].invoke(machine);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read purification cycle mode", error);
        }
    }

    private static <T> T withInventory(MTEMultiBlockBase machine, Function<BigRecipeInventory, T> action) {
        Set<StockingInputLogic> started = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object hatch : machine.mInputHatches) if (hatch instanceof StockingInputHost) {
            StockingInputLogic logic = ((StockingInputHost) hatch).getStockingInput();
            if (!logic.isProcessing() && started.add(logic)) logic.begin();
        }
        try {
            return action.apply(
                new BigRecipeInventory(
                    machine,
                    new ItemStack[0],
                    machine.getStoredFluids()
                        .toArray(new FluidStack[0])));
        } finally {
            StockingInputLogic.finishGroup(started);
        }
    }

    public static BigInteger parallel(MTEPurificationUnitBase<?> machine, FluidStack water) {
        BigInteger upper = PurificationParallels.affordable(machine);
        return withInventory(machine, inventory -> {
            BigInteger amount = BigInteger.ZERO;
            for (IAEStack<?> stack : inventory.getStacksBig())
                if (stack instanceof IAEFluidStack && ((IAEFluidStack) stack).getFluidStack()
                    .isFluidEqual(water)) {
                        if (BigAEStackValues.isInfinite(stack)) return PurificationParallels.limit(machine)
                            .applyTo(upper);
                        amount = amount.add(BigAEStackValues.get(stack));
                    }
            return amount.divide(BigInteger.valueOf(water.amount))
                .min(
                    PurificationParallels.limit(machine)
                        .applyTo(upper));
        });
    }

    public static IAEFluidStack request(FluidStack type, BigInteger amount) {
        FluidStack identity = type.copy();
        identity.amount = 1;
        return BigAEStackValues.copyWithSize(AEFluidStack.create(identity), amount);
    }

    public static List<IAEFluidStack> recipeInputs(GTRecipe recipe, int parallel) {
        return recipeInputs(recipe, BigInteger.valueOf(parallel));
    }

    public static List<IAEFluidStack> recipeInputs(GTRecipe recipe, BigInteger parallel) {
        List<IAEFluidStack> result = new ArrayList<>();
        for (int i = 0; i < recipe.mFluidInputs.length; i++) {
            FluidStack input = recipe.mFluidInputs[i];
            if (input != null && input.amount > 0) result.add(
                request(
                    input,
                    BigInteger.valueOf(input.amount)
                        .multiply(i == 0 ? parallel : BigInteger.ONE)));
        }
        return result;
    }

    public static IAEFluidStack boost(GTRecipe recipe, int parallel) {
        return boost(recipe, BigInteger.valueOf(parallel));
    }

    public static IAEFluidStack boost(GTRecipe recipe, BigInteger parallel) {
        // The native ratio is 0.1; calculate without a float/int quantity projection.
        BigInteger amount = BigDecimal.valueOf(recipe.mFluidOutputs[0].amount)
            .multiply(new BigDecimal(parallel))
            .movePointLeft(1)
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toBigIntegerExact();
        return request(recipe.mFluidOutputs[0], amount);
    }

    public static boolean consume(MTEMultiBlockBase machine, List<IAEFluidStack> required, boolean simulate) {
        try {
            return withInventory(machine, inventory -> inventory.consumeFluids(required, simulate));
        } catch (IllegalStateException unavailable) {
            // A network can change between simulation and commit. The shared debit rolls back.
            return false;
        }
    }
}
