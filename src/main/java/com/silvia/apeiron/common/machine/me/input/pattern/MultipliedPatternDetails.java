// SPDX-License-Identifier: MIT
// Pattern multiplier wrapper adapted from Programmable Hatches (c) 2024 reobf.
package com.silvia.apeiron.common.machine.me.input.pattern;

import java.math.BigInteger;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;

/** The encoded item in the UI remains unchanged; this serializable wrapper is advertised to AE. */
public final class MultipliedPatternDetails implements ICraftingPatternDetails {

    private final ICraftingPatternDetails original;
    private final BigInteger multiplier;
    private final NBTTagCompound identity;

    public MultipliedPatternDetails(ICraftingPatternDetails original, BigInteger multiplier) {
        if (original == null || multiplier.signum() <= 0)
            throw new IllegalArgumentException("Invalid multiplied pattern");
        this.original = original;
        this.multiplier = multiplier;
        identity = original.getPattern()
            .writeToNBT(new NBTTagCompound());
    }

    public BigInteger getMultiplierBig() {
        return multiplier;
    }

    @Override
    public ItemStack getPattern() {
        ItemStack item = new ItemStack(ApeironPatternItems.multipliedPattern);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("basePattern", identity.copy());
        BigValueCodec.writeNBT(tag, "multiplier", "ApeironMultiplierBig", new AdaptiveInteger(multiplier));
        item.setTagCompound(tag);
        return item;
    }

    private IAEStack<?>[] multiply(IAEStack<?>[] source) {
        IAEStack<?>[] result = new IAEStack<?>[source.length];
        for (int i = 0; i < source.length; i++) if (source[i] != null) result[i] = BigAEStackValues.copyWithSize(
            source[i],
            BigAEStackValues.get(source[i])
                .multiply(multiplier));
        return result;
    }

    @Override
    public IAEStack<?>[] getAEInputs() {
        return multiply(original.getAEInputs());
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return multiply(original.getAEOutputs());
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return multiply(original.getCondensedAEInputs());
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return multiply(original.getCondensedAEOutputs());
    }

    private static IAEItemStack[] legacy(IAEStack<?>[] stacks) {
        IAEItemStack[] result = new IAEItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++)
            if (stacks[i] != null) result[i] = Platform.stackConvertPacket(stacks[i]);
        return result;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return legacy(getAEInputs());
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return legacy(getAEOutputs());
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return legacy(getCondensedAEInputs());
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return legacy(getCondensedAEOutputs());
    }

    @Override
    public boolean isCraftable() {
        return original.isCraftable();
    }

    @Override
    public boolean canSubstitute() {
        return original.canSubstitute();
    }

    @Override
    public boolean canBeSubstitute() {
        return original.canBeSubstitute();
    }

    @Override
    public boolean isValidItemForSlot(int index, ItemStack stack, World world) {
        return original.isValidItemForSlot(index, stack, world);
    }

    @Override
    public boolean isValidItemForSlot(int index, IAEStack<?> stack, World world) {
        return original.isValidItemForSlot(index, stack, world);
    }

    @Override
    public boolean isInputOnly() {
        return original.isInputOnly();
    }

    @Override
    public java.util.UUID getInputOnlyUuid() {
        return original.getInputOnlyUuid();
    }

    @Override
    public ItemStack getOutput(InventoryCrafting inventory, World world) {
        return original.getOutput(inventory, world);
    }

    @Override
    public int getPriority() {
        return original.getPriority();
    }

    @Override
    public void setPriority(int priority) {
        original.setPriority(priority);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MultipliedPatternDetails && identity.equals(((MultipliedPatternDetails) other).identity)
            && multiplier.equals(((MultipliedPatternDetails) other).multiplier);
    }

    @Override
    public int hashCode() {
        return 31 * identity.hashCode() + multiplier.hashCode();
    }
}
