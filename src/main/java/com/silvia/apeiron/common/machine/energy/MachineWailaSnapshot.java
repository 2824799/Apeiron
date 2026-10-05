package com.silvia.apeiron.common.machine.energy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.integration.waila.MachineWailaAliases;
import com.silvia.apeiron.common.integration.waila.MachineWailaStructures;
import com.silvia.apeiron.common.integration.waila.WailaNBTBudget;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.parallel.ItemProcessingRecipes;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.math.BigNumberFormatter;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** OmniOcular needs operating information, not a disk-save image containing inventories and output ledgers. */
public final class MachineWailaSnapshot {

    private static final ClassValue<List<Field>> SCALARS = new ClassValue<List<Field>>() {

        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> result = new ArrayList<>();
            for (Class<?> current = type; current != null && result.size() < 192; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    Class<?> value = field.getType();
                    if (!value.isPrimitive() && value != BigInteger.class && value != String.class && !value.isEnum())
                        continue;
                    try {
                        field.setAccessible(true);
                        result.add(field);
                    } catch (RuntimeException unavailable) {
                        // Optional mods may restrict reflection; their native Waila provider still supplies status.
                    }
                }
            }
            return result;
        }
    };

    private MachineWailaSnapshot() {}

    private static final ClassValue<String> TILE_IDS = new ClassValue<String>() {

        @Override
        protected String computeValue(Class<?> type) {
            try {
                for (Field field : TileEntity.class.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())
                        || !java.util.Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object name = ((java.util.Map<?, ?>) field.get(null)).get(type);
                    if (name instanceof String) return (String) name;
                }
                return "";
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read machine HUD tile identifier", failure);
            }
        }
    };

    public static boolean write(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag) {
        if (!(tile instanceof IGregTechTileEntity)) return false;
        IGregTechTileEntity base = (IGregTechTileEntity) tile;
        IMetaTileEntity meta = base.getMetaTileEntity();
        if (meta == null) return false;
        if (ApeironConfig.isLightweightWailaEnabled()) {
            meta.getWailaNBTData(player, tile, tag, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord);
            MachineWailaAliases.write(meta, tag);
            MachineWailaStructures.write(meta, tag);
            writeScalars(meta, tag);
            writeBase(base, tag);
            writeInventory(meta, tag);
            if (meta instanceof MTEMultiBlockBase) writeMultiblock((MTEMultiBlockBase) meta, tag);
            return true;
        }
        // Retain the existing wireless-machine protection when the general compatibility option is disabled.
        if (!(meta instanceof MTEMultiBlockBase)) return false;
        MTEMultiBlockBase machine = (MTEMultiBlockBase) meta;
        boolean wireless = machine instanceof BigWirelessController
            && ((BigWirelessController) machine).getWirelessRecipeState()
                .isRunning();
        boolean itemSource = ItemProcessingRecipes.hasSource(machine.getRecipeMap());
        if (!wireless && !itemSource
            && (machine.mOutputItems == null || machine.mOutputItems.length <= 64)
            && (machine.mOutputFluids == null || machine.mOutputFluids.length <= 64)) return false;
        machine.getWailaNBTData(player, tile, tag, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord);
        writeBase(base, tag);
        writeMultiblock(machine, tag);
        tag.setTag("Inventory", new NBTTagList());
        return true;
    }

    private static void writeBase(IGregTechTileEntity base, NBTTagCompound tag) {
        MachineWailaAliases.write(base, tag);
        TileEntity tile = (TileEntity) base;
        if (!tag.hasKey("id")) tag.setString("id", TILE_IDS.get(tile.getClass()));
        tag.setInteger("x", tile.xCoord);
        tag.setInteger("y", tile.yCoord);
        tag.setInteger("z", tile.zCoord);
        tag.setInteger("mID", base.getMetaTileID());
        tag.setBoolean("mActive", base.isActive());
        // GregTech's disk key is inverted: zero means the machine is allowed to work.
        tag.setBoolean("mWorks", !base.isAllowedToWork());
        tag.setLong("mStoredEnergy", base.getStoredEU());
        tag.setLong("mStoredSteam", base.getStoredSteam());
        tag.setByte("mColor", (byte) (base.getColorization() + 1));
        tag.setShort(
            "mFacing",
            (short) base.getFrontFacing()
                .ordinal());
    }

    private static void writeMultiblock(MTEMultiBlockBase machine, NBTTagCompound tag) {
        long energy = tag.getLong("energyUsage");
        if (tag.hasKey("ApeironActualWirelessEUt")) {
            energy = new BigInteger(tag.getString("ApeironActualWirelessEUt")).min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
        }
        boolean idleWireless = tag.hasKey("ApeironParallelSetting") && !tag.getBoolean("ApeironWirelessRunning");
        tag.setLong("mEUt", idleWireless ? 0 : energy != 0 ? -energy : machine.mEUt);
        tag.setInteger("mEfficiency", machine.mEfficiency);
        tag.setInteger("mProgresstime", machine.mProgresstime);
        tag.setInteger("mMaxProgresstime", machine.mMaxProgresstime);
        tag.setBoolean("mWrench", machine.mWrench);
        tag.setBoolean("mScrewdriver", machine.mScrewdriver);
        tag.setBoolean("mSoftHammer", machine.mSoftMallet);
        tag.setBoolean("mHardHammer", machine.mHardHammer);
        tag.setBoolean("mSolderingTool", machine.mSolderingTool);
        tag.setBoolean("mCrowbar", machine.mCrowbar);
        // Native Waila supplies bounded product previews. Do not reproduce disk-save output arrays.
        tag.setInteger("mOutputItemsLength", 0);
        tag.setInteger("mOutputFluidsLength", 0);
    }

    private static void writeScalars(Object machine, NBTTagCompound tag) {
        for (Field field : SCALARS.get(machine.getClass())) {
            String key = field.getName();
            if (tag.hasKey(key)) continue;
            try {
                Object value = field.get(machine);
                if (value instanceof Boolean) tag.setBoolean(key, (Boolean) value);
                else if (value instanceof Byte) tag.setByte(key, (Byte) value);
                else if (value instanceof Short) tag.setShort(key, (Short) value);
                else if (value instanceof Integer) tag.setInteger(key, (Integer) value);
                else if (value instanceof Long) tag.setLong(key, (Long) value);
                else if (value instanceof Float) tag.setFloat(key, (Float) value);
                else if (value instanceof Double) tag.setDouble(key, (Double) value);
                else if (value instanceof Enum<?>) tag.setInteger(key, ((Enum<?>) value).ordinal());
                else if (value instanceof String && ((String) value).length() <= 4096)
                    tag.setString(key, (String) value);
                else if (value instanceof BigInteger) {
                    BigInteger number = (BigInteger) value;
                    if (number.bitLength() < 4096) tag.setByteArray(key, number.toByteArray());
                    else tag.setString(key, BigNumberFormatter.formatCompact(number));
                }
            } catch (IllegalAccessException unavailable) {
                // Native status remains usable without the optional script aliases.
            }
        }
    }

    private static void writeInventory(IMetaTileEntity machine, NBTTagCompound tag) {
        NBTTagList preview = new NBTTagList();
        // Encoded patterns have their own recipe/status preview and can contain deeply nested inventories.
        if (!(machine instanceof MTEInfinitePatternInputAssembly)) {
            ItemStack[] contents = machine.getRealInventory();
            if (contents != null) for (int slot = 0; slot < contents.length && preview.tagCount() < 64; slot++) {
                if (contents[slot] == null) continue;
                NBTTagCompound item = MachineWailaStructures.item(contents[slot]);
                item.setInteger("IntSlot", slot);
                item.setByte("Slot", (byte) slot);
                preview.appendTag(WailaNBTBudget.limit(item));
            }
        }
        tag.setTag("Inventory", preview);
    }
}
