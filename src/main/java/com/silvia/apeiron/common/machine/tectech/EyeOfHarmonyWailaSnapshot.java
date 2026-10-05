package com.silvia.apeiron.common.machine.tectech;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;

import com.silvia.apeiron.common.integration.waila.MachineWailaAliases;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;
import tectech.util.FluidStackLong;

/** The save-key aliases used by existing HUD scripts, without serializing the complete recipe outputs. */
public final class EyeOfHarmonyWailaSnapshot {

    public static final String DISPLAY_TAG = "ApeironEyeOfHarmony";
    public static final String PREFIX = "eyeOfHarmonyOutput";
    private static final Map<String, Field> FIELDS = fields();

    private EyeOfHarmonyWailaSnapshot() {}

    private static Map<String, Field> fields() {
        Map<String, Field> result = new HashMap<>();
        for (Field field : MTEEyeOfHarmony.class.getDeclaredFields()) {
            field.setAccessible(true);
            result.put(field.getName(), field);
        }
        return result;
    }

    private static Object read(MTEEyeOfHarmony eye, String name) {
        try {
            Field field = FIELDS.get(name);
            if (field == null) throw new IllegalStateException("Missing Eye of Harmony HUD field: " + name);
            return field.get(eye);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Unable to read Eye of Harmony HUD field: " + name, failure);
        }
    }

    @SuppressWarnings("unchecked")
    public static void write(MTEMultiBlockBase machine, NBTTagCompound tag) {
        if (!(machine instanceof MTEEyeOfHarmony)) return;
        MTEEyeOfHarmony eye = (MTEEyeOfHarmony) machine;
        MachineWailaAliases.write(eye, tag);

        Map<Fluid, Long> stored = (Map<Fluid, Long>) read(eye, "validFluidMap");
        Fluid[] inputs = { Materials.Hydrogen.mGas, Materials.Helium.mGas, Materials.RawStarMatter.mFluid };
        for (Fluid fluid : inputs) tag.setLong("stored." + fluid.getUnlocalizedName(), stored.getOrDefault(fluid, 0L));

        // These scripts only consume the last two special fluid outputs; the thousands of item outputs stay local.
        List<FluidStackLong> outputs = (List<FluidStackLong>) read(eye, "outputFluids");
        NBTTagCompound fluids = new NBTTagCompound();
        fluids.setLong(PREFIX + "numberOfFluids", outputs.size());
        for (int i = Math.max(0, outputs.size() - 2); i < outputs.size(); i++)
            fluids.setLong(i + "fluidAmount", outputs.get(i).amount);
        tag.setTag(PREFIX + "fluidOutput", fluids);
        writeFluid(tag, "recipeStarMatter", (FluidStackLong) read(eye, "starMatter"));
        writeFluid(tag, "recipeStellarPlasma", (FluidStackLong) read(eye, "stellarPlasma"));

        NBTTagCompound display = new NBTTagCompound();
        NBTTagList requirements = new NBTTagList();
        // A failed startup clears currentRecipe. Look up the selected planet even while waiting for fluids.
        if (!tag.getBoolean(PREFIX + "recipeRunning")) {
            boolean enhanced = eye.mInputHatches.stream()
                .anyMatch(hatch -> hatch instanceof MTEEyeOfHarmonyEnhancementModule);
            Map<Fluid, BigInteger> targets = EyeOfHarmonyFluidRequirements
                .selected(eye, (Long) read(eye, "astralArrayAmount"), enhanced);
            targets.forEach((fluid, required) -> requirement(requirements, fluid, stored, required));
        }
        display.setTag("requirements", requirements);
        tag.setTag(DISPLAY_TAG, display);
    }

    private static void writeFluid(NBTTagCompound tag, String key, FluidStackLong fluid) {
        NBTTagCompound preview = new NBTTagCompound();
        if (fluid != null && fluid.fluidStack != null) {
            // Names suffice for the legacy script; item/fluid custom NBT is not part of the HUD.
            preview.setString(
                "FluidName",
                fluid.fluidStack.getFluid()
                    .getName());
            preview.setInteger("Amount", fluid.fluidStack.amount);
        }
        tag.setTag(PREFIX + key, preview);
    }

    private static void requirement(NBTTagList rows, Fluid fluid, Map<Fluid, Long> stored, BigInteger required) {
        NBTTagCompound row = new NBTTagCompound();
        row.setString("fluid", fluid.getName());
        row.setString("stored", Long.toString(stored.getOrDefault(fluid, 0L)));
        row.setString("required", required.toString());
        rows.appendTag(row);
    }
}
