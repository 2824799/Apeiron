package com.silvia.apeiron.mixin.ae.terminal.pattern;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.PatternHelper;
import appeng.util.Platform;

/** Restores exact AE pattern quantities after PatternHelper crosses the ItemStack int boundary. */
@Mixin(value = PatternHelper.class, remap = false)
public abstract class PatternHelperBigMixin {

    @Shadow @Final @Mutable private IAEItemStack[] inputs;
    @Shadow @Final @Mutable private IAEItemStack[] outputs;
    @Shadow @Final @Mutable private IAEItemStack[] condensedInputs;
    @Shadow @Final @Mutable private IAEItemStack[] condensedOutputs;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void apeiron$restoreExactPatternValues(final net.minecraft.item.ItemStack pattern,
        final net.minecraft.world.World world, final CallbackInfo ci) {
        final NBTTagCompound encoded = pattern.getTagCompound();
        if (encoded == null) return;
        final IAEItemStack[] exactInputs = apeiron$readItems(encoded.getTagList("in", NBT.TAG_COMPOUND));
        final IAEItemStack[] exactOutputs = apeiron$readItems(encoded.getTagList("out", NBT.TAG_COMPOUND));
        if (exactInputs != null) this.inputs = exactInputs;
        if (exactOutputs != null && exactOutputs.length > 0) this.outputs = exactOutputs;
        if (exactInputs != null) this.condensedInputs = PatternHelper.convertToCondensedList(this.inputs);
        if (exactOutputs != null && exactOutputs.length > 0) {
            this.condensedOutputs = PatternHelper.convertToCondensedList(this.outputs);
        }
    }

    private static IAEItemStack[] apeiron$readItems(final NBTTagList list) {
        if (list == null || list.tagCount() == 0) return null;
        final List<IAEItemStack> values = new ArrayList<>(list.tagCount());
        boolean exact = false;
        for (int index = 0; index < list.tagCount(); index++) {
            final NBTTagCompound tag = list.getCompoundTagAt(index);
            final IAEStack<?> stack = Platform.readStackNBT(tag);
            if (stack instanceof IAEItemStack item) {
                values.add(item);
                exact |= BigAEStackValues.isBig(item) || tag.hasKey("ApeironCnt", NBT.TAG_BYTE_ARRAY);
            } else {
                values.add(null);
            }
        }
        return exact ? values.toArray(new IAEItemStack[0]) : null;
    }
}
