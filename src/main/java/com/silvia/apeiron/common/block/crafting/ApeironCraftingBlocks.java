package com.silvia.apeiron.common.block.crafting;

import com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingStorage;
import com.silvia.apeiron.common.tile.crafting.TileInfiniteCraftingUnit;

import appeng.block.AEBaseItemBlock;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ApeironCraftingBlocks {

    public static BlockInfiniteCraftingUnit unit;
    public static BlockInfiniteCraftingStorage storage;

    private ApeironCraftingBlocks() {}

    public static void register() {
        unit = new BlockInfiniteCraftingUnit();
        storage = new BlockInfiniteCraftingStorage();
        GameRegistry.registerBlock(unit, AEBaseItemBlock.class, "infinite_crafting_unit");
        GameRegistry.registerBlock(storage, AEBaseItemBlock.class, "infinite_crafting_storage");
        GameRegistry.registerTileEntity(TileInfiniteCraftingUnit.class, "apeiron.infinite_crafting_unit");
        GameRegistry.registerTileEntity(TileInfiniteCraftingStorage.class, "apeiron.infinite_crafting_storage");
        AEBaseTile.registerTileItem(TileInfiniteCraftingUnit.class, new BlockStackSrc(unit, 0, ActivityState.Enabled));
        AEBaseTile
            .registerTileItem(TileInfiniteCraftingStorage.class, new BlockStackSrc(storage, 0, ActivityState.Enabled));
    }
}
