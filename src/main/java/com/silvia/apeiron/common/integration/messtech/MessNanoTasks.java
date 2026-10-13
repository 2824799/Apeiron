package com.silvia.apeiron.common.integration.messtech;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.output.BigRecipeOutputCapacity;
import com.silvia.apeiron.common.machine.parallel.BigRecipeInputs;
import com.silvia.apeiron.common.machine.parallel.BigRecipeParallelHelper;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Independent native work threads keep separate paid progress and exact output ledgers. */
public final class MessNanoTasks {

    private final Map<Object, WirelessRecipeState> tasks = new IdentityHashMap<>();
    private final Map<Object, BigInteger> boards = new IdentityHashMap<>();
    private long schedulerTicks;

    public CheckRecipeResult start(MTEMultiBlockBase machine, Object thread, GTRecipe recipe,
        ArrayList<ItemStack> items, ArrayList<FluidStack> fluids) {
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        if (hatch == null) return null;
        if (!BigRecipeParallelHelper.supportsInputs(recipe)) return CheckRecipeResultRegistry.NO_RECIPE;
        WirelessRecipeState settings = ((BigWirelessController) machine).getWirelessRecipeState();
        double eut = recipe.mEUt * number(machine, "getEuModifier")
            * number(machine, "getThreadEuModifier", thread, recipe, fluids);
        double duration = recipe.mDuration * number(machine, "getSpeedBonus")
            * number(machine, "getThreadSpeedModifier", thread, recipe, fluids);
        long voltage = InfiniteEnergyHatches.processingVoltage(machine);
        if (!hatch.isUltimate()) {
            if (recipe.mEUt > voltage) return CheckRecipeResultRegistry.insufficientVoltage(recipe.mEUt);
            int overclocks = Math.max(0, GTUtility.getTier(voltage) - GTUtility.getTier(recipe.mEUt));
            for (int i = 0; i < overclocks && duration / 2 >= 100 && eut * 2 <= voltage; i++) {
                eut *= 2;
                duration /= 2;
            }
        }
        int nativeTicks = (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.ceil(duration)));
        int ticks = hatch.isUltimate() ? settings.getTargetDuration() : nativeTicks;
        BigInteger per = ceil(eut).max(BigInteger.ONE)
            .multiply(BigInteger.valueOf(nativeTicks));
        BigInteger reserved = tasks.values()
            .stream()
            .map(WirelessRecipeState::remainingEnergy)
            .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger cap = settings.getLimit()
            .applyTo(
                hatch.getAvailableEUBig()
                    .subtract(reserved)
                    .max(BigInteger.ZERO)
                    .divide(per));
        if (cap.signum() == 0) return CheckRecipeResultRegistry.NO_RECIPE;
        int circuit = ((Number) MessTechAccess.call(thread, "getCircuitNumber")).intValue();
        List<IAEFluidStack> cooling = new ArrayList<>();
        if (circuit == 9) {
            Object data = MessTechAccess.call(machine, "createOpticalTaskData", fluids);
            if (data == null) return CheckRecipeResultRegistry.NO_RECIPE;
            float discount = ((Number) MessTechAccess.get(data, "waterDiscount")).floatValue();
            for (FluidStack water : (List<FluidStack>) MessTechAccess.get(data, "waters")) {
                // The native scheduler drains once per 20 work ticks, independent of parallels.
                BigInteger amount = BigInteger.valueOf((int) (discount * water.amount))
                    .multiply(BigInteger.valueOf(((long) nativeTicks + 19) / 20));
                if (amount.signum() > 0) cooling.add(BigAEStackValues.copyWithSize(AEFluidStack.create(water), amount));
            }
        }
        BigRecipeInputs inputs;
        try {
            inputs = new BigRecipeInputs(
                machine,
                null,
                recipe,
                items.toArray(new ItemStack[0]),
                fluids.toArray(new FluidStack[0]),
                cooling);
        } catch (IllegalArgumentException missing) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        cap = inputs.allocation()
            .maximum(com.silvia.apeiron.api.machine.parallel.ParallelLimit.bounded(cap));
        BigMachineOutputQueue reservedProducts = products();
        cap = MessModuleRecipes.fit(machine, cap, count -> {
            BigMachineOutputQueue all = MessModuleRecipes.copy(reservedProducts);
            outputs(recipe, count).moveTo(all);
            return all;
        });
        if (cap.signum() == 0) return CheckRecipeResultRegistry.NO_RECIPE;
        BigMachineOutputQueue output = outputs(recipe, cap);
        inputs.consume(cap);
        Object task = MessTechAccess.call(
            thread,
            "start",
            ticks,
            MessTechAccess.call(thread, "getRecipeMap"),
            new ItemStack[0],
            new FluidStack[0]);
        MessTechAccess.call(task, "setEUt", 0L);
        MessTechAccess.call(
            task,
            "setParallel",
            cap.min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue());
        WirelessRecipeState state = new WirelessRecipeState();
        state.startExact(cap, per.multiply(cap), ticks, output, hatch.isUltimate());
        tasks.put(task, state);
        if (circuit == 4) {
            Object key;
            try {
                key = Class.forName("gregtech.api.recipe.metadata.BoardProcessingModuleFluidKey")
                    .getField("INSTANCE")
                    .get(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
            MessTechAccess.call(task, "setModuleData", MessTechAccess.call(recipe, "getMetadataOrDefault", key, 1));
            boards.put(
                task,
                recipe.mOutputs.length == 0 || recipe.mOutputs[0] == null ? BigInteger.ZERO
                    : cap.multiply(BigInteger.valueOf(recipe.mOutputs[0].stackSize)));
        }
        machine.markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    public void schedulerTick() {
        schedulerTicks++;
    }

    /** Return true when this callback is owned by the exact scheduler. */
    public boolean tick(MTEMultiBlockBase machine, Object thread, Object base) {
        List<Object> list = (List<Object>) MessTechAccess.call(thread, "getTasks");
        if (list.stream()
            .anyMatch(task -> !tasks.containsKey(task))) return schedulerTicks % 20 != 0;
        if (list.isEmpty()) return false; // Native lookup still runs all tier/tank/boost gates.
        MTEInfiniteEnergyHatch hatch = InfiniteEnergyHatches.find(machine);
        for (java.util.Iterator<Object> iterator = list.iterator(); iterator.hasNext();) {
            Object task = iterator.next();
            WirelessRecipeState state = tasks.get(task);
            int progress = ((Number) MessTechAccess.call(task, "getProgressTime")).intValue();
            if (progress < state.getDuration()) {
                if (hatch == null || !hatch.consumeEUBig(state.nextDebit(10000))) continue;
                state.paidTick();
                MessTechAccess.set(task, "progressTime", ++progress);
            }
            if (progress < state.getDuration()) continue;
            if (!BigRecipeOutputCapacity.check(machine, state.getOutputsUnsorted())
                .wasSuccessful()) {
                MessTechAccess.call(task, "setAwaitingOutput", true);
                continue;
            }
            finishBoard(machine, thread, task);
            state.complete();
            state.pending()
                .moveTo(
                    ((BigWirelessController) machine).getWirelessRecipeState()
                        .pending());
            tasks.remove(task);
            boards.remove(task);
            iterator.remove();
            machine.markDirty();
        }
        if (list.isEmpty()) MessTechAccess.call(machine, "checkAndStartThread", base, thread);
        return true;
    }

    private void finishBoard(MTEMultiBlockBase machine, Object thread, Object task) {
        BigInteger count = boards.get(task);
        if (count == null || count.signum() == 0) return;
        Object tank = MessTechAccess.call(machine, "getBoardTank", thread);
        if (tank == null) return;
        Object chamber = MessTechAccess.call(tank, "getTank", MessTechAccess.call(task, "getModuleData"));
        if (chamber == null) return;
        BigInteger total = count
            .add(BigInteger.valueOf(((Number) MessTechAccess.get(chamber, "processedItems")).intValue()));
        BigInteger[] rounds = total.divideAndRemainder(BigInteger.valueOf(1000));
        MessTechAccess.set(chamber, "processedItems", rounds[1].intValue());
        int fluid = ((Number) MessTechAccess.get(chamber, "fluidAmount")).intValue();
        int impurity = ((Number) MessTechAccess.get(chamber, "impurityAmount")).intValue();
        if (fluid <= 0 || MessTechAccess.get(chamber, "storedFluid") == null) return;
        int increase = (int) (100 / Math.pow(fluid / 1_000_000d, 1.5));
        impurity += rounds[0].multiply(BigInteger.valueOf(increase))
            .min(BigInteger.valueOf(fluid - impurity))
            .intValue();
        MessTechAccess.set(chamber, "impurityAmount", impurity);
        FluidStack waste = (FluidStack) MessTechAccess.get(chamber, "impurityFluid");
        if (waste != null) waste.amount = impurity;
    }

    public void clear() {
        tasks.clear();
        boards.clear();
    }

    public boolean isActive() {
        return !tasks.isEmpty();
    }

    public BigInteger parallels() {
        return sum(WirelessRecipeState::getParallelsBig);
    }

    public BigInteger eut() {
        return sum(
            s -> s.remainingEnergy()
                .signum() == 0 ? BigInteger.ZERO : s.nextDebit(10000));
    }

    public BigInteger energy() {
        return sum(WirelessRecipeState::getTotalEUBig);
    }

    private BigInteger sum(java.util.function.Function<WirelessRecipeState, BigInteger> field) {
        return tasks.values()
            .stream()
            .map(field)
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public BigMachineOutputQueue products() {
        BigMachineOutputQueue result = new BigMachineOutputQueue();
        for (WirelessRecipeState state : tasks.values()) {
            BigMachineOutputQueue one = new BigMachineOutputQueue();
            for (appeng.api.storage.data.IAEStack<?> value : state.getOutputsUnsorted()) {
                if (value instanceof appeng.api.storage.data.IAEItemStack) one.addItem(
                    ((appeng.api.storage.data.IAEItemStack) value).getItemStack(),
                    BigAEStackValues.get(value));
                else one.addFluid(((IAEFluidStack) value).getFluidStack(), BigAEStackValues.get(value));
            }
            one.moveTo(result);
        }
        return result;
    }

    public WirelessRecipeState threadState(Object thread) {
        List<?> list = (List<?>) MessTechAccess.call(thread, "getTasks");
        return list.isEmpty() ? null : tasks.get(list.get(0));
    }

    public void writeDisplay(MTEMultiBlockBase machine, NBTTagCompound tag) {
        WirelessRecipeState display = new WirelessRecipeState();
        display.setParallelSettingBig(
            ((BigWirelessController) machine).getParallelLimitBig()
                .getBound()
                .orElse(BigInteger.ZERO));
        display.setVoltageSetting(InfiniteEnergyHatches.processingVoltage(machine));
        display.startExact(parallels(), energy(), 1, products(), InfiniteEnergyHatches.isUltimate(machine));
        com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay.write(tag, display, 10000);
        tag.setString("ApeironWirelessEUt", com.silvia.apeiron.math.BigNumberFormatter.formatCompact(eut()));
        tag.setString("ApeironActualWirelessEUt", eut().toString());
        tag.setString(
            "ApeironActualWirelessEUtDisplay",
            com.silvia.apeiron.math.BigNumberFormatter.formatCompact(eut()));
    }

    public void save(MTEMultiBlockBase machine, NBTTagCompound tag) {
        NBTTagList entries = new NBTTagList();
        for (Object thread : (Collection<?>) MessTechAccess.call(machine, "getThreads")) {
            List<?> list = (List<?>) MessTechAccess.call(thread, "getTasks");
            for (int i = 0; i < list.size(); i++) if (tasks.containsKey(list.get(i))) {
                Object task = list.get(i);
                NBTTagCompound entry = tasks.get(task)
                    .save();
                entry.setString("thread", (String) MessTechAccess.call(thread, "getName"));
                entry.setInteger("index", i);
                if (boards.containsKey(task)) {
                    entry.setString(
                        "boards",
                        boards.get(task)
                            .toString());
                    entry.setInteger("boardType", ((Number) MessTechAccess.call(task, "getModuleData")).intValue());
                }
                entries.appendTag(entry);
            }
        }
        tag.setTag("ApeironMessTasks", entries);
    }

    public void load(MTEMultiBlockBase machine, NBTTagCompound tag) {
        clear();
        NBTTagList entries = tag.getTagList("ApeironMessTasks", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            Object thread = MessTechAccess.call(machine, "getThread", entry.getString("thread"));
            if (thread == null) continue;
            List<?> list = (List<?>) MessTechAccess.call(thread, "getTasks");
            int index = entry.getInteger("index");
            if (index < 0 || index >= list.size()) continue;
            WirelessRecipeState state = new WirelessRecipeState();
            state.load(entry);
            if (!state.isRunning()) continue;
            Object task = list.get(index);
            tasks.put(task, state);
            if (entry.hasKey("boards")) {
                boards.put(task, new BigInteger(entry.getString("boards")));
                MessTechAccess.call(task, "setModuleData", entry.getInteger("boardType"));
            }
        }
    }

    static BigMachineOutputQueue outputs(GTRecipe recipe, BigInteger parallels) {
        BigMachineOutputQueue result = new BigMachineOutputQueue();
        for (ItemStack item : recipe.mOutputs)
            if (item != null) result.addItem(item, parallels.multiply(BigInteger.valueOf(item.stackSize)));
        for (FluidStack fluid : recipe.mFluidOutputs)
            if (fluid != null) result.addFluid(fluid, parallels.multiply(BigInteger.valueOf(fluid.amount)));
        return result;
    }

    private static double number(Object target, String method, Object... args) {
        return ((Number) MessTechAccess.call(target, method, args)).doubleValue();
    }

    private static BigInteger ceil(double value) {
        return BigDecimal.valueOf(value)
            .setScale(0, RoundingMode.CEILING)
            .toBigIntegerExact();
    }
}
