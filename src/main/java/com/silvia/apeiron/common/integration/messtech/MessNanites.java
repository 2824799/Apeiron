package com.silvia.apeiron.common.integration.messtech;

import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class MessNanites {

    private MessNanites() {}

    public static void save(MTEMultiBlockBase machine, NBTTagCompound tag) {
        tag.removeTag("ApeironMessNanites");
        if (!(machine instanceof MessNaniteController)
            || !((com.silvia.apeiron.api.machine.parallel.BigWirelessController) machine).getWirelessRecipeState()
                .isRunning())
            return;
        Object[] required = (Object[]) MessTechAccess.get(machine, "requiredNanites");
        if (required == null) return;
        int[] tiers = new int[required.length];
        for (int i = 0; i < tiers.length; i++) tiers[i] = ((Number) MessTechAccess.get(required[i], "tier")).intValue();
        tag.setIntArray("ApeironMessNanites", tiers);
    }

    public static void load(MTEMultiBlockBase machine, NBTTagCompound tag) {
        if (!(machine instanceof MessNaniteController) || !tag.hasKey("ApeironMessNanites")) return;
        try {
            Class<?> type = Class.forName("gregtech.api.enums.NaniteTier");
            int[] tiers = tag.getIntArray("ApeironMessNanites");
            Object array = java.lang.reflect.Array.newInstance(type, tiers.length);
            for (int i = 0; i < tiers.length; i++) {
                Object found = null;
                for (Object value : type.getEnumConstants())
                    if (((Number) MessTechAccess.get(value, "tier")).intValue() == tiers[i]) found = value;
                if (found == null) throw new IllegalStateException("Unknown nanite tier: " + tiers[i]);
                java.lang.reflect.Array.set(array, i, found);
            }
            MessTechAccess.set(machine, "requiredNanites", array);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
