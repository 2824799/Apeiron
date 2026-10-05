package com.silvia.apeiron.common.integration.waila;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** Bounded traversal before compression. It never modifies the provider's tag or a live inventory. */
public final class WailaNBTBudget {

    public static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_NODES = 2048;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_LIST = 64;
    private static final int MAX_ARRAY = 512;
    private static final int MAX_STRING = 4096;
    private static final int MAX_KEYS = 256;
    private static final Field LIST_CONTENTS = listContentsField();
    private static final String[] PRIORITY = { "id", "x", "y", "z", "WailaX", "WailaY", "WailaZ", "mID", "mActive",
        "mWorks", "mEUt", "mStoredEnergy", "mStoredSteam", "mProgresstime", "mMaxProgresstime", "isActive", "isPowered",
        "isBooting", "energyUsage", "energyTier", "progress", "maxProgress", "ApeironSavedPushCalls",
        "ApeironBufferStatus", "ApeironActualWirelessEUt", "ApeironActualWirelessEUtDisplay",
        "ApeironActualWirelessTotalEUDisplay", "ApeironActualWirelessAmperageDisplay", "ApeironWirelessInputVoltage",
        "ApeironWirelessOutputRows", "ApeironEyeOfHarmony", "stored.fluid.hydrogen", "stored.fluid.helium",
        "stored.fluid.rawstarmatter", "eyeOfHarmonyOutputrecipeRunning", "eyeOfHarmonyOutputrecipeSuccessChance",
        "eyeOfHarmonyOutputrocketTier", "eyeOfHarmonyOutputcurrentCircuitMultiplier",
        "eyeOfHarmonyOutputanimationsEnabled", "eyeOfHarmonyOutputparallelAmount",
        "eyeOfHarmonyOutputsuccessfulParallelAmount", "eyeOfHarmonyOutputyield", "eyeOfHarmonyOutputastralArrayAmount",
        "eyeOfHarmonyOutputoutputEU_BigInt", "eyeOfHarmonyOutputusedEU", "eyeOfHarmonyOutputfluidOutput",
        "eyeOfHarmonyOutputrecipeStarMatter", "eyeOfHarmonyOutputrecipeStellarPlasma" };

    private int bytes = MAX_BYTES - 128; // Root header and the truncation marker also occupy space.
    private int nodes = MAX_NODES;
    private boolean truncated;

    private WailaNBTBudget() {}

    private static Field listContentsField() {
        // 1.7.10 has no public getter for arbitrary list elements. Type-based lookup works in mapped and obfuscated
        // jars.
        for (Field field : NBTTagList.class.getDeclaredFields()) if (List.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true);
            return field;
        }
        throw new IllegalStateException("NBTTagList contents field unavailable");
    }

    @SuppressWarnings("unchecked")
    private static List<NBTBase> listContents(NBTTagList list) {
        try {
            return (List<NBTBase>) LIST_CONTENTS.get(list);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Unable to read NBTTagList preview", failure);
        }
    }

    public static NBTTagCompound limit(NBTTagCompound source) {
        if (source == null) return null;
        WailaNBTBudget budget = new WailaNBTBudget();
        NBTTagCompound result = (NBTTagCompound) budget.copy(source, 0);
        if (!budget.truncated) return source;
        result.setBoolean("ApeironWailaTruncated", true);
        return result;
    }

    private boolean spend(int cost) {
        if (cost > bytes || nodes <= 0) {
            truncated = true;
            return false;
        }
        bytes -= cost;
        return true;
    }

    private NBTBase copy(NBTBase source, int depth) {
        if (depth > MAX_DEPTH || nodes-- <= 0) {
            truncated = true;
            return null;
        }
        switch (source.getId()) {
            case 10: {
                if (!spend(1)) return null; // Compound terminator.
                NBTTagCompound input = (NBTTagCompound) source;
                NBTTagCompound output = new NBTTagCompound();
                Set<String> visited = new HashSet<>();
                if (depth == 0) for (String key : PRIORITY) if (input.hasKey(key)) {
                    entry(input, output, key, depth);
                    visited.add(key);
                }
                // A large inventory must not consume the budget before an arbitrary provider's ordinary status.
                if (depth == 0) {
                    int scanned = 0;
                    for (String key : input.func_150296_c()) {
                        if (++scanned > MAX_KEYS) break;
                        NBTBase value = input.getTag(key);
                        byte type = value.getId();
                        boolean scalar = type >= 1 && type <= 6 || type == 8 && ((NBTTagString) value).func_150285_a_()
                            .length() <= 256;
                        if (!visited.contains(key) && scalar) {
                            entry(input, output, key, depth);
                            visited.add(key);
                        }
                    }
                }
                int examined = 0;
                for (String key : input.func_150296_c()) {
                    if (++examined > MAX_KEYS || nodes <= 0 || bytes <= 0) {
                        truncated = true;
                        break;
                    }
                    if (!visited.contains(key)) entry(input, output, key, depth);
                }
                return output;
            }
            case 9: {
                if (!spend(5)) return null;
                NBTTagList input = (NBTTagList) source;
                NBTTagList output = new NBTTagList();
                int count = Math.min(MAX_LIST, input.tagCount());
                if (count < input.tagCount()) truncated = true;
                List<NBTBase> contents = listContents(input);
                for (int i = 0; i < count; i++) {
                    NBTBase element = copy(contents.get(i), depth + 1);
                    if (element == null) break;
                    output.appendTag(element);
                }
                return output;
            }
            case 7: {
                byte[] input = ((NBTTagByteArray) source).func_150292_c();
                if (input.length > MAX_ARRAY) {
                    truncated = true;
                    return null;
                }
                return spend(4 + input.length) ? new NBTTagByteArray(Arrays.copyOf(input, input.length)) : null;
            }
            case 11: {
                int[] input = ((NBTTagIntArray) source).func_150302_c();
                if (input.length > MAX_ARRAY) {
                    truncated = true;
                    return null;
                }
                return spend(4 + input.length * 4) ? new NBTTagIntArray(Arrays.copyOf(input, input.length)) : null;
            }
            case 8: {
                String value = ((NBTTagString) source).func_150285_a_();
                // Do not silently change an exact numeric value by retaining only its first digits.
                if (value.length() > MAX_STRING) {
                    truncated = true;
                    return null;
                }
                return spend(2 + value.length() * 3) ? source.copy() : null;
            }
            default:
                int cost = source.getId() == 1 ? 1
                    : source.getId() == 2 ? 2 : source.getId() == 3 || source.getId() == 5 ? 4 : 8;
                return spend(cost) ? source.copy() : null;
        }
    }

    private void entry(NBTTagCompound input, NBTTagCompound output, String key, int depth) {
        if (key.length() > 128 || !spend(3 + key.length() * 3)) {
            truncated = true;
            return;
        }
        NBTBase value = copy(input.getTag(key), depth + 1);
        if (value != null) output.setTag(key, value);
    }
}
