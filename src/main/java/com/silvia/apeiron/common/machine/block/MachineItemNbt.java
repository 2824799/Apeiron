package com.silvia.apeiron.common.machine.block;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

import com.silvia.apeiron.common.machine.me.circuit.MTEInfiniteProgrammingCircuitProvider;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;
import com.silvia.apeiron.common.machine.me.output.MTEBeamlineMEOutputHatch;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.tectech.MTEEyeOfHarmonyEnhancementModule;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** Removes obsolete empty machine state from items already in inventories. */
public final class MachineItemNbt {

    private MachineItemNbt() {}

    public static void normalize(ItemStack stack) {
        if (!stack.hasTagCompound()) return;
        int id = stack.getItemDamage();
        if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length) return;
        IMetaTileEntity machine = GregTechAPI.METATILEENTITIES[id];
        NBTTagCompound tag = stack.getTagCompound();
        boolean contents;
        if (machine instanceof MTEBoundlessMEOutputBus || machine instanceof MTEBoundlessMEOutputHatch
            || machine instanceof MTEInfiniteMEOutputAssembly) {
            normalizeOutput(tag);
            if (machine instanceof MTEInfiniteMEOutputAssembly && tag.hasKey("ApeironAssemblyFluids", 10)) {
                NBTTagCompound fluids = tag.getCompoundTag("ApeironAssemblyFluids");
                normalizeOutput(fluids);
                if (fluids.hasNoTags()) tag.removeTag("ApeironAssemblyFluids");
            }
            contents = tag.hasKey("cache") || tag.hasKey("ApeironAssemblyFluids");
            if (machine instanceof MTEBeamlineMEOutputHatch) {
                NBTTagCompound beams = tag.getCompoundTag(MTEBeamlineMEOutputHatch.PENDING_TAG);
                if (beams.hasNoTags()) tag.removeTag(MTEBeamlineMEOutputHatch.PENDING_TAG);
                contents |= tag.hasKey(MTEBeamlineMEOutputHatch.PENDING_TAG);
            }
        } else if (machine instanceof MTEInfinitePatternInputAssembly) {
            NBTTagCompound state = tag.getCompoundTag("ApeironPatternInput");
            normalizePatternState(state);
            contents = state.hasKey("inventory") || state.hasKey("buffers")
                || state.hasKey("multipliers")
                || state.getBoolean("connections")
                || state.getBoolean("multiplierView")
                || (state.hasKey("patternOptimization") && !state.getBoolean("patternOptimization"))
                || (state.hasKey("terminalVisible") && !state.getBoolean("terminalVisible"))
                || !state.getString("name")
                    .isEmpty();
            NBTTagList buffers = state.getTagList("buffers", 10);
            for (int i = 0; i < buffers.tagCount(); i++) contents |= buffers.getCompoundTagAt(i)
                .getTagList("stacks", 10)
                .tagCount() > 0;
            if (state.hasNoTags()) tag.removeTag("ApeironPatternInput");
            tag.removeTag("proxy");
        } else if (machine instanceof MTEInfinitePatternInputMirror) {
            if (!tag.getBoolean("ApeironInputLinked")) for (String key : new String[] { "ApeironInputLinked",
                "ApeironInputDimension", "ApeironInputX", "ApeironInputY", "ApeironInputZ" }) tag.removeTag(key);
            contents = tag.getBoolean("ApeironInputLinked");
        } else if (machine instanceof StockingInputHost) {
            normalizeStockingState(tag);
            contents = tag.hasKey("ApeironStockingInput");
        } else if (machine instanceof MTEInfiniteProgrammingCircuitProvider) {
            if (!tag.getBoolean("ApeironAllSides")) tag.removeTag("ApeironAllSides");
            tag.removeTag("proxy");
            contents = tag.getBoolean("ApeironAllSides");
        } else if (machine instanceof MTEEyeOfHarmonyEnhancementModule) {
            NBTTagCompound settings = tag.getCompoundTag(MTEEyeOfHarmonyEnhancementModule.ROOT_TAG);
            if (settings.getInteger(MTEEyeOfHarmonyEnhancementModule.DURATION_TAG)
                == MTEEyeOfHarmonyEnhancementModule.DEFAULT_DURATION)
                settings.removeTag(MTEEyeOfHarmonyEnhancementModule.DURATION_TAG);
            if (settings.getDouble(MTEEyeOfHarmonyEnhancementModule.SUCCESS_CHANCE_TAG)
                == MTEEyeOfHarmonyEnhancementModule.DEFAULT_SUCCESS_CHANCE)
                settings.removeTag(MTEEyeOfHarmonyEnhancementModule.SUCCESS_CHANCE_TAG);
            if (settings.hasNoTags()) tag.removeTag(MTEEyeOfHarmonyEnhancementModule.ROOT_TAG);
            contents = tag.hasKey(MTEEyeOfHarmonyEnhancementModule.ROOT_TAG);
        } else return;
        boolean inventory = hasInventory(tag.getTagList("Inventory", 10));
        if (!inventory && tag.hasKey("Inventory", 9)) tag.removeTag("Inventory");
        if (!contents && !inventory) tag.removeTag("ApeironContents");
        if (tag.hasNoTags()) stack.setTagCompound(null);
    }

    private static boolean hasInventory(NBTTagList inventory) {
        for (int i = 0; i < inventory.tagCount(); i++) if (inventory.getCompoundTagAt(i)
            .getLong("Count") > 0) return true;
        return false;
    }

    private static void normalizeOutput(NBTTagCompound tag) {
        if (!tag.getBoolean("additionalConnection")) tag.removeTag("additionalConnection");
        if (!tag.getBoolean("cacheMode")) tag.removeTag("cacheMode");
        if (tag.getInteger("myPriority") == 0) tag.removeTag("myPriority");
        for (String key : new String[] { "baseCapacity", "ApeironBaseCapacity", "checkMode", "proxy" })
            tag.removeTag(key);
        NBTBase cache = tag.getTag("cache");
        if (cache instanceof NBTTagList && ((NBTTagList) cache).tagCount() == 0) tag.removeTag("cache");
        // Older drops can wrap the default cache settings in a compound. Keep unknown payloads intact.
        if (cache instanceof NBTTagCompound) {
            NBTTagCompound state = (NBTTagCompound) cache;
            for (String key : new String[] { "cacheMode", "myPriority" }) state.removeTag(key);
            if (state.hasNoTags()) tag.removeTag("cache");
        }
    }

    private static void normalizePatternState(NBTTagCompound state) {
        if (state.hasKey("connections") && !state.getBoolean("connections")) state.removeTag("connections");
        if (state.hasKey("multiplierView") && !state.getBoolean("multiplierView")) state.removeTag("multiplierView");
        if (state.hasKey("patternOptimization") && state.getBoolean("patternOptimization"))
            state.removeTag("patternOptimization");
        if (state.hasKey("terminalVisible") && state.getBoolean("terminalVisible")) state.removeTag("terminalVisible");
        if (state.hasKey("name") && state.getString("name")
            .isEmpty()) state.removeTag("name");
        state.removeTag("savedPushCalls");
        state.removeTag("savedPushCallsBig");
        removeEmptyList(state, "multipliers");
        removeEmptyList(state, "inventory");
        NBTBase buffers = state.getTag("buffers");
        if (buffers instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) buffers;
            boolean any = false;
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound buffer = list.getCompoundTagAt(i);
                any |= buffer.getInteger("slot") >= 0 || buffer.getBoolean("locked")
                    || buffer.getTagList("recipeInputs", 10)
                        .tagCount() > 0
                    || buffer.hasKey("pattern", 10)
                    || buffer.getTagList("stacks", 10)
                        .tagCount() > 0
                    || buffer.getTagList("selectors", 10)
                        .tagCount() > 0;
            }
            if (!any) state.removeTag("buffers");
        }
    }

    private static void removeEmptyList(NBTTagCompound tag, String key) {
        NBTBase value = tag.getTag(key);
        if (value instanceof NBTTagList && ((NBTTagList) value).tagCount() == 0) tag.removeTag(key);
    }

    private static void normalizeStockingState(NBTTagCompound root) {
        NBTTagCompound state = root.getCompoundTag("ApeironStockingInput");
        if (state.hasKey("minimum") && "1".equals(
            state.getString("minimum")
                .trim()))
            state.removeTag("minimum");
        if (state.hasKey("refresh") && state.getInteger("refresh") == 100) state.removeTag("refresh");
        if (state.hasKey("mod") && state.getString("mod")
            .isEmpty()) state.removeTag("mod");
        if (state.hasKey("item") && "*".equals(state.getString("item"))) state.removeTag("item");
        if (state.hasKey("ore") && state.getString("ore")
            .isEmpty()) state.removeTag("ore");
        if (state.hasKey("meta") && state.getInteger("meta") == OreDictionary.WILDCARD_VALUE) state.removeTag("meta");
        if (state.hasKey("auto") && !state.getBoolean("auto")) state.removeTag("auto");
        removeEmptyList(state, "marks");
        removeEmptyList(state, "refunds");
        if (state.hasNoTags()) root.removeTag("ApeironStockingInput");
    }
}
