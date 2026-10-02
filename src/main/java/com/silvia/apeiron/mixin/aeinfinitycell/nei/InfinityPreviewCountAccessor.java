package com.silvia.apeiron.mixin.aeinfinitycell.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import cn.dancingsnow.aeinfinitycell.storage.CellCount;

/** Reads the already-exact preview count rather than its legacy display projection. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewHandler$ViewItemStack", remap = false)
public interface InfinityPreviewCountAccessor {

    @Accessor("amount")
    CellCount apeiron$getAmount();
}
