package com.silvia.apeiron.common.machine.tectech;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import tectech.TecTech;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.thing.CustomItemList;

/** One startup requirement calculation shared by EOH input transfer and the idle HUD. */
public final class EyeOfHarmonyFluidRequirements {

    private EyeOfHarmonyFluidRequirements() {}

    public static Map<Fluid, BigInteger> selected(MTEMultiBlockBase machine, long astralArrays, boolean enhanced) {
        ItemStack planet = machine.getControllerSlot();
        EyeOfHarmonyRecipe recipe = planet == null || TecTech.eyeOfHarmonyRecipeStorage == null ? null
            : TecTech.eyeOfHarmonyRecipeStorage.recipeLookUp(planet);
        if (recipe == null) return Collections.emptyMap();
        return forRecipe(recipe, plannedParallels(machine, astralArrays, enhanced));
    }

    public static Map<Fluid, BigInteger> forRecipe(EyeOfHarmonyRecipe recipe, long parallels) {
        Map<Fluid, BigInteger> result = new LinkedHashMap<>();
        if (parallels > 1L) {
            // Match the native long/double comparison, including its floating-point evaluation order.
            double threshold = recipe.getHeliumRequirement() * (12.4 / 1_000_000f) * parallels;
            result.put(Materials.RawStarMatter.mFluid, new BigDecimal(Math.ceil(threshold)).toBigIntegerExact());
        } else {
            result.put(Materials.Hydrogen.mGas, BigInteger.valueOf(recipe.getHydrogenRequirement()));
            result.put(Materials.Helium.mGas, BigInteger.valueOf(recipe.getHeliumRequirement()));
        }
        return result;
    }

    public static long plannedParallels(MTEMultiBlockBase machine, long astralArrays, boolean enhanced) {
        long limit = enhanced ? Long.MAX_VALUE : 8637L;
        long arrays = Math.max(0L, Math.min(astralArrays, limit));
        // processRecipe absorbs arrays from the first bus before choosing its input fluid; preview that step.
        if (!machine.mInputBusses.isEmpty()) {
            MTEHatchInputBus bus = machine.mInputBusses.get(0);
            ItemStack arrayType = CustomItemList.astralArrayFabricator.get(1);
            for (int slot = 0; slot < bus.getSizeInventory() && arrays < limit; slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null && stack.stackSize > 0 && stack.isItemEqual(arrayType))
                    arrays += Math.min((long) stack.stackSize, limit - arrays);
            }
        }
        if (arrays == 0L) return 1L;
        long exponent = (long) Math.floor(Math.log(8.0D * arrays) / Math.log(1.7D));
        return (long) GTUtility.powInt(2, exponent);
    }
}
