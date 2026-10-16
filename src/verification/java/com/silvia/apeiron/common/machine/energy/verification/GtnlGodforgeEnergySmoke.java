package com.silvia.apeiron.common.machine.energy.verification;

import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.addItem;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.attach;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.attempt;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.configure;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.finish;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.map;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.output;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.process;
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.state;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.energy.MachineRecipeDisplay;
import com.silvia.apeiron.common.machine.energy.WirelessControllerEnergy;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.common.misc.WirelessNetworkManager;
import tectech.thing.metaTileEntity.multi.godforge.MTEBaseModule;

/** Optional GTNL types are resolved only inside the shared fixture when that mod is installed. */
public final class GtnlGodforgeEnergySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(30)
        .add(BigInteger.valueOf(13));
    private static final BigInteger BALANCE = BigInteger.TEN.pow(70);

    private GtnlGodforgeEnergySmoke() {}

    public static void verify() throws ReflectiveOperationException {
        verifyGtnlModules();
    }

    private static final class GtnlFusion
        extends com.science.gtnl.common.machine.multiblock.FOGAlloyBlastSmelterModule {

        private final RecipeMap<?> recipes = map();

        private GtnlFusion() {
            super("apeiron.verify.gtnl.fusion");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    private static final class GtnlAlloy extends com.science.gtnl.common.machine.multiblock.FOGAlloySmelterModule {

        private final RecipeMap<?> recipes = map();

        private GtnlAlloy() {
            super("apeiron.verify.gtnl.alloy");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    private static final class GtnlExtractor extends com.science.gtnl.common.machine.multiblock.FOGExtractorModule {

        private final RecipeMap<?> recipes = map();

        private GtnlExtractor() {
            super("apeiron.verify.gtnl.extractor");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    private static final class GtnlCatalyst
        extends com.science.gtnl.common.machine.multiblock.FOGSolarMuonCatalystModule {

        private final RecipeMap<?> recipes = map();

        private GtnlCatalyst() {
            super("apeiron.verify.gtnl.catalyst");
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipes;
        }
    }

    private static MTEBaseModule gtnlModule(int kind, UUID owner, boolean upgrades)
        throws ReflectiveOperationException {
        return gtnlModule(kind, owner, upgrades, 3, 1000);
    }

    private static MTEBaseModule gtnlModule(int kind, UUID owner, boolean upgrades, int items, int fluids)
        throws ReflectiveOperationException {
        MTEBaseModule module = kind == 0 ? new GtnlFusion()
            : kind == 1 ? new GtnlAlloy() : kind == 2 ? new GtnlExtractor() : new GtnlCatalyst();
        configure(module, owner, upgrades);
        module.setProcessingVoltage(8);
        module.getRecipeMap()
            .addRecipe(
                GTRecipeBuilder.builder()
                    .itemInputs(new ItemStack(Items.diamond))
                    .itemOutputs(new ItemStack(Items.emerald, items))
                    .fluidOutputs(new FluidStack(FluidRegistry.WATER, fluids))
                    .duration(300)
                    .eut(6)
                    .build()
                    .get());
        return module;
    }

    private static void verifyGtnlModules() throws ReflectiveOperationException {
        for (int kind = 0; kind < 4; kind++) verifyGtnlNativeOutput(kind);
        for (int kind = 0; kind < 4; kind++) for (boolean ultimate : new boolean[] { false, true })
            for (boolean upgrades : new boolean[] { false, true }) {
                UUID owner = UUID.randomUUID();
                MTEBaseModule baseline = gtnlModule(kind, owner, upgrades);
                baseline.setPowerPanelMaxParallel(1);
                MTEInfinitePatternInputAssembly nativeInput = attach(baseline, owner, null);
                addItem(nativeInput, BigInteger.ONE);
                WirelessNetworkManager.setUserEU(owner, BALANCE);
                process(baseline, nativeInput);
                BigInteger cost = baseline.getPowerTally();
                int nativeDuration = baseline.mMaxProgresstime;
                check(cost.signum() > 0, "GTNL native cost is empty");
                MTEBaseModule module = gtnlModule(kind, owner, upgrades);
                MTEInfinitePatternInputAssembly input = attach(module, owner, ultimate);
                WirelessRecipeState state = state(module);
                state.setVoltageSetting(8);
                state.setParallelSettingBig(HUGE);
                state.setTargetDuration(7);
                addItem(input, HUGE);
                BigInteger balance = cost.multiply(HUGE);
                WirelessNetworkManager.setUserEU(owner, balance);
                process(module, input);
                check(
                    state.isRunning() && state.getParallelsBig()
                        .equals(HUGE),
                    "GTNL retained int parallel cap");
                check(module.mMaxProgresstime == (ultimate ? 7 : nativeDuration), "GTNL duration changed");
                check(
                    state.getTotalEUBig()
                        .equals(balance),
                    "GTNL lost native speed/energy discount");
                check(
                    WirelessNetworkManager.getUserEU(owner)
                        .equals(balance),
                    "GTNL precharged energy");
                check(
                    module.getPowerTally()
                        .equals(balance) && module.getRecipeTally() == Long.MAX_VALUE,
                    "GTNL milestone tally changed");
                check(
                    input.getBuffers()
                        .get(0)
                        .isEmpty(),
                    "GTNL exact input deduction changed");
                if (kind == 0 && ultimate && upgrades) {
                    NBTTagCompound saved = new NBTTagCompound();
                    module.saveNBTData(saved);
                    MTEBaseModule restored = gtnlModule(kind, owner, upgrades);
                    MTEInfinitePatternInputAssembly restoredInput = attach(restored, owner, ultimate);
                    restored.loadNBTData(saved);
                    module = restored;
                    input = restoredInput;
                    state = state(restored);
                    check(
                        state.isRunning() && state.getTotalEUBig()
                            .equals(balance)
                            && state.getParallelsBig()
                                .equals(HUGE)
                            && state.getDuration() == 7,
                        "GTNL running reload lost exact state");
                    WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
                    check(!module.onRunningTick(null), "GTNL unfunded running tick advanced");
                    WirelessNetworkManager.setUserEU(owner, balance);
                }
                finish(module, owner);
                check(
                    output(module).getProvider()
                        .getCachedAmountBig()
                        .equals(HUGE.multiply(BigInteger.valueOf(3))),
                    "GTNL item output truncated");
                check(
                    output(module).getFluidProvider()
                        .getCachedAmountBig()
                        .equals(HUGE.multiply(BigInteger.valueOf(1000))),
                    "GTNL fluid output truncated");
                // A one-recipe balance must pass the exact cost check even if the configured maximum is enormous.
                addItem(input, BigInteger.valueOf(2));
                WirelessNetworkManager.setUserEU(owner, cost);
                process(module, input);
                check(
                    state.getParallelsBig()
                        .equals(BigInteger.ONE),
                    "GTNL ignored affordable parallel");
                finish(module, owner);
                check(
                    !attempt(module, input).wasSuccessful() && !input.getBuffers()
                        .get(0)
                        .isEmpty(),
                    "GTNL unfunded recipe consumed input");
            }
        Apeiron.LOG.info(
            "GTNL godforge energy verification passed: four native modules, both hatches, exact item/fluid output, main discounts, budget and tallies");
    }

    private static void verifyGtnlNativeOutput(int kind) throws ReflectiveOperationException {
        UUID owner = UUID.randomUUID();
        int perRecipe = Integer.MAX_VALUE - 5;
        MTEBaseModule module = gtnlModule(kind, owner, false, perRecipe, perRecipe);
        int parallel = 1_000_000_000;
        module.setCalculatedMaxParallel(parallel);
        module.setPowerPanelMaxParallel(parallel);
        MTEInfinitePatternInputAssembly input = attach(module, owner, null);
        addItem(input, BigInteger.valueOf(parallel));
        WirelessNetworkManager.setUserEU(owner, BALANCE);
        process(module, input);
        check(module.getRecipeTally() == parallel, "native GTNL parallel cap changed");
        verifyNativeCompletion(
            module,
            BigInteger.valueOf(perRecipe)
                .multiply(BigInteger.valueOf(parallel)),
            BigInteger.valueOf(perRecipe)
                .multiply(BigInteger.valueOf(parallel)),
            owner);
    }

    static void verifyNativeCompletion(MTEBaseModule module, BigInteger items, BigInteger fluids, UUID owner)
        throws ReflectiveOperationException {
        WirelessRecipeState state = state(module);
        check(InfiniteEnergyHatches.find(module) == null && state.usesNativeEnergy(), "native power mode changed");
        check(state.isRunning() && !state.hasPreparedRecipe(), "native outputs were not committed by controller");
        check(
            module.mOutputItems.length == 0 && module.mOutputFluids.length == 0,
            "native outputs expanded into int chunks");
        MTEInfiniteMEOutputAssembly port = output(module);
        check(
            port.getProvider()
                .getCachedAmountBig()
                .signum() == 0
                && port.getFluidProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
            "native products delivered before completion");
        BigInteger paidBalance = WirelessNetworkManager.getUserEU(owner);
        check(
            state.getTotalEUBig()
                .equals(module.getPowerTally()) && state.getDuration() == module.mMaxProgresstime,
            "native cost or duration changed");
        check(
            module.onRunningTick(null) && WirelessNetworkManager.getUserEU(owner)
                .equals(paidBalance),
            "prepaid module charged twice");
        BigMachineOutputQueue display = new BigMachineOutputQueue();
        display.load(MachineRecipeDisplay.snapshot(module));
        check(
            display.getItemAmountBig()
                .equals(items)
                && display.getFluidAmountBig()
                    .equals(fluids),
            "native preview lost exact products");

        NBTTagCompound saved = new NBTTagCompound();
        module.saveNBTData(saved);
        state.cancelRecipe();
        module.loadNBTData(saved);
        check(state.isRunning() && state.usesNativeEnergy(), "native in-flight reload changed power mode");
        module.mOutputBusses.clear();
        module.mOutputHatches.clear();
        // Match the real controller: native arrays are delivered first, then outputAfterRecipe.
        java.lang.reflect.Method complete = MTEMultiBlockBase.class.getDeclaredMethod("outputAfterRecipe");
        complete.setAccessible(true);
        complete.invoke(module);
        complete.invoke(module);
        check(
            !state.isRunning() && state.pending()
                .getItemAmountBig()
                .equals(items)
                && state.pending()
                    .getFluidAmountBig()
                    .equals(fluids),
            "disconnected completion lost or duplicated products");
        saved = new NBTTagCompound();
        module.saveNBTData(saved);
        module.loadNBTData(saved);
        check(module.addClassicToMachineList(port.getBaseMetaTileEntity(), 0), "output reconnection failed");
        WirelessControllerEnergy.complete(module);
        WirelessControllerEnergy.complete(module);
        check(
            port.getProvider()
                .getCachedAmountBig()
                .equals(items),
            "native GTNL item output truncated");
        check(
            port.getFluidProvider()
                .getCachedAmountBig()
                .equals(fluids)
                && state.pending()
                    .isEmpty(),
            "native GTNL fluid output truncated");

        BigMachineOutputQueue cancelled = new BigMachineOutputQueue();
        cancelled.addItem(new ItemStack(Items.emerald), BigInteger.ONE);
        state.startNativePowered(BigInteger.ONE, BigInteger.ZERO, 5, cancelled);
        module.stopMachine(gregtech.api.util.shutdown.ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        complete.invoke(module);
        check(
            !state.isRunning() && port.getProvider()
                .getCachedAmountBig()
                .equals(items),
            "stopped recipe produced unearned output");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("GTNL godforge energy: " + message);
    }
}
