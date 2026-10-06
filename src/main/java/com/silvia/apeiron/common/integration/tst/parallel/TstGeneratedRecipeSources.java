package com.silvia.apeiron.common.integration.tst.parallel;

import java.math.BigInteger;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.Nxer.TwistSpaceTechnology.common.api.random.XSTR;
import com.Nxer.TwistSpaceTechnology.common.machine.MiscHelper;
import com.Nxer.TwistSpaceTechnology.common.machine.TST_NetherInterface;
import com.Nxer.TwistSpaceTechnology.common.material.MaterialPool;
import com.Nxer.TwistSpaceTechnology.common.recipeMap.GTCMRecipe;
import com.Nxer.TwistSpaceTechnology.config.Config;
import com.silvia.apeiron.api.machine.parallel.GeneratedRecipeSource;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.GeneratedRecipes;
import com.silvia.apeiron.mixin.tst.energy.WaterPurifierRecipeAccessor;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;

/** Native fixed-cycle data and random pools, without controller-specific execution injections. */
public final class TstGeneratedRecipeSources {

    private TstGeneratedRecipeSources() {}

    public static void register() {
        GeneratedRecipes.register(GTCMRecipe.NetherInterfaceVisualRecipeMap, new Nether());
        GeneratedRecipes.register(GTCMRecipe.SuperWaterPurifierVisualRecipeMap, new Purifier());
    }

    private static GTRecipe recipe(FluidStack input, int duration, int eut) {
        return GTRecipeBuilder.builder()
            .fluidInputs(input)
            .duration(duration)
            .eut(eut)
            .build()
            .get();
    }

    private static final class Nether implements GeneratedRecipeSource {

        @Override
        public GTRecipe findRecipe(List<IAEStack<?>> inputs) {
            if (MiscHelper.distilledWater == null) return null;
            FluidStack water = MiscHelper.distilledWater.copy();
            water.amount = Config.BasicDistilledWaterCost_NetherInterface;
            return recipe(water, Config.CycleTime_NetherInterface, Config.BasicEnergyCost_NetherInterface);
        }

        @Override
        public BigInteger getFixedEUt(GTRecipe recipe) {
            return BigInteger.valueOf(recipe.mEUt)
                .multiply(BigInteger.valueOf(2));
        }

        @Override
        public BigMachineOutputQueue rollOutputs(GTRecipe recipe) {
            BigMachineOutputQueue result = new BigMachineOutputQueue();
            for (int i = 0; i < Config.GenerateStackEveryProcessing_NetherInterface; i++) {
                ItemStack output = TST_NetherInterface.ItemRandomGetter.getOne();
                result.addItem(output, BigInteger.valueOf(output.stackSize));
            }
            if (XSTR.XSTR_INSTANCE.nextInt(10000) < Config.OutputHellishMetalPercent_NetherInterface * 100)
                result.addFluid(Materials.HellishMetal.getMolten(1), BigInteger.valueOf(288));
            result.addFluid(Materials.PoorNetherWaste.getFluid(1), BigInteger.valueOf(recipe.mFluidInputs[0].amount));
            return result;
        }
    }

    private static final class Purifier implements GeneratedRecipeSource {

        @Override
        public GTRecipe findRecipe(List<IAEStack<?>> inputs) {
            FluidStack uum = Materials.UUMatter.getFluid(1000);
            FluidStack concentrated = MaterialPool.ConcentratedUUMatter.getFluidOrGas(1);
            for (IAEStack<?> stock : inputs) {
                if (!(stock instanceof IAEFluidStack)) continue;
                FluidStack fluid = ((IAEFluidStack) stock).getFluidStack();
                if (uum.isFluidEqual(fluid)) return recipe(uum, 1200, (int) TierEU.RECIPE_UMV);
                if (concentrated.isFluidEqual(fluid)) return recipe(concentrated, 1200, (int) TierEU.RECIPE_UMV);
            }
            return null;
        }

        @Override
        public BigMachineOutputQueue rollOutputs(GTRecipe recipe) {
            BigMachineOutputQueue result = new BigMachineOutputQueue();
            int rolls = recipe.mFluidInputs[0].amount == 1 ? 6 : 3;
            for (int i = 0; i < rolls; i++) {
                FluidStack output = WaterPurifierRecipeAccessor.apeiron$randomOutputs()
                    .getOne();
                result.addFluid(output, BigInteger.valueOf(output.amount));
            }
            return result;
        }
    }
}
