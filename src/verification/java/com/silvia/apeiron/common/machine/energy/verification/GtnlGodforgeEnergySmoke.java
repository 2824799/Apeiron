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
import static com.silvia.apeiron.common.machine.energy.verification.GodforgeEnergySmoke.verifyNativeCompletion;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

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

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("GTNL godforge energy: " + message);
    }
}
