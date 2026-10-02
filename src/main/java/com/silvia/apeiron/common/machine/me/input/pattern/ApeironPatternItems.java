package com.silvia.apeiron.common.machine.me.input.pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import cpw.mods.fml.common.registry.GameRegistry;

/** Internal task representation so in-flight AE jobs keep a block's exact multiplier after reloading. */
public final class ApeironPatternItems {

    public static Item multipliedPattern;

    private ApeironPatternItems() {}

    public static void register() {
        if (multipliedPattern != null) return;
        multipliedPattern = new MultipliedPatternItem();
        GameRegistry.registerItem(multipliedPattern, "multiplied_pattern");
    }

    private static final class MultipliedPatternItem extends Item implements ICraftingPatternItem {

        private MultipliedPatternItem() {
            setUnlocalizedName("apeiron.multiplied_pattern");
            setTextureName("appliedenergistics2:ItemEncodedPattern");
            setMaxStackSize(1);
        }

        @Override
        public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
            if (stack == null || !stack.hasTagCompound()) return null;
            ItemStack base = ItemStack.loadItemStackFromNBT(
                stack.getTagCompound()
                    .getCompoundTag("basePattern"));
            if (base == null || !(base.getItem() instanceof ICraftingPatternItem)) return null;
            ICraftingPatternDetails details = ((ICraftingPatternItem) base.getItem()).getPatternForItem(base, world);
            if (details == null) return null;
            return new MultipliedPatternDetails(
                details,
                BigValueCodec.readNBT(stack.getTagCompound(), "multiplier", "ApeironMultiplierBig")
                    .toBigInteger());
        }
    }
}
