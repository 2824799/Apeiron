// SPDX-License-Identifier: GPL-3.0-only
// Combined selection rules adapted from GT Not Leisure's TypeFilteredInputBusME and OredictInputBusME.
package com.silvia.apeiron.common.machine.me.stocking;

import java.math.BigInteger;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.ScientificInteger;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

public final class StockingFilter {

    private BigInteger minimum = BigInteger.ONE;
    private String minimumText = "1";
    private int refreshTicks = 100;
    private String modId = "", itemId = "*", ore = "";
    private int meta = OreDictionary.WILDCARD_VALUE;

    public BigInteger getMinimum() {
        return minimum;
    }

    public void setMinimum(String value) {
        minimum = ScientificInteger.positive(value);
        minimumText = value.trim();
    }

    public String getMinimumText() {
        return minimumText;
    }

    public int getRefreshTicks() {
        return refreshTicks;
    }

    public void setRefreshTicks(int ticks) {
        refreshTicks = Math.max(1, ticks);
    }

    public String getModId() {
        return modId;
    }

    public void setModId(String value) {
        modId = limited(value);
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String value) {
        itemId = limited(value);
    }

    public String getOre() {
        return ore;
    }

    public void setOre(String value) {
        ore = limited(value);
    }

    public int getMeta() {
        return meta;
    }

    public boolean isDefault() {
        return minimum.equals(BigInteger.ONE) && "1".equals(minimumText)
            && refreshTicks == 100
            && modId.isEmpty()
            && "*".equals(itemId)
            && ore.isEmpty()
            && meta == OreDictionary.WILDCARD_VALUE;
    }

    public void setMeta(int value) {
        meta = value < 0 ? OreDictionary.WILDCARD_VALUE : value;
    }

    private static String limited(String value) {
        String result = value.trim();
        if (result.length() > 256) throw new IllegalArgumentException("Filter too long");
        return result;
    }

    public static boolean glob(String filter, String value) {
        if (filter.isEmpty() || filter.equals("*")) return true;
        int pattern = 0, text = 0, star = -1, retry = 0;
        while (text < value.length()) {
            if (pattern < filter.length()
                && (filter.charAt(pattern) == '?' || filter.charAt(pattern) == value.charAt(text))) {
                pattern++;
                text++;
            } else if (pattern < filter.length() && filter.charAt(pattern) == '*') {
                star = pattern++;
                retry = text;
            } else if (star >= 0) {
                pattern = star + 1;
                text = ++retry;
            } else return false;
        }
        while (pattern < filter.length() && filter.charAt(pattern) == '*') pattern++;
        return pattern == filter.length();
    }

    public boolean accepts(IAEStack<?> stack) {
        if (stack == null || (!BigAEStackValues.isInfinite(stack) && BigAEStackValues.get(stack)
            .compareTo(minimum) < 0)) return false;
        if (stack instanceof IAEItemStack) {
            ItemStack item = ((IAEItemStack) stack).getItemStack();
            String id = String.valueOf(Item.itemRegistry.getNameForObject(item.getItem()));
            int colon = id.indexOf(':');
            if (!glob(modId, colon < 0 ? "minecraft" : id.substring(0, colon))) return false;
            if (!glob(itemId, id) && !glob(itemId, colon < 0 ? id : id.substring(colon + 1))) return false;
            if (meta != OreDictionary.WILDCARD_VALUE && meta != item.getItemDamage()) return false;
            if (!ore.isEmpty()) {
                for (int oreId : OreDictionary.getOreIDs(item))
                    if (glob(ore, OreDictionary.getOreName(oreId))) return true;
                return false;
            }
            return true;
        }
        // Fluids have no item damage or ore dictionary entry. Their identifier is the fluid registry name.
        if (stack instanceof IAEFluidStack) return glob(
            itemId,
            ((IAEFluidStack) stack).getFluid()
                .getName());
        return false;
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("minimum", minimumText);
        tag.setInteger("refresh", refreshTicks);
        tag.setString("mod", modId);
        tag.setString("item", itemId);
        tag.setString("ore", ore);
        tag.setInteger("meta", meta);
        return tag;
    }

    public void load(NBTTagCompound tag) {
        if (tag.hasKey("minimum")) setMinimum(tag.getString("minimum"));
        if (tag.hasKey("refresh")) setRefreshTicks(tag.getInteger("refresh"));
        setModId(tag.getString("mod"));
        setItemId(tag.hasKey("item") ? tag.getString("item") : "*");
        setOre(tag.getString("ore"));
        setMeta(tag.hasKey("meta") ? tag.getInteger("meta") : OreDictionary.WILDCARD_VALUE);
    }
}
