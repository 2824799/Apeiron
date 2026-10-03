// SPDX-License-Identifier: MIT
// Recipe isolation and programmed zero-count selectors adapted from Programmable Hatches (c) 2024 reobf.
package com.silvia.apeiron.common.machine.me.input.storage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.objects.GTDualInputPattern;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputInventoryWithPattern;

/** Exact storage is authoritative. Mutable int views are reconciled once after native recipe processing. */
public final class BigPatternBuffer implements IDualInputInventoryWithPattern {

    public static final int ITEM_SLOTS = 32;
    public static final int FLUID_SLOTS = 32;
    private final List<IAEItemStack> items = new ArrayList<>();
    private final List<IAEFluidStack> fluids = new ArrayList<>();
    private final List<ItemStack> selectors = new ArrayList<>();
    private final List<IAEStack<?>> recipeInputs = new ArrayList<>();
    private boolean locked;
    private int manualSelectorCount;
    private ItemStack pattern;
    private int patternSlot = -1;
    private ItemStack[] itemView;
    private FluidStack[] fluidView;
    private int[] itemBaseline;
    private int[] fluidBaseline;

    public int getPatternSlot() {
        return patternSlot;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean value) {
        locked = value && hasRecipe();
    }

    public boolean hasRecipe() {
        return !recipeInputs.isEmpty();
    }

    public List<ItemStack> getSelectors() {
        return getSelectors(Integer.MAX_VALUE);
    }

    public int getSelectorCount() {
        return selectors.size();
    }

    public List<ItemStack> getSelectors(int limit) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, selectors.size()); i++) result.add(
            selectors.get(i)
                .copy());
        return result;
    }

    public List<IAEStack<?>> getRecipeInputs() {
        return getRecipeInputs(Integer.MAX_VALUE);
    }

    public int getRecipeInputCount() {
        return recipeInputs.size();
    }

    public List<IAEStack<?>> getRecipeInputs(int limit) {
        List<IAEStack<?>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, recipeInputs.size()); i++) result.add(
            recipeInputs.get(i)
                .copy());
        return result;
    }

    public void setRecipeInputs(List<IAEStack<?>> inputs) {
        recipeInputs.clear();
        for (IAEStack<?> input : inputs) if (BigAEStackValues.get(input)
            .signum() > 0) recipeInputs.add(input.copy());
    }

    public boolean hasSameRecipeInputs(List<IAEStack<?>> input) {
        if (recipeInputs.isEmpty()) return true; // Older saves have no recorded shape.
        if (input.size() != recipeInputs.size()) return false;
        for (IAEStack<?> expected : recipeInputs) {
            boolean found = false;
            for (IAEStack<?> offered : input) if (sameType(expected, offered) && BigAEStackValues.get(expected)
                .equals(BigAEStackValues.get(offered))) found = true;
            if (!found) return false;
        }
        return true;
    }

    public BigInteger getPossibleBatchesBig() {
        BigInteger count = null;
        for (IAEStack<?> input : recipeInputs) {
            BigInteger copies = getAmountBig(input).divide(BigAEStackValues.get(input));
            count = count == null ? copies : count.min(copies);
        }
        return count == null ? BigInteger.ZERO : count;
    }

    public boolean ownsViews(ItemStack[] providedItems, FluidStack[] providedFluids) {
        if (itemView == null || providedItems == null || providedFluids == null) return false;
        for (ItemStack item : itemView) if (item != null) {
            boolean found = false;
            for (ItemStack candidate : providedItems) if (candidate == item) found = true;
            if (!found) return false;
        }
        for (FluidStack fluid : fluidView) if (fluid != null) {
            boolean found = false;
            for (FluidStack candidate : providedFluids) if (candidate == fluid) found = true;
            if (!found) return false;
        }
        return itemView.length > 0 || fluidView.length > 0;
    }

    public boolean belongsTo(int slot, ItemStack encoded, List<ItemStack> catalysts) {
        if (patternSlot != slot || !ItemStack.areItemStacksEqual(pattern, encoded)
            || selectors.size() != catalysts.size()) return false;
        for (int i = 0; i < selectors.size(); i++)
            if (!GTUtility.areStacksEqual(selectors.get(i), catalysts.get(i))) return false;
        return true;
    }

    public void assign(int slot, ItemStack encoded, List<ItemStack> catalysts) {
        assign(slot, encoded, catalysts, 0);
    }

    public void assign(int slot, ItemStack encoded, List<ItemStack> catalysts, int manualCount) {
        if (!isEmpty()) throw new IllegalStateException("Cannot reassign a nonempty recipe buffer");
        clearViews();
        items.clear();
        fluids.clear();
        selectors.clear();
        manualSelectorCount = manualCount;
        patternSlot = slot;
        pattern = encoded == null ? null : encoded.copy();
        recipeInputs.clear();
        for (ItemStack catalyst : catalysts) {
            ItemStack copy = catalyst.copy();
            copy.stackSize = 0;
            selectors.add(copy);
        }
    }

    public void updateManualSelectors(List<ItemStack> manual) {
        reconcile();
        List<ItemStack> programmed = new ArrayList<>(
            selectors.subList(Math.min(manualSelectorCount, selectors.size()), selectors.size()));
        selectors.clear();
        for (ItemStack stack : manual) {
            ItemStack copy = stack.copy();
            copy.stackSize = 0;
            selectors.add(copy);
        }
        selectors.addAll(programmed);
        manualSelectorCount = manual.size();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public boolean canAdd(List<IAEStack<?>> input) {
        List<IAEStack<?>> combined = new ArrayList<>();
        combined.addAll(items);
        combined.addAll(fluids);
        for (IAEStack<?> stack : input) {
            if (!(stack instanceof IAEItemStack) && !(stack instanceof IAEFluidStack)) return false;
            if (BigAEStackValues.get(stack)
                .signum() <= 0) continue;
            if (locked && recipeInputs.stream()
                .noneMatch(ingredient -> sameType(ingredient, stack))) return false;
            boolean known = false;
            for (IAEStack<?> existing : combined)
                if (existing.getClass() == stack.getClass() && ((IAEStack) existing).isSameType(stack)) {
                    known = true;
                    break;
                }
            if (!known) combined.add(stack);
        }
        long itemTypes = combined.stream()
            .filter(s -> s instanceof IAEItemStack)
            .count();
        return itemTypes <= ITEM_SLOTS && combined.size() - itemTypes <= FLUID_SLOTS;
    }

    public boolean add(List<IAEStack<?>> input, BigInteger batches) {
        if (batches.signum() <= 0) throw new IllegalArgumentException("Batch count must be positive");
        if (!canAdd(input)) return false;
        reconcile();
        for (IAEStack<?> stack : input) {
            if (BigAEStackValues.get(stack)
                .signum() <= 0) continue;
            if (stack instanceof IAEItemStack) merge(items, (IAEItemStack) stack, batches);
            else merge(fluids, (IAEFluidStack) stack, batches);
        }
        return true;
    }

    private static <T extends IAEStack<T>> void merge(List<T> into, T input, BigInteger batches) {
        BigInteger amount = BigAEStackValues.get(input)
            .multiply(batches);
        for (T existing : into) if (existing.isSameType(input)) {
            BigAEStackValues.set(
                existing,
                BigAEStackValues.get(existing)
                    .add(amount));
            return;
        }
        into.add(BigAEStackValues.copyWithSize(input, amount));
    }

    public List<IAEStack<?>> getStacksBig() {
        List<IAEStack<?>> result = new ArrayList<>();
        for (IAEItemStack stack : items) result.add(stack.copy());
        for (IAEFluidStack stack : fluids) result.add(stack.copy());
        return result;
    }

    public BigInteger getItemAmountBig() {
        BigInteger sum = BigInteger.ZERO;
        for (IAEItemStack stack : items) sum = sum.add(BigAEStackValues.get(stack));
        return sum;
    }

    public BigInteger getFluidAmountBig() {
        BigInteger sum = BigInteger.ZERO;
        for (IAEFluidStack stack : fluids) sum = sum.add(BigAEStackValues.get(stack));
        return sum;
    }

    public BigInteger getAmountBig(IAEStack<?> type) {
        List<? extends IAEStack<?>> channel = type instanceof IAEFluidStack ? fluids : items;
        for (IAEStack<?> stored : channel) {
            if (sameType(stored, type)) return BigAEStackValues.get(stored);
        }
        return BigInteger.ZERO;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean sameType(IAEStack<?> a, IAEStack<?> b) {
        return a.getClass() == b.getClass() && ((IAEStack) a).isSameType(b);
    }

    public void removeBig(IAEStack<?> type, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative removal");
        reconcile();
        List<? extends IAEStack<?>> channel = type instanceof IAEFluidStack ? fluids : items;
        for (int i = 0; i < channel.size(); i++) {
            IAEStack<?> stored = channel.get(i);
            if (!sameType(stored, type)) continue;
            BigInteger remaining = BigAEStackValues.get(stored)
                .subtract(amount);
            if (remaining.signum() < 0) throw new IllegalArgumentException("Insufficient input");
            if (remaining.signum() == 0) channel.remove(i);
            else BigAEStackValues.set(stored, remaining);
            return;
        }
        if (amount.signum() > 0) throw new IllegalArgumentException("Unknown input");
    }

    @Override
    public boolean isEmpty() {
        return getItemAmountBig().signum() == 0 && getFluidAmountBig().signum() == 0;
    }

    private void prepareViews() {
        if (itemView != null) return;
        itemView = new ItemStack[items.size() + selectors.size()];
        itemBaseline = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            itemView[i] = items.get(i)
                .getItemStack();
            itemView[i].stackSize = BigAEStackValues.get(items.get(i))
                .min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
            itemBaseline[i] = itemView[i].stackSize;
        }
        for (int i = 0; i < selectors.size(); i++) itemView[items.size() + i] = selectors.get(i)
            .copy();
        fluidView = new FluidStack[fluids.size()];
        fluidBaseline = new int[fluids.size()];
        for (int i = 0; i < fluids.size(); i++) {
            fluidView[i] = fluids.get(i)
                .getFluidStack();
            fluidView[i].amount = BigAEStackValues.get(fluids.get(i))
                .min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
            fluidBaseline[i] = fluidView[i].amount;
        }
    }

    @Override
    public ItemStack[] getItemInputs() {
        prepareViews();
        return itemView;
    }

    @Override
    public FluidStack[] getFluidInputs() {
        prepareViews();
        return fluidView;
    }

    @Override
    public boolean shouldBeCached() {
        return false;
    }

    @Override
    public GTDualInputPattern getPatternInputs() {
        prepareViews();
        ItemStack[] shapeItems = new ItemStack[itemView.length];
        FluidStack[] shapeFluids = new FluidStack[fluidView.length];
        for (int i = 0; i < itemView.length; i++) shapeItems[i] = itemView[i] == null ? null : itemView[i].copy();
        for (int i = 0; i < fluidView.length; i++) shapeFluids[i] = fluidView[i] == null ? null : fluidView[i].copy();
        return new GTDualInputPattern(shapeItems, shapeFluids);
    }

    public void reconcile() {
        if (itemView == null) return;
        // Validate all deltas before committing any, including replacement/type changes.
        for (int i = 0; i < items.size(); i++) {
            int remaining = itemView[i] == null ? 0 : itemView[i].stackSize;
            if (remaining < 0 || remaining > itemBaseline[i]
                || itemView[i] != null && !items.get(i)
                    .isSameType(itemView[i]))
                throw new IllegalStateException("Invalid item mutation in recipe input view");
        }
        for (int i = 0; i < fluids.size(); i++) {
            int remaining = fluidView[i] == null ? 0 : fluidView[i].amount;
            if (remaining < 0 || remaining > fluidBaseline[i]
                || fluidView[i] != null && !fluids.get(i)
                    .getFluidStack()
                    .isFluidEqual(fluidView[i]))
                throw new IllegalStateException("Invalid fluid mutation in recipe input view");
        }
        for (int i = 0; i < items.size(); i++) BigAEStackValues.set(
            items.get(i),
            BigAEStackValues.get(items.get(i))
                .subtract(BigInteger.valueOf(itemBaseline[i] - (itemView[i] == null ? 0 : itemView[i].stackSize))));
        for (int i = 0; i < fluids.size(); i++) BigAEStackValues.set(
            fluids.get(i),
            BigAEStackValues.get(fluids.get(i))
                .subtract(BigInteger.valueOf(fluidBaseline[i] - (fluidView[i] == null ? 0 : fluidView[i].amount))));
        items.removeIf(
            s -> BigAEStackValues.get(s)
                .signum() == 0);
        fluids.removeIf(
            s -> BigAEStackValues.get(s)
                .signum() == 0);
        clearViews();
    }

    private void clearViews() {
        itemView = null;
        fluidView = null;
        itemBaseline = null;
        fluidBaseline = null;
    }

    public NBTTagCompound writeNBT() {
        reconcile();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("slot", patternSlot);
        tag.setInteger("manualSelectorCount", manualSelectorCount);
        tag.setBoolean("locked", locked);
        NBTTagList recipe = new NBTTagList();
        for (IAEStack<?> input : recipeInputs) recipe.appendTag(BigPatternStackCodec.write(input));
        tag.setTag("recipeInputs", recipe);
        if (pattern != null) tag.setTag("pattern", pattern.writeToNBT(new NBTTagCompound()));
        NBTTagList stacks = new NBTTagList();
        for (IAEStack<?> stack : getStacksBig()) stacks.appendTag(BigPatternStackCodec.write(stack));
        tag.setTag("stacks", stacks);
        NBTTagList catalysts = new NBTTagList();
        for (ItemStack stack : selectors) catalysts.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("selectors", catalysts);
        return tag;
    }

    public void readNBT(NBTTagCompound tag) {
        clearViews();
        items.clear();
        fluids.clear();
        selectors.clear();
        recipeInputs.clear();
        boolean savedLock = tag.getBoolean("locked");
        locked = false;
        NBTTagList recipe = tag.getTagList("recipeInputs", 10);
        for (int i = 0; i < recipe.tagCount(); i++) {
            IAEStack<?> input = BigPatternStackCodec.read(recipe.getCompoundTagAt(i));
            if (input != null && BigAEStackValues.get(input)
                .signum() > 0) recipeInputs.add(input);
        }
        patternSlot = tag.hasKey("slot") ? tag.getInteger("slot") : -1;
        manualSelectorCount = Math.max(0, tag.getInteger("manualSelectorCount"));
        pattern = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("pattern"));
        NBTTagList stacks = tag.getTagList("stacks", 10);
        List<IAEStack<?>> input = new ArrayList<>();
        for (int i = 0; i < stacks.tagCount(); i++) {
            IAEStack<?> stack = BigPatternStackCodec.read(stacks.getCompoundTagAt(i));
            if (stack != null && BigAEStackValues.get(stack)
                .signum() > 0) input.add(stack);
        }
        if (!add(input, BigInteger.ONE)) throw new IllegalArgumentException("Saved recipe buffer has too many types");
        NBTTagList catalysts = tag.getTagList("selectors", 10);
        for (int i = 0; i < catalysts.tagCount(); i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(catalysts.getCompoundTagAt(i));
            if (stack != null) {
                stack.stackSize = 0;
                selectors.add(stack);
            }
        }
        locked = savedLock;
    }
}
