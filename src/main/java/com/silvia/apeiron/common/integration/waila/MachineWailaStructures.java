package com.silvia.apeiron.common.integration.waila;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** Small structured properties used by stock HUD scripts; each traversal stops at the preview limit. */
public final class MachineWailaStructures {

    private static final int PREVIEW_LIMIT = 64;

    private MachineWailaStructures() {}

    public static void write(Object machine, NBTTagCompound tag) {
        Object fluid = value(machine, "mFluid");
        if (fluid instanceof FluidStack) {
            tag.setTag("mFluid", fluid((FluidStack) fluid));
            tag.setString(
                "mFluidName",
                ((FluidStack) fluid).getFluid()
                    .getName());
        }
        Object steam = value(machine, "mSteam");
        if (steam instanceof FluidStack) tag.setTag("mSteam", fluid((FluidStack) steam));
        Object locked = value(machine, "mLockedFluid");
        if (locked instanceof FluidStack) tag.setString(
            "mLockedFluidName",
            ((FluidStack) locked).getFluid()
                .getName());

        Object inserted = value(machine, "insertedStuffThisCycle");
        if (inserted instanceof Map<?, ?>) {
            NBTTagCompound fluids = new NBTTagCompound();
            int count = 0;
            for (Object entry : ((Map<?, ?>) inserted).values()) {
                if (++count > PREVIEW_LIMIT) break;
                if (entry instanceof FluidStack) {
                    FluidStack stack = (FluidStack) entry;
                    fluids.setTag(
                        stack.getFluid()
                            .getName(),
                        fluid(stack));
                }
            }
            tag.setTag("insertedFluidMap", fluids);
        }
        Object catalysts = value(machine, "insertedCatalysts");
        if (catalysts instanceof Collection<?>) {
            NBTTagCompound items = new NBTTagCompound();
            int index = 0;
            for (Object item : (Collection<?>) catalysts) {
                if (index >= PREVIEW_LIMIT) break;
                if (item instanceof ItemStack) items.setTag(Integer.toString(index++), item((ItemStack) item));
            }
            tag.setTag("insertedItems", items);
        }
        Object signal = value(machine, "controlSignal");
        Object signalValue = signal == null ? null : value(signal, "signal");
        if (signalValue instanceof Number) tag.setByte("controlSignal", ((Number) signalValue).byteValue());

        Object stored = value(machine, "validFluidMap");
        if (stored instanceof Map<?, ?>) {
            int count = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) stored).entrySet()) {
                if (++count > PREVIEW_LIMIT) break;
                if (entry.getKey() instanceof Fluid && entry.getValue() instanceof Number) tag.setLong(
                    "stored." + ((Fluid) entry.getKey()).getUnlocalizedName(),
                    ((Number) entry.getValue()).longValue());
            }
        }

        // Modern ME hatches delegate their configuration serializer to a provider object.
        Object provider = value(machine, "provider");
        if (provider != null && provider.getClass()
            .getName()
            .startsWith("gregtech.common.tileentities.machines.outputme.")) MachineWailaAliases.write(provider, tag);
        if (Boolean.TRUE.equals(value(machine, "masterSet"))) {
            NBTTagCompound master = new NBTTagCompound();
            coordinate(machine, master, "masterX", "x");
            coordinate(machine, master, "masterY", "y");
            coordinate(machine, master, "masterZ", "z");
            tag.setTag("master", master);
        }
    }

    public static NBTTagCompound item(ItemStack stack) {
        // Identifying metadata and charge suffice for stock scripts; patterns and storage contents stay local.
        NBTTagCompound result = stack.writeToNBT(new NBTTagCompound());
        if (result.hasKey("tag")) {
            NBTTagCompound charge = new NBTTagCompound();
            NBTTagCompound original = result.getCompoundTag("tag");
            for (String key : new String[] { "GT.ItemCharge", "charge" }) if (original.hasKey(key, 99)) charge.setTag(
                key,
                original.getTag(key)
                    .copy());
            if (original.hasKey("display", 10)) {
                NBTTagCompound display = new NBTTagCompound();
                String name = original.getCompoundTag("display")
                    .getString("Name");
                if (!name.isEmpty() && name.length() <= 256) display.setString("Name", name);
                if (!display.hasNoTags()) charge.setTag("display", display);
            }
            if (charge.hasNoTags()) result.removeTag("tag");
            else result.setTag("tag", charge);
        }
        return result;
    }

    private static NBTTagCompound fluid(FluidStack stack) {
        NBTTagCompound result = new NBTTagCompound();
        result.setString(
            "FluidName",
            stack.getFluid()
                .getName());
        result.setInteger("Amount", stack.amount);
        return result;
    }

    private static void coordinate(Object machine, NBTTagCompound tag, String field, String key) {
        Object value = value(machine, field);
        if (value instanceof Number) tag.setInteger(key, ((Number) value).intValue());
    }

    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<Map<String, Field>>() {

        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> result = new java.util.HashMap<>();
            String[] names = { "mFluid", "mSteam", "mLockedFluid", "insertedStuffThisCycle", "insertedCatalysts",
                "controlSignal", "signal", "validFluidMap", "provider", "masterSet", "masterX", "masterY", "masterZ" };
            for (String name : names) {
                Field field = MachineWailaAliases.field(type, name);
                if (field != null) result.put(name, field);
            }
            return result;
        }
    };

    private static Object value(Object object, String name) {
        Field field = FIELDS.get(object.getClass())
            .get(name);
        if (field == null) return null;
        try {
            return field.get(object);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read structured HUD field: " + name, failure);
        }
    }
}
